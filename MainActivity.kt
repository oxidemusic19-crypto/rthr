package com.example

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.TorchScreen
import com.example.ui.TorchViewModel
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.DualTorchTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TorchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current

            val cameraPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) {
                viewModel.onPermissionResult()
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> viewModel.onResume()
                        Lifecycle.Event.ON_STOP -> viewModel.onStop()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            DualTorchTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = AmoledBlack,
                    contentWindowInsets = WindowInsets.safeDrawing,
                ) { innerPadding ->
                    TorchScreen(
                        state = uiState,
                        innerPadding = innerPadding,
                        onToggleRear = viewModel::onToggleRearTorch,
                        onRearStrengthChange = viewModel::onRearStrengthChange,
                        onToggleFront = viewModel::onToggleFrontLed,
                        onFrontStrengthChange = viewModel::onFrontStrengthChange,
                        onTurnAllOff = viewModel::onTurnAllOff,
                        onOpenInspector = viewModel::onOpenInspector,
                        onRequestCameraPermission = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onRescanHardware = viewModel::onRescanHardware,
                        onDismissNotice = viewModel::onDismissNotice,
                    )
                }
            }
        }
    }
}
