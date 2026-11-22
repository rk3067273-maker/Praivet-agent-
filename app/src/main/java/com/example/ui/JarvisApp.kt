package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.components.CyberGridBackground
import com.example.ui.screens.HudScreen
import com.example.ui.screens.SensorsScreen
import com.example.ui.screens.SentryScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.screens.WorkflowsScreen
import com.example.ui.theme.CyanArc
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TextSecondary

sealed class JarvisDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Hud : JarvisDestination(
        route = "hud",
        labelRes = R.string.nav_hud,
        selectedIcon = Icons.Filled.Hub,
        unselectedIcon = Icons.Outlined.Hub
    )

    data object Sensors : JarvisDestination(
        route = "sensors",
        labelRes = R.string.nav_sensors,
        selectedIcon = Icons.Filled.PanTool,
        unselectedIcon = Icons.Outlined.PanTool
    )

    data object Sentry : JarvisDestination(
        route = "sentry",
        labelRes = R.string.nav_sentry,
        selectedIcon = Icons.Filled.Security,
        unselectedIcon = Icons.Outlined.Security
    )

    data object Workflows : JarvisDestination(
        route = "workflows",
        labelRes = R.string.nav_workflows,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome
    )

    data object Vault : JarvisDestination(
        route = "vault",
        labelRes = R.string.nav_vault,
        selectedIcon = Icons.Filled.Storage,
        unselectedIcon = Icons.Outlined.Storage
    )
}

private val JarvisDestinations = listOf(
    JarvisDestination.Hud,
    JarvisDestination.Sensors,
    JarvisDestination.Sentry,
    JarvisDestination.Workflows,
    JarvisDestination.Vault
)

