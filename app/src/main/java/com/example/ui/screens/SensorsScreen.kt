package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.hardware.SensorTelemetryState
import com.example.data.local.JarvisSettings
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.CyanArc
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.IceBlue
import com.example.ui.theme.NeuralViolet
import com.example.ui.theme.PlasmaTeal
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidObsidian
import kotlin.math.abs

@Composable
fun SensorsScreen(
    settings: JarvisSettings,
    telemetry: SensorTelemetryState,
    onToggleAirGestures: (Boolean) -> Unit,
    onRefreshPower: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                        text = "ZERO-TOUCH AIR GESTURES",
                        style = MaterialTheme.typography.headlineMedium,
                        color = CyanArc
                    )
                    Text(
                        text = "Optical proximity sensor gesture engine & live hardware telemetry",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Air Gesture Master Control Card
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = if (settings.airGesturesEnabled) PlasmaTeal else CyanArc
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PanTool,
                                    contentDescription = "Air Gestures",
                                    tint = if (settings.airGesturesEnabled) PlasmaTeal else CyanArc,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Optical Air Gesture Engine",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = if (telemetry.hasProximitySensor) {
                                            "Sensor: ${telemetry.proximitySensorName}"
                                        } else {
                                            "No hardware proximity sensor detected"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = settings.airGesturesEnabled,
                                onCheckedChange = onToggleAirGestures,
                                enabled = telemetry.hasProximitySensor,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = VoidObsidian,
                                    checkedTrackColor = PlasmaTeal
                                ),
                                modifier = Modifier.testTag("air_gestures_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "PROXIMITY FIELD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Text(
                                    text = if (!telemetry.hasProximitySensor) {
                                        "UNAVAILABLE"
                                    } else if (telemetry.isProximityNear) {
                                        "NEAR (${"%.1f".format(telemetry.proximityDistanceCm)} cm)"
                                    } else {
                                        "FAR (${"%.1f".format(telemetry.proximityDistanceCm)} cm)"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (telemetry.isProximityNear) PlasmaTeal else TextPrimary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "GESTURES TRIGGERED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Text(
                                    text = "${telemetry.gestureCount}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = CyanArc
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Last Gesture: ${telemetry.lastGestureLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = IceBlue
                        )
                    }
                }
            }

            // Gesture Mappings Guide
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = IceBlue
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "GESTURE PROTOCOL MATRIX",
                            style = MaterialTheme.typography.labelLarge,
                            color = IceBlue
                        )
                        GestureMappingRow(
                            icon = Icons.Filled.SkipNext,
                            gesture = "Quick Palm Wave (< 0.75s)",
                            action = "Skip to Next Media Track",
                            color = CyanArc
                        )
                        GestureMappingRow(
                            icon = Icons.Filled.Sensors,
                            gesture = "Double Palm Wave",
                            action = "Toggle Media Play / Pause",
                            color = NeuralViolet
                        )
                        GestureMappingRow(
                            icon = Icons.Filled.FlashlightOn,
                            gesture = "Sustained Palm Hover (1.0s)",
                            action = "Toggle Tactical Flashlight",
                            color = TacticalAmber
                        )
                    }
                }
            }

            // Live 3-Axis Accelerometer Telemetry
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = NeuralViolet
                ) {
                    Column {
                        Text(
                            text = "3-AXIS INERTIAL ACCELEROMETER",
                            style = MaterialTheme.typography.labelLarge,
                            color = NeuralViolet
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (!telemetry.hasAccelerometer) {
                            Text(
                                text = "Accelerometer sensor unavailable on this device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        } else {
                            AxisMeterRow("X-Axis", telemetry.accelX, CyanArc)
                            AxisMeterRow("Y-Axis", telemetry.accelY, PlasmaTeal)
                            AxisMeterRow("Z-Axis", telemetry.accelZ, NeuralViolet)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Magnitude: ${"%.2f".format(telemetry.accelMagnitude)} m/s²",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Delta: ${"%.2f".format(telemetry.accelDelta)} m/s²",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TacticalAmber
                                )
                            }
                        }
                    }
                }
            }

            // Ambient Light & System Power/Network
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidGlassCard(
                        modifier = Modifier.weight(1f),
                        accentColor = TacticalAmber
                    ) {
                        Column {
                            Icon(
                                imageVector = Icons.Filled.LightMode,
                                contentDescription = "Ambient Light",
                                tint = TacticalAmber
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "AMBIENT LUX",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Text(
                                text = if (telemetry.hasLightSensor) {
                                    "${"%.0f".format(telemetry.ambientLux)} lx"
                                } else {
                                    "N/A"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                        }
                    }

                    LiquidGlassCard(
                        modifier = Modifier.weight(1f),
                        accentColor = EmeraldOnline
                    ) {
                        Column {
                            Icon(
                                imageVector = Icons.Filled.BatteryChargingFull,
                                contentDescription = "Power status",
                                tint = EmeraldOnline
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (telemetry.isCharging) "CHARGING" else "BATTERY",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Text(
                                text = "${telemetry.batteryPercent}%",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = IceBlue
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Wifi,
                                contentDescription = "Network transport",
                                tint = IceBlue
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "NETWORK TRANSPORT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Text(
                                    text = telemetry.networkTransport,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                        Button(
                            onClick = onRefreshPower,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IceBlue.copy(alpha = 0.2f),
                                contentColor = IceBlue
                            ),
                            modifier = Modifier.testTag("refresh_telemetry_button")
                        ) {
                            Text("Poll")
                        }
                    }
                }
            }

            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = CyanArc
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "RUNTIME HARDWARE COMPATIBILITY MATRIX",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyanArc
                        )
                        HardwareStatusRow("Proximity Sensor", telemetry.hasProximitySensor, telemetry.proximityAccuracyLabel)
                        HardwareStatusRow("Accelerometer", telemetry.hasAccelerometer, telemetry.accelAccuracyLabel)
                        HardwareStatusRow("Gyroscope", telemetry.hasGyroscope, null)
                        HardwareStatusRow("Ambient Light", telemetry.hasLightSensor, null)
                        HardwareStatusRow("Camera Hardware", telemetry.hasCamera, null)
                        HardwareStatusRow("Tactical Flash", telemetry.hasCameraFlash, null)
                        HardwareStatusRow("Microphone", telemetry.hasMicrophone, null)
                        HardwareStatusRow("Vibration Motor", telemetry.hasVibrator, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareStatusRow(name: String, available: Boolean, accuracy: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Text(
            text = if (available) {
                if (accuracy != null && accuracy != "Unknown") "READY ($accuracy)" else "READY"
            } else {
                "UNAVAILABLE"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (available) EmeraldOnline else TacticalAmber
        )
    }
}

@Composable
private fun GestureMappingRow(
    icon: ImageVector,
    gesture: String,
    action: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = gesture, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(text = action, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun AxisMeterRow(label: String, value: Float, color: Color) {
    val normalized = (abs(value) / 19.6f).coerceIn(0f, 1f)
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(
                text = "${"%.2f".format(value)} m/s²",
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { normalized },
            color = color,
            trackColor = color.copy(alpha = 0.14f),
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
    }
}
