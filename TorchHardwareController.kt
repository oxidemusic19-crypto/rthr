package com.example.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TorchHardwareController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(TorchControllerUiState())
    val state: StateFlow<TorchControllerUiState> = _state.asStateFlow()

    private var rearCameraId: String? = null
    private var frontCameraId: String? = null
    private var isCallbackRegistered = false

    // Active Camera2 session resources used ONLY if front LED exposes flash via HAL
    // but rejects CameraManager.setTorchMode()
    private var activeFrontDevice: CameraDevice? = null
    private var activeFrontSession: CameraCaptureSession? = null
    private var activeFrontSurface: Surface? = null
    private var activeFrontTexture: SurfaceTexture? = null
    private var isFrontUsingCamera2Session = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeUnavailable(cameraId: String) {
            when (cameraId) {
                rearCameraId -> _state.update { current ->
                    current.copy(
                        rearTorch = current.rearTorch.copy(
                            isEnabled = false,
                            isUnavailableTemporarily = true,
                            isBusy = false,
                            errorMessage = "Rear flash temporarily unavailable (camera in use by another app).",
                        )
                    )
                }

                frontCameraId -> {
                    if (!isFrontUsingCamera2Session) {
                        _state.update { current ->
                            current.copy(
                                frontLed = current.frontLed.copy(
                                    isEnabled = false,
                                    isUnavailableTemporarily = true,
                                    isBusy = false,
                                    errorMessage = "Front flash temporarily unavailable (camera in use by another app).",
                                )
                            )
                        }
                    }
                }
            }
        }

        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            when (cameraId) {
                rearCameraId -> {
                    val syncedStrength = if (enabled) {
                        readCurrentStrengthOrFallback(cameraId, _state.value.rearTorch.currentStrengthLevel)
                    } else {
                        _state.value.rearTorch.currentStrengthLevel
                    }
                    _state.update { current ->
                        current.copy(
                            rearTorch = current.rearTorch.copy(
                                isEnabled = enabled,
                                isUnavailableTemporarily = false,
                                isBusy = false,
                                currentStrengthLevel = syncedStrength,
                                errorMessage = null,
                            )
                        )
                    }
                }

                frontCameraId -> {
                    if (!isFrontUsingCamera2Session) {
                        val syncedStrength = if (enabled) {
                            readCurrentStrengthOrFallback(cameraId, _state.value.frontLed.currentStrengthLevel)
                        } else {
                            _state.value.frontLed.currentStrengthLevel
                        }
                        _state.update { current ->
                            current.copy(
                                frontLed = current.frontLed.copy(
                                    isEnabled = enabled,
                                    isUnavailableTemporarily = false,
                                    isBusy = false,
                                    currentStrengthLevel = syncedStrength,
                                    errorMessage = null,
                                )
                            )
                        }
                    }
                }
            }
        }

        override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
            when (cameraId) {
                rearCameraId -> _state.update { current ->
                    current.copy(
                        rearTorch = current.rearTorch.copy(
                            currentStrengthLevel = newStrengthLevel.coerceIn(
                                1,
                                current.rearTorch.maxStrengthLevel.coerceAtLeast(1),
                            )
                        )
                    )
                }

                frontCameraId -> _state.update { current ->
                    current.copy(
                        frontLed = current.frontLed.copy(
                            currentStrengthLevel = newStrengthLevel.coerceIn(
                                1,
                                current.frontLed.maxStrengthLevel.coerceAtLeast(1),
                            )
                        )
                    )
                }
            }
        }
    }

    fun initialize() {
        registerTorchCallbackIfNeeded()
        scanHardware()
    }

    fun refreshPermissionState() {
        val granted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        _state.update { it.copy(cameraPermissionGranted = granted) }
    }

    fun scanHardware() {
        refreshPermissionState()
        val pm = appContext.packageManager
        val hasFlashFeature = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        val hasFrontFeature = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)

        val manager = cameraManager
        if (manager == null) {
            val report = buildReport(
                hasFlashFeature = hasFlashFeature,
                hasFrontFeature = hasFrontFeature,
                cameras = emptyList(),
                sysfsProbes = probeSysfsReadOnly(),
                frontVerdict = "CameraManager system service unavailable on this device.",
            )
            _state.update { current ->
                current.copy(
                    rearTorch = TorchUnitState(
                        title = "REAR TORCH",
                        isAvailable = false,
                        activeControlPath = "Unavailable",
                        availabilityReason = "CameraManager system service could not be acquired.",
                    ),
                    frontLed = TorchUnitState(
                        title = "FRONT LED",
                        isAvailable = false,
                        activeControlPath = "Unavailable",
                        availabilityReason = "CameraManager system service could not be acquired.",
                    ),
                    hardwareReport = report,
                )
            }
            return
        }

        val cameraProbes = mutableListOf<CameraProbeInfo>()
        try {
            val ids = manager.cameraIdList
            for (id in ids) {
                val probe = inspectCamera(manager, id)
                if (probe != null) {
                    cameraProbes.add(probe)
                }
            }
        } catch (e: CameraAccessException) {
            _state.update { it.copy(globalNotice = formatCameraAccessError("Camera enumeration failed", e)) }
        } catch (e: Exception) {
            _state.update { it.copy(globalNotice = "Camera enumeration error: ${e.message ?: "Unknown"}") }
        }

        val rearProbe = cameraProbes.firstOrNull { it.lensFacing == LensFacing.BACK && it.flashAvailable }
        val frontProbe = cameraProbes.firstOrNull { it.lensFacing == LensFacing.FRONT && it.flashAvailable }
        val frontCameraExistsWithoutFlash = cameraProbes.any { it.lensFacing == LensFacing.FRONT && !it.flashAvailable }
        val externalFlashProbe = cameraProbes.firstOrNull { it.lensFacing == LensFacing.EXTERNAL && it.flashAvailable }

        rearCameraId = rearProbe?.cameraId
        frontCameraId = frontProbe?.cameraId

        val sysfsProbes = probeSysfsReadOnly()
        val anyWritableSysfs = sysfsProbes.any { it.writable }
        val discoveredFrontVendorKeys = cameraProbes
            .filter { it.lensFacing == LensFacing.FRONT }
            .flatMap { it.vendorFlashKeys }
            .distinct()

        val rearState = if (rearProbe != null) {
            val prev = _state.value.rearTorch
            val level = prev.currentStrengthLevel.takeIf {
                prev.cameraId == rearProbe.cameraId && it in 1..rearProbe.maxStrengthLevel
            } ?: rearProbe.defaultStrengthLevel
            TorchUnitState(
                title = "REAR TORCH",
                cameraId = rearProbe.cameraId,
                isAvailable = true,
                isEnabled = prev.isEnabled,
                isUnavailableTemporarily = prev.isUnavailableTemporarily,
                supportsStrengthControl = rearProbe.supportsStrengthLevel,
                minStrengthLevel = 1,
                maxStrengthLevel = rearProbe.maxStrengthLevel,
                defaultStrengthLevel = rearProbe.defaultStrengthLevel,
                currentStrengthLevel = level,
                activeControlPath = if (rearProbe.supportsStrengthLevel && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    "CameraManager.turnOnTorchWithStrengthLevel (ID ${rearProbe.cameraId})"
                } else {
                    "CameraManager.setTorchMode (ID ${rearProbe.cameraId})"
                },
                availabilityReason = if (rearProbe.supportsStrengthLevel) {
                    "Camera ID ${rearProbe.cameraId} · ${rearProbe.maxStrengthLevel} hardware intensity levels"
                } else {
                    "Camera ID ${rearProbe.cameraId} · Standard on/off hardware torch"
                },
            )
        } else {
            TorchUnitState(
                title = "REAR TORCH",
                isAvailable = false,
                activeControlPath = "Not exposed",
                availabilityReason = "No back-facing camera reported FLASH_INFO_AVAILABLE=true.",
            )
        }

        val frontVerdict: String
        val frontState: TorchUnitState
        if (frontProbe != null) {
            val prev = _state.value.frontLed
            val level = prev.currentStrengthLevel.takeIf {
                prev.cameraId == frontProbe.cameraId && it in 1..frontProbe.maxStrengthLevel
            } ?: frontProbe.defaultStrengthLevel
            frontVerdict = "Front camera ID ${frontProbe.cameraId} exposes FLASH_INFO_AVAILABLE=true. " +
                "Supported via CameraManager torch APIs (with Camera2 CaptureSession FLASH_MODE_TORCH fallback if needed)."
            frontState = TorchUnitState(
                title = "FRONT LED",
                cameraId = frontProbe.cameraId,
                isAvailable = true,
                isEnabled = prev.isEnabled,
                isUnavailableTemporarily = prev.isUnavailableTemporarily,
                supportsStrengthControl = frontProbe.supportsStrengthLevel,
                minStrengthLevel = 1,
                maxStrengthLevel = frontProbe.maxStrengthLevel,
                defaultStrengthLevel = frontProbe.defaultStrengthLevel,
                currentStrengthLevel = level,
                activeControlPath = if (isFrontUsingCamera2Session) {
                    "Camera2 CaptureSession FLASH_MODE_TORCH (ID ${frontProbe.cameraId})"
                } else {
                    "CameraManager Torch API (ID ${frontProbe.cameraId})"
                },
                availabilityReason = "Front Camera ID ${frontProbe.cameraId} · Hardware flash exposed by HAL",
            )
        } else {
            val vendorNote = if (discoveredFrontVendorKeys.isNotEmpty()) {
                "Detected unverified read-only vendor tags on front camera: ${discoveredFrontVendorKeys.joinToString(", ")}. "
            } else {
                ""
            }
            val externalNote = if (externalFlashProbe != null) {
                "Note: External camera ID ${externalFlashProbe.cameraId} reports flash support, but no front-facing unit does. "
            } else {
                ""
            }
            val sysfsNote = if (anyWritableSysfs) {
                "Writable sysfs node detected."
            } else {
                "Kernel /sys/class/leds nodes are non-writable under Android 15 SELinux."
            }
            frontVerdict = buildString {
                if (frontCameraExistsWithoutFlash) {
                    append("Front camera(s) report FLASH_INFO_AVAILABLE=false. ")
                } else {
                    append("No front-facing camera with flash metadata was reported by Camera2 HAL. ")
                }
                append(vendorNote)
                append(externalNote)
                append(sysfsNote)
                append(" Screen-brightness simulation is disabled by design.")
            }
            frontState = TorchUnitState(
                title = "FRONT LED",
                cameraId = cameraProbes.firstOrNull { it.lensFacing == LensFacing.FRONT }?.cameraId,
                isAvailable = false,
                isEnabled = false,
                activeControlPath = "Restricted / Not exposed by HAL",
                availabilityReason = if (frontCameraExistsWithoutFlash) {
                    "Front camera HAL reports FLASH_INFO_AVAILABLE=false · Hardware LED not exposed to apps"
                } else {
                    "No controllable front hardware LED detected via Camera2 or accessible sysfs"
                },
            )
        }

        val report = buildReport(
            hasFlashFeature = hasFlashFeature,
            hasFrontFeature = hasFrontFeature,
            cameras = cameraProbes,
            sysfsProbes = sysfsProbes,
            frontVerdict = frontVerdict,
        )

        _state.update { current ->
            current.copy(
                rearTorch = rearState,
                frontLed = frontState,
                hardwareReport = report,
            )
        }
    }

    fun toggleRearTorch() {
        val current = _state.value.rearTorch
        if (!current.isAvailable || current.cameraId == null) {
            _state.update { state ->
                state.copy(
                    rearTorch = state.rearTorch.copy(
                        errorMessage = "Rear torch hardware is not available on this device.",
                    )
                )
            }
            return
        }
        setRearTorch(!current.isEnabled)
    }

    fun setRearTorch(enable: Boolean) {
        val manager = cameraManager ?: return
        val current = _state.value.rearTorch
        val id = current.cameraId ?: return

        _state.update { it.copy(rearTorch = it.rearTorch.copy(isBusy = true, errorMessage = null)) }
        try {
            if (enable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && current.supportsStrengthControl) {
                    val level = current.currentStrengthLevel.coerceIn(1, current.maxStrengthLevel)
                    manager.turnOnTorchWithStrengthLevel(id, level)
                } else {
                    manager.setTorchMode(id, true)
                }
            } else {
                manager.setTorchMode(id, false)
            }
            _state.update { state ->
                state.copy(
                    rearTorch = state.rearTorch.copy(
                        isEnabled = enable,
                        isBusy = false,
                        isUnavailableTemporarily = false,
                        errorMessage = null,
                    )
                )
            }
        } catch (e: CameraAccessException) {
            _state.update { state ->
                state.copy(
                    rearTorch = state.rearTorch.copy(
                        isBusy = false,
                        isEnabled = if (enable) false else state.rearTorch.isEnabled,
                        errorMessage = formatCameraAccessError("Rear torch failed", e),
                    )
                )
            }
        } catch (e: SecurityException) {
            _state.update { state ->
                state.copy(
                    rearTorch = state.rearTorch.copy(
                        isBusy = false,
                        errorMessage = "Camera permission denied by Android OS.",
                    )
                )
            }
        } catch (e: IllegalArgumentException) {
            _state.update { state ->
                state.copy(
                    rearTorch = state.rearTorch.copy(
                        isBusy = false,
                        errorMessage = "HAL rejected torch command for rear camera $id: ${e.message ?: "Unsupported"}",
                    )
                )
            }
        }
    }

    fun setRearStrengthLevel(level: Int) {
        val current = _state.value.rearTorch
        val clamped = level.coerceIn(1, current.maxStrengthLevel.coerceAtLeast(1))
        _state.update { it.copy(rearTorch = it.rearTorch.copy(currentStrengthLevel = clamped)) }

        if (current.isEnabled && current.supportsStrengthControl && current.cameraId != null) {
            val manager = cameraManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    manager.turnOnTorchWithStrengthLevel(current.cameraId, clamped)
                } catch (e: CameraAccessException) {
                    _state.update { state ->
                        state.copy(
                            rearTorch = state.rearTorch.copy(
                                errorMessage = formatCameraAccessError("Strength update failed", e),
                            )
                        )
                    }
                } catch (e: IllegalArgumentException) {
                    _state.update { state ->
                        state.copy(
                            rearTorch = state.rearTorch.copy(
                                errorMessage = "Invalid torch strength level $clamped: ${e.message}",
                            )
                        )
                    }
                }
            }
        }
    }

    fun toggleFrontLed() {
        val current = _state.value.frontLed
        if (!current.isAvailable || current.cameraId == null) {
            _state.update { state ->
                state.copy(
                    frontLed = state.frontLed.copy(
                        errorMessage = current.availabilityReason,
                    )
                )
            }
            return
        }
        setFrontLed(!current.isEnabled)
    }

    fun setFrontLed(enable: Boolean) {
        val manager = cameraManager ?: return
        val current = _state.value.frontLed
        val id = current.cameraId ?: return

        if (!enable) {
            turnOffFrontLedInternal(manager, id)
            return
        }

        _state.update {
            it.copy(
                frontLed = it.frontLed.copy(
                    isBusy = true,
                    errorMessage = null,
                    requiresCameraPermissionForFallback = false,
                )
            )
        }

        // Primary path: standard CameraManager torch API
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && current.supportsStrengthControl) {
                val level = current.currentStrengthLevel.coerceIn(1, current.maxStrengthLevel)
                manager.turnOnTorchWithStrengthLevel(id, level)
            } else {
                manager.setTorchMode(id, true)
            }
            _state.update { state ->
                state.copy(
                    frontLed = state.frontLed.copy(
                        isEnabled = true,
                        isBusy = false,
                        isUnavailableTemporarily = false,
                        activeControlPath = if (current.supportsStrengthControl && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            "CameraManager.turnOnTorchWithStrengthLevel (ID $id)"
                        } else {
                            "CameraManager.setTorchMode (ID $id)"
                        },
                        errorMessage = null,
                    )
                )
            }
            return
        } catch (e: CameraAccessException) {
            if (e.reason == CameraAccessException.CAMERA_IN_USE ||
                e.reason == CameraAccessException.MAX_CAMERAS_IN_USE ||
                e.reason == CameraAccessException.CAMERA_DISABLED
            ) {
                _state.update { state ->
                    state.copy(
                        frontLed = state.frontLed.copy(
                            isBusy = false,
                            isEnabled = false,
                            errorMessage = formatCameraAccessError("Front LED unavailable", e),
                        )
                    )
                }
                return
            }
            // Fall through to Camera2 CaptureSession attempt when HAL fails setTorchMode
        } catch (_: IllegalArgumentException) {
            // Some OEM HALs expose FLASH_INFO_AVAILABLE=true on front camera
            // but only accept FLASH_MODE_TORCH inside an open CameraCaptureSession
        } catch (e: SecurityException) {
            _state.update { state ->
                state.copy(
                    frontLed = state.frontLed.copy(
                        isBusy = false,
                        requiresCameraPermissionForFallback = true,
                        errorMessage = "Camera permission required to access front flash: ${e.message}",
                    )
                )
            }
            return
        }

        // Secondary verified Camera2 path: open front CameraDevice and set FLASH_MODE_TORCH
        openFrontCamera2TorchSession(manager, id)
    }

    fun setFrontStrengthLevel(level: Int) {
        val current = _state.value.frontLed
        val clamped = level.coerceIn(1, current.maxStrengthLevel.coerceAtLeast(1))
        _state.update { it.copy(frontLed = it.frontLed.copy(currentStrengthLevel = clamped)) }

        if (current.isEnabled && current.supportsStrengthControl && !isFrontUsingCamera2Session && current.cameraId != null) {
            val manager = cameraManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    manager.turnOnTorchWithStrengthLevel(current.cameraId, clamped)
                } catch (e: Exception) {
                    _state.update { state ->
                        state.copy(
                            frontLed = state.frontLed.copy(
                                errorMessage = "Front strength update failed: ${e.message}",
                            )
                        )
                    }
                }
            }
        }
    }

    private fun turnOffFrontLedInternal(manager: CameraManager, id: String) {
        if (isFrontUsingCamera2Session) {
            closeFrontCamera2Session()
        }
        try {
            manager.setTorchMode(id, false)
        } catch (_: Exception) {
            // Ignore if front camera only used Camera2 session or doesn't support setTorchMode
        }
        _state.update { state ->
            state.copy(
                isFrontSessionActive = false,
                frontLed = state.frontLed.copy(
                    isEnabled = false,
                    isBusy = false,
                    errorMessage = null,
                ),
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun openFrontCamera2TorchSession(manager: CameraManager, cameraId: String) {
        val hasPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            _state.update { state ->
                state.copy(
                    cameraPermissionGranted = false,
                    frontLed = state.frontLed.copy(
                        isBusy = false,
                        isEnabled = false,
                        requiresCameraPermissionForFallback = true,
                        errorMessage = "Standard setTorchMode rejected by front HAL. Grant CAMERA permission to attempt Camera2 CaptureSession FLASH_MODE_TORCH.",
                    ),
                )
            }
            return
        }

        closeFrontCamera2Session()
        try {
            manager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        activeFrontDevice = camera
                        createFrontTorchCaptureSession(camera, cameraId)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        if (activeFrontDevice == camera) {
                            closeFrontCamera2Session()
                        }
                        _state.update { state ->
                            state.copy(
                                isFrontSessionActive = false,
                                frontLed = state.frontLed.copy(
                                    isEnabled = false,
                                    isBusy = false,
                                    errorMessage = "Front camera disconnected by system.",
                                ),
                            )
                        }
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        if (activeFrontDevice == camera) {
                            closeFrontCamera2Session()
                        }
                        _state.update { state ->
                            state.copy(
                                isFrontSessionActive = false,
                                frontLed = state.frontLed.copy(
                                    isEnabled = false,
                                    isBusy = false,
                                    errorMessage = "Front CameraDevice error code $error while enabling torch session.",
                                ),
                            )
                        }
                    }
                },
                mainHandler,
            )
        } catch (e: CameraAccessException) {
            _state.update { state ->
                state.copy(
                    frontLed = state.frontLed.copy(
                        isEnabled = false,
                        isBusy = false,
                        errorMessage = formatCameraAccessError("Front Camera2 session failed", e),
                    )
                )
            }
        } catch (e: SecurityException) {
            _state.update { state ->
                state.copy(
                    cameraPermissionGranted = false,
                    frontLed = state.frontLed.copy(
                        isEnabled = false,
                        isBusy = false,
                        requiresCameraPermissionForFallback = true,
                        errorMessage = "Camera permission denied: ${e.message}",
                    ),
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun createFrontTorchCaptureSession(camera: CameraDevice, cameraId: String) {
        try {
            val texture = SurfaceTexture(0).apply { setDefaultBufferSize(16, 16) }
            val surface = Surface(texture)
            activeFrontTexture = texture
            activeFrontSurface = surface

            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        activeFrontSession = session
                        try {
                            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(surface)
                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                            }
                            session.setRepeatingRequest(requestBuilder.build(), null, mainHandler)
                            isFrontUsingCamera2Session = true
                            _state.update { state ->
                                state.copy(
                                    isFrontSessionActive = true,
                                    frontLed = state.frontLed.copy(
                                        isEnabled = true,
                                        isBusy = false,
                                        requiresCameraPermissionForFallback = false,
                                        activeControlPath = "Camera2 CaptureSession FLASH_MODE_TORCH (ID $cameraId)",
                                        errorMessage = null,
                                    ),
                                )
                            }
                        } catch (e: Exception) {
                            closeFrontCamera2Session()
                            _state.update { state ->
                                state.copy(
                                    isFrontSessionActive = false,
                                    frontLed = state.frontLed.copy(
                                        isEnabled = false,
                                        isBusy = false,
                                        errorMessage = "Failed to start front FLASH_MODE_TORCH stream: ${e.message}",
                                    ),
                                )
                            }
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        closeFrontCamera2Session()
                        _state.update { state ->
                            state.copy(
                                isFrontSessionActive = false,
                                frontLed = state.frontLed.copy(
                                    isEnabled = false,
                                    isBusy = false,
                                    errorMessage = "Front CameraCaptureSession configuration failed.",
                                ),
                            )
                        }
                    }
                },
                mainHandler,
            )
        } catch (e: Exception) {
            closeFrontCamera2Session()
            _state.update { state ->
                state.copy(
                    isFrontSessionActive = false,
                    frontLed = state.frontLed.copy(
                        isEnabled = false,
                        isBusy = false,
                        errorMessage = "Could not create front torch session: ${e.message}",
                    ),
                )
            }
        }
    }

    fun releaseTemporaryCameraSessions() {
        if (isFrontUsingCamera2Session) {
            closeFrontCamera2Session()
            _state.update { state ->
                state.copy(
                    isFrontSessionActive = false,
                    frontLed = state.frontLed.copy(
                        isEnabled = false,
                        isBusy = false,
                    ),
                )
            }
        }
    }

    fun turnOffAllLights() {
        if (_state.value.rearTorch.isEnabled) {
            setRearTorch(false)
        }
        if (_state.value.frontLed.isEnabled || isFrontUsingCamera2Session) {
            setFrontLed(false)
        }
    }

    fun releaseAll() {
        turnOffAllLights()
        closeFrontCamera2Session()
        unregisterTorchCallbackIfNeeded()
    }

    fun setInspectorOpen(open: Boolean) {
        _state.update { it.copy(isInspectorOpen = open) }
    }

    fun clearGlobalNotice() {
        _state.update { it.copy(globalNotice = null) }
    }

    private fun closeFrontCamera2Session() {
        isFrontUsingCamera2Session = false
        try {
            activeFrontSession?.stopRepeating()
        } catch (_: Exception) {
        }
        try {
            activeFrontSession?.close()
        } catch (_: Exception) {
        }
        activeFrontSession = null
        try {
            activeFrontDevice?.close()
        } catch (_: Exception) {
        }
        activeFrontDevice = null
        try {
            activeFrontSurface?.release()
        } catch (_: Exception) {
        }
        activeFrontSurface = null
        try {
            activeFrontTexture?.release()
        } catch (_: Exception) {
        }
        activeFrontTexture = null
    }

    private fun registerTorchCallbackIfNeeded() {
        if (!isCallbackRegistered) {
            cameraManager?.registerTorchCallback(torchCallback, mainHandler)
            isCallbackRegistered = true
        }
    }

    private fun unregisterTorchCallbackIfNeeded() {
        if (isCallbackRegistered) {
            try {
                cameraManager?.unregisterTorchCallback(torchCallback)
            } catch (_: Exception) {
            }
            isCallbackRegistered = false
        }
    }

    private fun readCurrentStrengthOrFallback(cameraId: String, fallback: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                return cameraManager?.getTorchStrengthLevel(cameraId) ?: fallback
            } catch (_: Exception) {
            }
        }
        return fallback
    }

    private fun inspectCamera(manager: CameraManager, cameraId: String): CameraProbeInfo? {
        return try {
            val chars = manager.getCameraCharacteristics(cameraId)
            val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> LensFacing.BACK
                CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
                CameraCharacteristics.LENS_FACING_EXTERNAL -> LensFacing.EXTERNAL
                else -> LensFacing.UNKNOWN
            }
            val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

            var maxStrength = 1
            var defaultStrength = 1
            var supportsStrength = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && flashAvailable) {
                maxStrength = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                defaultStrength = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL) ?: 1
                supportsStrength = maxStrength > 1
            }

            val aeModes = (chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: IntArray(0))
                .map { mode -> aeModeName(mode) }

            val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                chars.physicalCameraIds.sorted()
            } else {
                emptyList()
            }

            val keywords = listOf("flash", "torch", "led", "strobe", "sprd", "unisoc", "transsion", "infinix")
            val vendorKeys = buildList {
                chars.keys.forEach { key ->
                    val name = key.name
                    if (!name.startsWith("android.") && keywords.any { kw -> name.contains(kw, ignoreCase = true) }) {
                        add("char:$name")
                    }
                }
                chars.availableCaptureRequestKeys.forEach { key ->
                    val name = key.name
                    if (!name.startsWith("android.") && keywords.any { kw -> name.contains(kw, ignoreCase = true) }) {
                        add("req:$name")
                    }
                }
            }.distinct().sorted()

            CameraProbeInfo(
                cameraId = cameraId,
                lensFacing = facing,
                flashAvailable = flashAvailable,
                supportsStrengthLevel = supportsStrength,
                maxStrengthLevel = maxStrength,
                defaultStrengthLevel = defaultStrength.coerceIn(1, maxStrength.coerceAtLeast(1)),
                aeModes = aeModes,
                physicalCameraIds = physicalIds,
                vendorFlashKeys = vendorKeys,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun probeSysfsReadOnly(): List<SysfsProbeResult> {
        val results = mutableListOf<SysfsProbeResult>()
        val ledRoot = File("/sys/class/leds")
        try {
            val children = ledRoot.listFiles()
            if (children != null) {
                val matching = children
                    .filter {
                        val n = it.name.lowercase()
                        n.contains("flash") || n.contains("torch") || n.contains("led") || n.contains("sprd")
                    }
                    .sortedBy { it.name }
                if (matching.isNotEmpty()) {
                    matching.forEach { dir ->
                        val brightness = File(dir, "brightness")
                        results.add(
                            SysfsProbeResult(
                                path = brightness.absolutePath,
                                exists = brightness.exists(),
                                readable = brightness.canRead(),
                                writable = brightness.canWrite(),
                                note = if (brightness.canWrite()) "Writable" else "Read-only / SELinux blocked",
                            )
                        )
                    }
                    return results
                }
            }
        } catch (e: SecurityException) {
            results.add(
                SysfsProbeResult(
                    path = "/sys/class/leds",
                    exists = ledRoot.exists(),
                    readable = false,
                    writable = false,
                    note = "Directory listing blocked by Android SELinux (${e.javaClass.simpleName})",
                )
            )
        } catch (_: Throwable) {
        }

        val knownPaths = listOf(
            "/sys/class/leds/torch-light/brightness",
            "/sys/class/leds/flashlight/brightness",
            "/sys/class/leds/front-flash/brightness",
            "/sys/class/leds/front_flash/brightness",
            "/sys/class/leds/sub-flash/brightness",
            "/sys/class/leds/sprd_flash/brightness",
            "/sys/class/leds/led:torch_0/brightness",
            "/sys/class/leds/led:torch_1/brightness",
        )
        knownPaths.forEach { path ->
            val file = File(path)
            val exists = try {
                file.exists()
            } catch (_: SecurityException) {
                false
            }
            val readable = try {
                file.canRead()
            } catch (_: SecurityException) {
                false
            }
            val writable = try {
                file.canWrite()
            } catch (_: SecurityException) {
                false
            }
            results.add(
                SysfsProbeResult(
                    path = path,
                    exists = exists,
                    readable = readable,
                    writable = writable,
                    note = when {
                        writable -> "Writable"
                        exists -> "Exists, blocked by SELinux/UID permissions"
                        else -> "Not present or hidden by SELinux"
                    },
                )
            )
        }
        return results
    }

    private fun buildReport(
        hasFlashFeature: Boolean,
        hasFrontFeature: Boolean,
        cameras: List<CameraProbeInfo>,
        sysfsProbes: List<SysfsProbeResult>,
        frontVerdict: String,
    ): DeviceHardwareReport {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.ifBlank { Build.HARDWARE }
        } else {
            Build.HARDWARE
        }
        return DeviceHardwareReport(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            hardware = Build.HARDWARE,
            board = Build.BOARD,
            socModel = soc,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            hasCameraFlashFeature = hasFlashFeature,
            hasFrontCameraFeature = hasFrontFeature,
            cameras = cameras,
            sysfsProbes = sysfsProbes,
            frontLedVerdict = frontVerdict,
            scannedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun aeModeName(mode: Int): String = when (mode) {
        CaptureRequest.CONTROL_AE_MODE_OFF -> "AE_OFF"
        CaptureRequest.CONTROL_AE_MODE_ON -> "AE_ON"
        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH -> "AUTO_FLASH"
        CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH -> "ALWAYS_FLASH"
        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE -> "AUTO_FLASH_REDEYE"
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            mode == CaptureRequest.CONTROL_AE_MODE_ON_EXTERNAL_FLASH
        ) {
            "EXTERNAL_FLASH"
        } else {
            "AE_$mode"
        }
    }

    private fun formatCameraAccessError(prefix: String, e: CameraAccessException): String {
        val detail = when (e.reason) {
            CameraAccessException.CAMERA_IN_USE -> "Camera resource is currently in use by another app (CAMERA_IN_USE)."
            CameraAccessException.MAX_CAMERAS_IN_USE -> "System limit of concurrent active cameras/torches reached (MAX_CAMERAS_IN_USE)."
            CameraAccessException.CAMERA_DISABLED -> "Camera/torch disabled by device policy (CAMERA_DISABLED)."
            CameraAccessException.CAMERA_DISCONNECTED -> "Camera device disconnected (CAMERA_DISCONNECTED)."
            CameraAccessException.CAMERA_ERROR -> "Camera HAL error (CAMERA_ERROR)."
            else -> e.message ?: "Unknown CameraAccessException (${e.reason})."
        }
        return "$prefix: $detail"
    }
}
