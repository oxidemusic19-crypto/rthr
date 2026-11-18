package com.example.hardware

enum class LensFacing(val label: String) {
    BACK("BACK"),
    FRONT("FRONT"),
    EXTERNAL("EXTERNAL"),
    UNKNOWN("UNKNOWN"),
}

data class TorchUnitState(
    val title: String,
    val cameraId: String? = null,
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val isUnavailableTemporarily: Boolean = false,
    val isBusy: Boolean = false,
    val supportsStrengthControl: Boolean = false,
    val minStrengthLevel: Int = 1,
    val maxStrengthLevel: Int = 1,
    val defaultStrengthLevel: Int = 1,
    val currentStrengthLevel: Int = 1,
    val activeControlPath: String = "Not detected",
    val availabilityReason: String = "Scanning hardware...",
    val errorMessage: String? = null,
    val requiresCameraPermissionForFallback: Boolean = false,
)

data class CameraProbeInfo(
    val cameraId: String,
    val lensFacing: LensFacing,
    val flashAvailable: Boolean,
    val supportsStrengthLevel: Boolean,
    val maxStrengthLevel: Int,
    val defaultStrengthLevel: Int,
    val aeModes: List<String>,
    val physicalCameraIds: List<String>,
    val vendorFlashKeys: List<String>,
)

data class SysfsProbeResult(
    val path: String,
    val exists: Boolean,
    val readable: Boolean,
    val writable: Boolean,
    val note: String,
)

data class DeviceHardwareReport(
    val manufacturer: String,
    val model: String,
    val device: String,
    val hardware: String,
    val board: String,
    val socModel: String,
    val androidRelease: String,
    val sdkInt: Int,
    val hasCameraFlashFeature: Boolean,
    val hasFrontCameraFeature: Boolean,
    val cameras: List<CameraProbeInfo>,
    val sysfsProbes: List<SysfsProbeResult>,
    val frontLedVerdict: String,
    val scannedAtEpochMs: Long,
) {
    fun toFormattedText(): String = buildString {
        appendLine("=== Dual Torch Controller · Hardware Report ===")
        appendLine("Device: $manufacturer $model ($device)")
        appendLine("Platform: hardware=$hardware, board=$board, soc=$socModel")
        appendLine("Android: $androidRelease (API $sdkInt)")
        appendLine("System Features: camera.flash=$hasCameraFlashFeature, camera.front=$hasFrontCameraFeature")
        appendLine()
        appendLine("--- Camera2 Enumeration (${cameras.size} IDs) ---")
        if (cameras.isEmpty()) {
            appendLine("No cameras reported by CameraManager.")
        } else {
            cameras.forEach { cam ->
                appendLine(
                    "Camera ID '${cam.cameraId}' | facing=${cam.lensFacing.label} | " +
                        "flashAvailable=${cam.flashAvailable} | " +
                        "strengthControl=${cam.supportsStrengthLevel} (max=${cam.maxStrengthLevel}, default=${cam.defaultStrengthLevel})"
                )
                if (cam.physicalCameraIds.isNotEmpty()) {
                    appendLine("  Physical IDs: ${cam.physicalCameraIds.joinToString(", ")}")
                }
                if (cam.aeModes.isNotEmpty()) {
                    appendLine("  AE Modes: ${cam.aeModes.joinToString(", ")}")
                }
                if (cam.vendorFlashKeys.isNotEmpty()) {
                    appendLine("  Vendor Flash/LED Keys (read-only): ${cam.vendorFlashKeys.joinToString(", ")}")
                }
            }
        }
        appendLine()
        appendLine("--- Read-Only Sysfs LED Probe ---")
        sysfsProbes.forEach { probe ->
            appendLine("${probe.path} -> exists=${probe.exists}, read=${probe.readable}, write=${probe.writable} (${probe.note})")
        }
        appendLine()
        appendLine("--- Front LED Verdict ---")
        appendLine(frontLedVerdict)
    }
}

data class TorchControllerUiState(
    val rearTorch: TorchUnitState = TorchUnitState(title = "REAR TORCH"),
    val frontLed: TorchUnitState = TorchUnitState(title = "FRONT LED"),
    val hardwareReport: DeviceHardwareReport? = null,
    val cameraPermissionGranted: Boolean = false,
    val isFrontSessionActive: Boolean = false,
    val isInspectorOpen: Boolean = false,
    val globalNotice: String? = null,
)
