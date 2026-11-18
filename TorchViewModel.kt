package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.hardware.TorchControllerUiState
import com.example.hardware.TorchHardwareController
import kotlinx.coroutines.flow.StateFlow

class TorchViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = TorchHardwareController(application)
    val uiState: StateFlow<TorchControllerUiState> = controller.state

    init {
        controller.initialize()
    }

    fun onResume() {
        controller.scanHardware()
    }

    fun onStop() {
        // Release any active CameraDevice/CameraCaptureSession so we never hold
        // exclusive camera hardware locks while backgrounded.
        controller.releaseTemporaryCameraSessions()
    }

    fun onToggleRearTorch() {
        controller.toggleRearTorch()
    }

    fun onRearStrengthChange(level: Int) {
        controller.setRearStrengthLevel(level)
    }

    fun onToggleFrontLed() {
        controller.toggleFrontLed()
    }

    fun onFrontStrengthChange(level: Int) {
        controller.setFrontStrengthLevel(level)
    }

    fun onTurnAllOff() {
        controller.turnOffAllLights()
    }

    fun onPermissionResult() {
        controller.refreshPermissionState()
        controller.scanHardware()
    }

    fun onRescanHardware() {
        controller.scanHardware()
    }

    fun onOpenInspector(open: Boolean) {
        controller.setInspectorOpen(open)
    }

    fun onDismissNotice() {
        controller.clearGlobalNotice()
    }

    override fun onCleared() {
        super.onCleared()
        controller.releaseAll()
    }
}
