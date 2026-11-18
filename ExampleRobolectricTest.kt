package com.example

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.hardware.TorchControllerUiState
import com.example.hardware.TorchHardwareController
import com.example.hardware.TorchUnitState
import com.example.ui.TorchScreen
import com.example.ui.theme.DualTorchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app name matches Dual Torch Controller`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Dual Torch Controller", appName)
    }

    @Test
    fun `hardware controller scans without crashing and builds report`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = TorchHardwareController(context)
        controller.initialize()

        val state = controller.state.value
        assertNotNull(state.hardwareReport)
        assertFalse(state.rearTorch.isEnabled)
        assertFalse(state.frontLed.isEnabled)

        controller.releaseAll()
    }

    @Test
    fun `torch screen renders rear torch front led and inspector controls`() {
        val uiState = TorchControllerUiState(
            rearTorch = TorchUnitState(
                title = "REAR TORCH",
                cameraId = "0",
                isAvailable = true,
                supportsStrengthControl = true,
                maxStrengthLevel = 5,
                currentStrengthLevel = 3,
                availabilityReason = "Camera ID 0 · 5 hardware intensity levels",
            ),
            frontLed = TorchUnitState(
                title = "FRONT LED",
                cameraId = "1",
                isAvailable = false,
                availabilityReason = "Front camera HAL reports FLASH_INFO_AVAILABLE=false",
            ),
        )

        composeRule.setContent {
            DualTorchTheme {
                TorchScreen(
                    state = uiState,
                    innerPadding = PaddingValues(),
                    onToggleRear = {},
                    onRearStrengthChange = {},
                    onToggleFront = {},
                    onFrontStrengthChange = {},
                    onTurnAllOff = {},
                    onOpenInspector = {},
                    onRequestCameraPermission = {},
                    onRescanHardware = {},
                    onDismissNotice = {},
                )
            }
        }

        composeRule.onNodeWithTag("inspector_button").assertIsDisplayed()
        composeRule.onNodeWithTag("rear_torch_toggle").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("rear_brightness_slider").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("front_led_toggle").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `all off button appears when light is enabled and invokes callback`() {
        var allOffClicked = false
        val uiState = TorchControllerUiState(
            rearTorch = TorchUnitState(
                title = "REAR TORCH",
                cameraId = "0",
                isAvailable = true,
                isEnabled = true,
                availabilityReason = "Camera ID 0 · Standard on/off hardware torch",
            ),
            frontLed = TorchUnitState(
                title = "FRONT LED",
                cameraId = "1",
                isAvailable = false,
                availabilityReason = "Front camera HAL reports FLASH_INFO_AVAILABLE=false",
            ),
        )

        composeRule.setContent {
            DualTorchTheme {
                TorchScreen(
                    state = uiState,
                    innerPadding = PaddingValues(),
                    onToggleRear = {},
                    onRearStrengthChange = {},
                    onToggleFront = {},
                    onFrontStrengthChange = {},
                    onTurnAllOff = { allOffClicked = true },
                    onOpenInspector = {},
                    onRequestCameraPermission = {},
                    onRescanHardware = {},
                    onDismissNotice = {},
                )
            }
        }

        composeRule.onNodeWithTag("all_off_button").assertIsDisplayed().performClick()
        assertTrue(allOffClicked)
    }
}