@Composable
fun JarvisApp(
    viewModel: JarvisViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: JarvisDestination.Hud.route
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val draftCommand by viewModel.draftCommand.collectAsStateWithLifecycle()
    val maskedApiKey by viewModel.maskedApiKeyForSelectedProvider.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKeyForSelectedProvider.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val workflows by viewModel.workflows.collectAsStateWithLifecycle()
    val sentryEvents by viewModel.sentryEvents.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val usageSummary by viewModel.usageSummary.collectAsStateWithLifecycle()
    val dbIntegrityHealthy by viewModel.databaseIntegrityHealthy.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val aiRunState by viewModel.aiRunState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val sentryState by viewModel.sentryState.collectAsStateWithLifecycle()
    val isTorchOn by viewModel.isTorchOn.collectAsStateWithLifecycle()
    val pendingAction by viewModel.pendingActionConfirmation.collectAsStateWithLifecycle()
    val workflowGenerating by viewModel.workflowGenerating.collectAsStateWithLifecycle()
    val workflowExecution by viewModel.workflowExecution.collectAsStateWithLifecycle()
    val connectionTesting by viewModel.connectionTesting.collectAsStateWithLifecycle()
    val connectionTestResult by viewModel.connectionTestResult.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onAppForeground()
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.statusBanner.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        viewModel.sentryCaptureSignal.collect { peakDelta ->
            viewModel.onSentryCaptureReady(lifecycleOwner, peakDelta)
        }
    }

    val backgroundAccent = when (sentryState) {
        is SentryState.Triggered -> TacticalAmber
        else -> CyanArc
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        CyberGridBackground(
            accentColor = backgroundAccent,
            modifier = Modifier.fillMaxSize()
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = DeepSpace.copy(alpha = 0.94f),
                            tonalElevation = 8.dp
                        ) {
                            JarvisDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(dest.labelRes),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = CyanArc,
                                        selectedTextColor = CyanArc,
                                        indicatorColor = CyanArc.copy(alpha = 0.16f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(
                            containerColor = DeepSpace.copy(alpha = 0.92f),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            JarvisDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(dest.labelRes),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = CyanArc,
                                        selectedTextColor = CyanArc,
                                        indicatorColor = CyanArc.copy(alpha = 0.16f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("rail_${dest.route}")
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        NavHost(
                            navController = navController,
                            startDestination = JarvisDestination.Hud.route
                        ) {
                            composable(JarvisDestination.Hud.route) {
                                HudScreen(
                                    settings = settings,
                                    draftCommand = draftCommand,
                                    messages = messages,
                                    telemetry = telemetry,
                                    aiRunState = aiRunState,
                                    voiceState = voiceState,
                                    partialTranscript = partialTranscript,
                                    sentryState = sentryState,
                                    isTorchOn = isTorchOn,
                                    pendingAction = pendingAction,
                                    onDraftCommandChange = viewModel::updateDraftCommand,
                                    onSendCommand = viewModel::sendCommand,
                                    onCancelAi = viewModel::cancelAiGeneration,
                                    onConfirmPendingAction = viewModel::confirmPendingAction,
                                    onDismissPendingAction = viewModel::dismissPendingAction,
                                    onTapVoiceMic = viewModel::startSingleVoiceCommand,
                                    onStopVoice = viewModel::stopVoiceListening,
                                    onToggleWakeWord = viewModel::setWakeWordEnabled,
                                    onToggleTts = viewModel::setTtsEnabled,
                                    onToggleTorch = viewModel::toggleTorch,
                                    onClearChat = viewModel::clearChatHistory,
                                    onDismissError = viewModel::dismissAiError,
                                    onPermissionDenied = viewModel::emitBanner
                                )
                            }

                            composable(JarvisDestination.Sensors.route) {
                                SensorsScreen(
                                    settings = settings,
                                    telemetry = telemetry,
                                    onToggleAirGestures = viewModel::setAirGesturesEnabled,
                                    onRefreshPower = viewModel::refreshPowerAndNetwork
                                )
                            }

                            composable(JarvisDestination.Sentry.route) {
                                SentryScreen(
                                    settings = settings,
                                    sentryState = sentryState,
                                    telemetry = telemetry,
                                    sentryEvents = sentryEvents,
                                    onArmSentry = viewModel::armSentry,
                                    onDisarmSentry = viewModel::disarmSentry,
                                    onSensitivityChange = viewModel::setSentrySensitivity,
                                    onDeleteEvent = viewModel::deleteSentryEvent,
                                    onPermissionMessage = viewModel::emitBanner
                                )
                            }

                            composable(JarvisDestination.Workflows.route) {
                                WorkflowsScreen(
                                    settings = settings,
                                    maskedApiKey = maskedApiKey,
                                    hasApiKey = hasApiKey,
                                    workflows = workflows,
                                    workflowGenerating = workflowGenerating,
                                    workflowExecution = workflowExecution,
                                    connectionTesting = connectionTesting,
                                    connectionTestResult = connectionTestResult,
                                    parseSteps = viewModel::parseSteps,
                                    onSelectProvider = viewModel::selectProvider,
                                    onSaveProviderConfig = viewModel::saveActiveProviderConfig,
                                    onSaveApiKey = viewModel::saveApiKeyForProvider,
                                    onClearApiKey = viewModel::clearApiKeyForProvider,
                                    onTestConnection = viewModel::testProviderConnection,
                                    onSetMaxAgentSteps = viewModel::setMaxAgentSteps,
                                    onSaveWebhook = viewModel::updateWebhookUrl,
                                    onGenerateWorkflow = viewModel::generateWorkflow,
                                    onExecuteWorkflow = viewModel::executeWorkflow,
                                    onCancelWorkflow = viewModel::cancelWorkflowExecution,
                                    onDeleteWorkflow = viewModel::deleteWorkflow
                                )
                            }

                            composable(JarvisDestination.Vault.route) {
                                VaultScreen(
                                    settings = settings,
                                    memories = memories,
                                    usageSummary = usageSummary,
                                    databaseIntegrityHealthy = dbIntegrityHealthy,
                                    onSaveMemory = viewModel::saveMemory,
                                    onUpdateMemory = viewModel::updateMemoryEntry,
                                    onDeleteMemory = viewModel::deleteMemoryEntry,
                                    onImportMemoriesJson = viewModel::importMemoriesJson,
                                    onExportAllData = viewModel::exportAllDataToUri,
                                    onSetUsageWarningLimit = viewModel::setUsageWarningTokenLimit,
                                    onClearTemporaryCache = viewModel::clearTemporaryCache,
                                    onRevokeCredentials = viewModel::revokeAllCredentialsAndWebhook,
                                    onWipeAllLocalData = viewModel::wipeAllLocalData
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
