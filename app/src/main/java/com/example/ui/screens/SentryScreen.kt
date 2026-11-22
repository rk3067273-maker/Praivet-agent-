package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.example.R
import com.example.data.hardware.SensorTelemetryState
import com.example.data.local.JarvisSettings
import com.example.data.local.SentryEventEntity
import com.example.ui.SentryState
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.CyanArc
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidObsidian
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SentryScreen(
    settings: JarvisSettings,
    sentryState: SentryState,
    telemetry: SensorTelemetryState,
    sentryEvents: List<SentryEventEntity>,
    onArmSentry: () -> Unit,
    onDisarmSentry: () -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onDeleteEvent: (SentryEventEntity) -> Unit,
    onPermissionMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            onPermissionMessage(context.getString(R.string.error_camera_permission))
        }
    }

    val accent = when (sentryState) {
        SentryState.Disarmed -> CyanArc
        is SentryState.Arming -> TacticalAmber
        SentryState.Armed -> EmeraldOnline
        is SentryState.Triggered -> AlertCrimson
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "TACTICAL SENTRY MODE",
                        style = MaterialTheme.typography.headlineMedium,
                        color = accent
                    )
                    Text(
                        text = "Accelerometer watchdog with silent CameraX intruder snapshot & vocal deterrent",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Sentry Reactor Control Card
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = accent
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (sentryState) {
                                    is SentryState.Triggered -> Icons.Filled.WarningAmber
                                    SentryState.Armed -> Icons.Filled.Security
                                    else -> Icons.Filled.Shield
                                },
                                contentDescription = "Sentry status",
                                tint = accent,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (sentryState) {
                                        SentryState.Disarmed -> "WATCHDOG STANDBY"
                                        is SentryState.Arming -> "ARMING IN ${sentryState.secondsLeft}s…"
                                        SentryState.Armed -> "SENTRY ARMED & MONITORING"
                                        is SentryState.Triggered ->
                                            "BREACH DETECTED (${"%.2f".format(sentryState.peakDelta)} m/s²)"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = accent
                                )
                                Text(
                                    text = when (sentryState) {
                                        SentryState.Disarmed -> "Place device on a flat surface, then arm."
                                        is SentryState.Arming -> "Step back — calibrating inertial baseline."
                                        SentryState.Armed -> "Live delta: ${"%.2f".format(telemetry.accelDelta)} m/s²"
                                        is SentryState.Triggered -> "Deterrent sounded & evidence captured."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (sentryState == SentryState.Disarmed) {
                                Button(
                                    onClick = onArmSentry,
                                    enabled = telemetry.hasAccelerometer,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldOnline,
                                        contentColor = VoidObsidian
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("arm_sentry_button")
                                ) {
                                    Text("Arm Sentry Mode", style = MaterialTheme.typography.labelLarge)
                                }
                            } else {
                                Button(
                                    onClick = onDisarmSentry,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AlertCrimson,
                                        contentColor = TextPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("disarm_sentry_button")
                                ) {
                                    Text("Disarm Sentry", style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            if (!hasCameraPermission) {
                                OutlinedButton(
                                    onClick = {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("grant_camera_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CameraAlt,
                                        contentDescription = null,
                                        tint = CyanArc,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Camera", color = CyanArc)
                                }
                            }
                        }
                    }
                }
            }

            // Sensitivity Threshold Card
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = CyanArc
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "MOTION TRIGGER THRESHOLD",
                                style = MaterialTheme.typography.labelLarge,
                                color = CyanArc
                            )
                            Text(
                                text = "${"%.1f".format(settings.sentrySensitivity)} m/s²",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                        Slider(
                            value = settings.sentrySensitivity,
                            onValueChange = onSensitivityChange,
                            valueRange = 1.2f..8.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = CyanArc,
                                activeTrackColor = CyanArc
                            ),
                            modifier = Modifier.testTag("sentry_sensitivity_slider")
                        )
                        Text(
                            text = "Lower values trigger on subtle table vibration; higher values require pickup.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Intruder Evidence Gallery Header
            item {
                Text(
                    text = "INCIDENT EVIDENCE LOG (${sentryEvents.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
            }

            if (sentryEvents.isEmpty()) {
                item {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = CyanArc
                    ) {
                        Text(
                            text = "No intrusion events recorded. When Sentry Mode triggers, timestamps, G-force spikes, and front-camera captures appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                items(sentryEvents, key = { it.id }) { event ->
                    SentryEventCard(
                        event = event,
                        onDelete = { onDeleteEvent(event) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SentryEventCard(
    event: SentryEventEntity,
    onDelete: () -> Unit
) {
    val formatter = remember {
        SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    }
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = AlertCrimson
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!event.photoPath.isNullOrBlank() && File(event.photoPath).exists()) {
                Image(
                    painter = rememberAsyncImagePainter(File(event.photoPath)),
                    contentDescription = "Captured sentry intruder snapshot",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, AlertCrimson, RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.summary,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatter.format(Date(event.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                if (event.photoPath == null) {
                    Text(
                        text = "No camera frame (grant Camera permission for photo capture)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalAmber
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete sentry event",
                    tint = TextSecondary
                )
            }
        }
    }
}
