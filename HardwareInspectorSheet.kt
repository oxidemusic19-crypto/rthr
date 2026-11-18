package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.hardware.CameraProbeInfo
import com.example.hardware.DeviceHardwareReport
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.FrontCyanBright
import com.example.ui.theme.FrontCyanContainer
import com.example.ui.theme.FrontCyanPrimary
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.RearAmberBright
import com.example.ui.theme.RearAmberPrimary
import com.example.ui.theme.StatusCoralError
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceObsidian
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareInspectorSheet(
    report: DeviceHardwareReport?,
    cameraPermissionGranted: Boolean,
    onRescan: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var copiedFeedback by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceObsidian,
        contentColor = TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeveloperBoard,
                        contentDescription = null,
                        tint = FrontCyanPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(R.string.inspector_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                }

                OutlinedButton(
                    onClick = onRescan,
                    modifier = Modifier.testTag("rescan_hardware_button"),
                    border = BorderStroke(1.dp, HairlineBorder),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = RearAmberPrimary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RESCAN",
                        style = MaterialTheme.typography.labelMedium,
                        color = RearAmberBright,
                    )
                }
            }

            if (report == null) {
                Text(
                    text = "Hardware scan pending...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
                // Device Identity Block
                InspectorCard(title = "DEVICE & PLATFORM") {
                    KeyValueRow("Model", "${report.manufacturer} ${report.model} (${report.device})")
                    KeyValueRow("SoC / Board", "${report.socModel} · ${report.hardware} · ${report.board}")
                    KeyValueRow("Android", "Android ${report.androidRelease} (API ${report.sdkInt})")
                    KeyValueRow(
                        "Features",
                        "flash=${report.hasCameraFlashFeature}, frontCam=${report.hasFrontCameraFeature}",
                    )
                    KeyValueRow(
                        "Camera Perm",
                        if (cameraPermissionGranted) "GRANTED" else "NOT GRANTED (optional for std torch)",
                    )
                }

                if (!cameraPermissionGranted) {
                    Button(
                        onClick = onRequestCameraPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("inspector_grant_permission_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FrontCyanContainer,
                            contentColor = FrontCyanBright,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, FrontCyanPrimary.copy(alpha = 0.5f)),
                    ) {
                        Text(
                            text = stringResource(R.string.grant_camera_permission),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                // Front LED Verdict Block
                Surface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "FRONT LED HAL VERDICT",
                            style = MaterialTheme.typography.labelMedium,
                            color = FrontCyanPrimary,
                        )
                        Text(
                            text = report.frontLedVerdict,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                    }
                }

                // Camera2 Enumeration
                InspectorCard(title = "CAMERA2 ID ENUMERATION (${report.cameras.size})") {
                    if (report.cameras.isEmpty()) {
                        Text(
                            text = "No camera IDs exposed by CameraManager.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusCoralError,
                        )
                    } else {
                        report.cameras.forEachIndexed { index, cam ->
                            CameraProbeRow(cam)
                            if (index < report.cameras.lastIndex) {
                                HorizontalDivider(
                                    color = HairlineBorder,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }

                // Sysfs Probe
                InspectorCard(title = "SYSFS LED NODES (READ-ONLY CHECK)") {
                    report.sysfsProbes.forEach { probe ->
                        Column(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = probe.path,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                            )
                            Text(
                                text = "exists=${probe.exists} · read=${probe.readable} · write=${probe.writable} · ${probe.note}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (probe.writable) StatusEmerald else TextDim,
                            )
                        }
                    }
                }

                // Copy report action
                Button(
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText("Dual Torch Hardware Report", report.toFormattedText())
                        )
                        copiedFeedback = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("copy_report_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RearAmberPrimary,
                        contentColor = AmoledBlack,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = if (copiedFeedback) Icons.Outlined.CheckCircle else Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (copiedFeedback) {
                            stringResource(R.string.report_copied).uppercase()
                        } else {
                            stringResource(R.string.copy_report).uppercase()
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectorCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        color = SurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun KeyValueRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = TextDim,
            modifier = Modifier.width(104.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CameraProbeRow(cam: CameraProbeInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ID ${cam.cameraId} · ${cam.lensFacing.label}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = if (cam.flashAvailable) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = if (cam.flashAvailable) StatusEmerald else StatusWarning,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = if (cam.flashAvailable) "FLASH=TRUE" else "FLASH=FALSE",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (cam.flashAvailable) StatusEmerald else TextSecondary,
                )
            }
        }
        Text(
            text = if (cam.supportsStrengthLevel) {
                "Strength Levels: 1..${cam.maxStrengthLevel} (default ${cam.defaultStrengthLevel})"
            } else {
                "Strength Levels: Fixed (max=${cam.maxStrengthLevel})"
            },
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        if (cam.physicalCameraIds.isNotEmpty()) {
            Text(
                text = "Physical IDs: ${cam.physicalCameraIds.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
            )
        }
        if (cam.aeModes.isNotEmpty()) {
            Text(
                text = "AE Modes: ${cam.aeModes.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
            )
        }
        if (cam.vendorFlashKeys.isNotEmpty()) {
            Text(
                text = "Vendor Flash Tags (read-only): ${cam.vendorFlashKeys.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = FrontCyanPrimary,
            )
        }
    }
}
