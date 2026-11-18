package com.example.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.hardware.TorchControllerUiState
import com.example.hardware.TorchUnitState
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.FrontCyanBright
import com.example.ui.theme.FrontCyanContainer
import com.example.ui.theme.FrontCyanGlow
import com.example.ui.theme.FrontCyanPrimary
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.RearAmberBright
import com.example.ui.theme.RearAmberContainer
import com.example.ui.theme.RearAmberGlow
import com.example.ui.theme.RearAmberPrimary
import com.example.ui.theme.StatusCoralError
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceMuted
import com.example.ui.theme.SurfaceObsidian
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun TorchScreen(
    state: TorchControllerUiState,
    innerPadding: PaddingValues,
    onToggleRear: () -> Unit,
    onRearStrengthChange: (Int) -> Unit,
    onToggleFront: () -> Unit,
    onFrontStrengthChange: (Int) -> Unit,
    onTurnAllOff: () -> Unit,
    onOpenInspector: (Boolean) -> Unit,
    onRequestCameraPermission: () -> Unit,
    onRescanHardware: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    val anyLightOn = state.rearTorch.isEnabled || state.frontLed.isEnabled

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(innerPadding),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val isWideLayout = maxWidth >= 640.dp

            Column(
                modifier = Modifier
                    .widthIn(max = if (isWideLayout) 960.dp else 560.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                HeaderBar(
                    deviceModel = state.hardwareReport?.let { "${it.manufacturer} ${it.model}" }
                        ?: "${Build.MANUFACTURER} ${Build.MODEL}",
                    socLabel = state.hardwareReport?.socModel ?: Build.HARDWARE,
                    rearOn = state.rearTorch.isEnabled,
                    frontOn = state.frontLed.isEnabled,
                    onOpenInspector = { onOpenInspector(true) },
                )

                AnimatedVisibility(visible = state.globalNotice != null) {
                    state.globalNotice?.let { notice ->
                        NoticeBanner(
                            message = notice,
                            onDismiss = onDismissNotice,
                        )
                    }
                }

                if (isWideLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        TorchControlModule(
                            unit = state.rearTorch,
                            accentPrimary = RearAmberPrimary,
                            accentBright = RearAmberBright,
                            accentGlow = RearAmberGlow,
                            accentContainer = RearAmberContainer,
                            activeIcon = Icons.Filled.FlashlightOn,
                            inactiveIcon = Icons.Outlined.FlashlightOff,
                            toggleTestTag = "rear_torch_toggle",
                            sliderTestTag = "rear_brightness_slider",
                            onToggle = onToggleRear,
                            onStrengthChange = onRearStrengthChange,
                            onRequestPermission = onRequestCameraPermission,
                            onOpenInspector = { onOpenInspector(true) },
                            modifier = Modifier.weight(1f),
                        )

                        TorchControlModule(
                            unit = state.frontLed,
                            accentPrimary = FrontCyanPrimary,
                            accentBright = FrontCyanBright,
                            accentGlow = FrontCyanGlow,
                            accentContainer = FrontCyanContainer,
                            activeIcon = Icons.Filled.LightMode,
                            inactiveIcon = Icons.Outlined.LightMode,
                            toggleTestTag = "front_led_toggle",
                            sliderTestTag = "front_brightness_slider",
                            onToggle = onToggleFront,
                            onStrengthChange = onFrontStrengthChange,
                            onRequestPermission = onRequestCameraPermission,
                            onOpenInspector = { onOpenInspector(true) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    TorchControlModule(
                        unit = state.rearTorch,
                        accentPrimary = RearAmberPrimary,
                        accentBright = RearAmberBright,
                        accentGlow = RearAmberGlow,
                        accentContainer = RearAmberContainer,
                        activeIcon = Icons.Filled.FlashlightOn,
                        inactiveIcon = Icons.Outlined.FlashlightOff,
                        toggleTestTag = "rear_torch_toggle",
                        sliderTestTag = "rear_brightness_slider",
                        onToggle = onToggleRear,
                        onStrengthChange = onRearStrengthChange,
                        onRequestPermission = onRequestCameraPermission,
                        onOpenInspector = { onOpenInspector(true) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    TorchControlModule(
                        unit = state.frontLed,
                        accentPrimary = FrontCyanPrimary,
                        accentBright = FrontCyanBright,
                        accentGlow = FrontCyanGlow,
                        accentContainer = FrontCyanContainer,
                        activeIcon = Icons.Filled.LightMode,
                        inactiveIcon = Icons.Outlined.LightMode,
                        toggleTestTag = "front_led_toggle",
                        sliderTestTag = "front_brightness_slider",
                        onToggle = onToggleFront,
                        onStrengthChange = onFrontStrengthChange,
                        onRequestPermission = onRequestCameraPermission,
                        onOpenInspector = { onOpenInspector(true) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Bottom padding so floating ALL OFF button never overlaps content
                Spacer(modifier = Modifier.height(if (anyLightOn) 80.dp else 16.dp))
            }
        }

        // Emergency / Reliable ALL OFF floating pill when either light is active
        AnimatedVisibility(
            visible = anyLightOn,
            enter = fadeIn(tween(200)) + slideInVertically { it / 2 },
            exit = fadeOut(tween(180)) + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp, start = 20.dp, end = 20.dp),
        ) {
            Button(
                onClick = onTurnAllOff,
                modifier = Modifier
                    .height(54.dp)
                    .widthIn(min = 210.dp)
                    .testTag("all_off_button"),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusCoralError,
                    contentColor = AmoledBlack,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.all_off_button),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    if (state.isInspectorOpen) {
        HardwareInspectorSheet(
            report = state.hardwareReport,
            cameraPermissionGranted = state.cameraPermissionGranted,
            onRescan = onRescanHardware,
            onRequestCameraPermission = onRequestCameraPermission,
            onDismiss = { onOpenInspector(false) },
        )
    }
}

@Composable
private fun HeaderBar(
    deviceModel: String,
    socLabel: String,
    rearOn: Boolean,
    frontOn: Boolean,
    onOpenInspector: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "DUAL TORCH",
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusDot(
                    active = rearOn || frontOn,
                    activeColor = when {
                        rearOn && frontOn -> StatusEmerald
                        rearOn -> RearAmberPrimary
                        frontOn -> FrontCyanPrimary
                        else -> TextDim
                    },
                )
                Text(
                    text = "${deviceModel.uppercase()} · ${socLabel.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Surface(
            color = SurfaceObsidian,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, HairlineBorder),
        ) {
            IconButton(
                onClick = onOpenInspector,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("inspector_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeveloperBoard,
                    contentDescription = stringResource(R.string.inspector_button_cd),
                    tint = FrontCyanPrimary,
                )
            }
        }
    }
}

@Composable
private fun TorchControlModule(
    unit: TorchUnitState,
    accentPrimary: Color,
    accentBright: Color,
    accentGlow: Color,
    accentContainer: Color,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    toggleTestTag: String,
    sliderTestTag: String,
    onToggle: () -> Unit,
    onStrengthChange: (Int) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenInspector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val isLit = unit.isEnabled && unit.isAvailable

    val borderColor by animateColorAsState(
        targetValue = when {
            unit.errorMessage != null || unit.isUnavailableTemporarily -> StatusCoralError.copy(alpha = 0.65f)
            isLit -> accentPrimary.copy(alpha = 0.85f)
            !unit.isAvailable -> HairlineBorder.copy(alpha = 0.6f)
            else -> HairlineBorder
        },
        animationSpec = tween(250),
        label = "module_border",
    )

    val containerBg by animateColorAsState(
        targetValue = if (isLit) accentContainer.copy(alpha = 0.48f) else SurfaceObsidian,
        animationSpec = tween(280),
        label = "module_bg",
    )

    Surface(
        modifier = modifier,
        color = containerBg,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top Status Row: Title + ON/OFF Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = unit.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isLit) accentBright else TextPrimary,
                    )
                    Text(
                        text = unit.cameraId?.let { "CAMERA ID $it" } ?: "NO HARDWARE ID",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim,
                    )
                }

                StatusPill(
                    unit = unit,
                    accentPrimary = accentPrimary,
                    accentBright = accentBright,
                )
            }

            // Centerpiece Large Glowing Toggle Button
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                GlowingTorchButton(
                    isAvailable = unit.isAvailable,
                    isEnabled = isLit,
                    isBusy = unit.isBusy,
                    title = unit.title,
                    accentPrimary = accentPrimary,
                    accentBright = accentBright,
                    accentGlow = accentGlow,
                    activeIcon = activeIcon,
                    inactiveIcon = inactiveIcon,
                    testTag = toggleTestTag,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    },
                )
            }

            // Hardware Brightness Slider (only when hardware exposes >1 strength levels)
            if (unit.isAvailable && unit.supportsStrengthControl && unit.maxStrengthLevel > 1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.brightness_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                        )
                        Text(
                            text = "LVL ${unit.currentStrengthLevel} / ${unit.maxStrengthLevel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isLit) accentBright else TextPrimary,
                        )
                    }

                    Slider(
                        value = unit.currentStrengthLevel.toFloat(),
                        onValueChange = { raw ->
                            val snapped = raw.roundToInt().coerceIn(1, unit.maxStrengthLevel)
                            if (snapped != unit.currentStrengthLevel) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onStrengthChange(snapped)
                            }
                        },
                        valueRange = 1f..unit.maxStrengthLevel.toFloat(),
                        steps = (unit.maxStrengthLevel - 2).coerceAtLeast(0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(sliderTestTag),
                        colors = SliderDefaults.colors(
                            thumbColor = accentBright,
                            activeTrackColor = accentPrimary,
                            inactiveTrackColor = SurfaceMuted,
                            activeTickColor = AmoledBlack.copy(alpha = 0.4f),
                            inactiveTickColor = TextDim,
                        ),
                    )
                }
            } else if (unit.isAvailable) {
                Surface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.brightness_fixed),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }

            // Availability / Control Path & Diagnostics
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = unit.availabilityReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unit.isAvailable) TextSecondary else StatusWarning,
                )
                Text(
                    text = "PATH: ${unit.activeControlPath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                )
            }

            // Error or Permission Prompt Box
            if (unit.errorMessage != null) {
                Surface(
                    color = StatusErrorContainer,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, StatusCoralError.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = StatusCoralError,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = unit.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                            )
                        }

                        if (unit.requiresCameraPermissionForFallback) {
                            Button(
                                onClick = onRequestPermission,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("grant_permission_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FrontCyanPrimary,
                                    contentColor = AmoledBlack,
                                ),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.grant_camera_permission).uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            } else if (!unit.isAvailable) {
                Surface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenInspector() }
                        .minimumInteractiveComponentSize(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Tap to view Camera2 & HAL diagnostic details",
                            style = MaterialTheme.typography.labelMedium,
                            color = FrontCyanPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlowingTorchButton(
    isAvailable: Boolean,
    isEnabled: Boolean,
    isBusy: Boolean,
    title: String,
    accentPrimary: Color,
    accentBright: Color,
    accentGlow: Color,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isEnabled) 1.04f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "torch_button_scale",
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0f,
        animationSpec = tween(260),
        label = "torch_glow_alpha",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    val coreColor by animateColorAsState(
        targetValue = when {
            !isAvailable -> SurfaceMuted.copy(alpha = 0.45f)
            isEnabled -> accentPrimary
            else -> SurfaceElevated
        },
        animationSpec = tween(220),
        label = "core_color",
    )

    val iconTint by animateColorAsState(
        targetValue = when {
            !isAvailable -> TextDim
            isEnabled -> AmoledBlack
            else -> accentPrimary.copy(alpha = 0.85f)
        },
        animationSpec = tween(220),
        label = "icon_tint",
    )

    val ringColor by animateColorAsState(
        targetValue = when {
            !isAvailable -> HairlineBorder.copy(alpha = 0.5f)
            isEnabled -> accentBright
            else -> accentPrimary.copy(alpha = 0.45f)
        },
        animationSpec = tween(220),
        label = "ring_color",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val stateLabel = when {
        !isAvailable -> "$title hardware unavailable"
        isEnabled -> "$title is ON, tap to turn OFF"
        else -> "$title is OFF, tap to turn ON"
    }

    Box(
        modifier = Modifier
            .size(188.dp)
            .scale(scale),
        contentAlignment = Alignment.Center,
    ) {
        // Ambient radial glow rendered directly on Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (glowAlpha > 0.01f) {
                val outerRadius = (size.minDimension / 2f) * pulseScale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentGlow.copy(alpha = 0.46f * glowAlpha),
                            accentPrimary.copy(alpha = 0.20f * glowAlpha),
                            Color.Transparent,
                        ),
                        radius = outerRadius,
                    ),
                    radius = outerRadius,
                )
            }
            drawCircle(
                color = ringColor.copy(alpha = if (isEnabled) 0.9f else 0.4f),
                radius = size.minDimension * 0.37f,
                style = Stroke(width = if (isEnabled) 3.dp.toPx() else 1.5.dp.toPx()),
            )
        }

        // Tactile circular switch surface
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(CircleShape)
                .background(
                    brush = if (isEnabled) {
                        Brush.radialGradient(
                            colors = listOf(accentBright, coreColor),
                        )
                    } else {
                        Brush.radialGradient(
                            colors = listOf(coreColor, SurfaceObsidian),
                        )
                    }
                )
                .border(
                    width = if (isEnabled) 2.dp else 1.5.dp,
                    color = ringColor,
                    shape = CircleShape,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = accentBright),
                    enabled = !isBusy,
                    role = Role.Switch,
                    onClick = onClick,
                )
                .semantics { contentDescription = stateLabel }
                .testTag(testTag),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (isEnabled) activeIcon else inactiveIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(38.dp),
                )
                Text(
                    text = when {
                        !isAvailable -> "N/A"
                        isBusy -> "..."
                        isEnabled -> stringResource(R.string.status_on)
                        else -> stringResource(R.string.status_off)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isEnabled) AmoledBlack else TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    unit: TorchUnitState,
    accentPrimary: Color,
    accentBright: Color,
) {
    val label = when {
        unit.isUnavailableTemporarily -> stringResource(R.string.status_busy)
        !unit.isAvailable -> stringResource(R.string.status_unavailable)
        unit.isEnabled -> stringResource(R.string.status_on)
        else -> stringResource(R.string.status_off)
    }

    val pillColor = when {
        unit.isUnavailableTemporarily -> StatusCoralError.copy(alpha = 0.16f)
        !unit.isAvailable -> SurfaceMuted
        unit.isEnabled -> accentPrimary.copy(alpha = 0.2f)
        else -> SurfaceElevated
    }

    val textColor = when {
        unit.isUnavailableTemporarily -> StatusCoralError
        !unit.isAvailable -> StatusWarning
        unit.isEnabled -> accentBright
        else -> TextSecondary
    }

    val dotColor = when {
        unit.isUnavailableTemporarily -> StatusCoralError
        !unit.isAvailable -> StatusWarning
        unit.isEnabled -> accentBright
        else -> TextDim
    }

    Surface(
        color = pillColor,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusDot(active = unit.isEnabled, activeColor = dotColor)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
            )
        }
    }
}

@Composable
private fun StatusDot(
    active: Boolean,
    activeColor: Color,
) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (active) activeColor else activeColor.copy(alpha = 0.65f)),
    )
}

@Composable
private fun NoticeBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Surface(
        color = StatusErrorContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StatusCoralError.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "DISMISS",
                style = MaterialTheme.typography.labelMedium,
                color = StatusCoralError,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}
