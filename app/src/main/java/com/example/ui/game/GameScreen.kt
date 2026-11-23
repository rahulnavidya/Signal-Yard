package com.example.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scenarios.GameMode
import com.example.scenarios.ScenarioCatalog
import com.example.sim.*
import com.example.ui.canvas.DioramaCanvas
import com.example.ui.canvas.DioramaTapTarget
import com.example.ui.theme.*

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onExitToMenu: () -> Unit,
    onLaunchNextScenario: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val graph = state.graph
    val snapshot = state.snapshot

    BackHandler {
        if (state.scoreBreakdown != null) {
            onExitToMenu()
        } else {
            viewModel.setShowPauseMenu(!state.showPauseMenu)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GraphiteDeep)
    ) {
        if (graph != null && snapshot != null) {
            DioramaCanvas(
                graph = graph,
                snapshot = snapshot,
                camera = state.camera,
                selectedTrainId = state.selectedTrainId,
                selectedStationId = state.selectedStationId,
                colorblindSymbols = state.settings.colorblindSymbols,
                showRouteGuidance = state.settings.showRouteGuidance,
                onCameraChange = viewModel::updateCamera,
                onTapTarget = viewModel::onTapTarget,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("diorama_canvas")
            )
        }

        // Top Atlas Control HUD (Safe area padded)
        if (snapshot != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Top-Left: Objective & Score Plaque
                Surface(
                    color = ForestGreenDeep.copy(alpha = 0.93f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, BrassGold),
                    tonalElevation = 4.dp,
                    modifier = Modifier.widthIn(max = 390.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Train,
                                contentDescription = null,
                                tint = BrassGoldBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = state.title.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = WarmIvoryPaper,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HudMetricBadge(
                                label = "DELIVERED",
                                value = if (state.mode == GameMode.ZEN_DISPATCH || state.mode == GameMode.RUSH_HOUR) {
                                    "${snapshot.deliveredCount}"
                                } else {
                                    "${snapshot.deliveredCount}/${state.targetDeliveryCount}"
                                }
                            )
                            HudMetricBadge(
                                label = "SCORE",
                                value = "${snapshot.totalScore}"
                            )
                            if (state.mode == GameMode.RUSH_HOUR) {
                                val rem = (state.rushHourDurationSeconds - snapshot.simTimeSeconds).coerceAtLeast(0f).toInt()
                                val mins = rem / 60
                                val secs = rem % 60
                                HudMetricBadge(
                                    label = "TIME LEFT",
                                    value = "%d:%02d".format(mins, secs),
                                    highlight = rem <= 30
                                )
                            } else {
                                HudMetricBadge(
                                    label = "STREAK",
                                    value = "${snapshot.currentStreak}x"
                                )
                            }
                        }
                    }
                }

                // Top-Right: Speed & System Controls
                Surface(
                    color = ForestGreenDeep.copy(alpha = 0.93f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, BrassGold),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SpeedPill(
                            label = if (snapshot.isPaused) "PAUSED" else "PAUSE",
                            selected = snapshot.isPaused,
                            onClick = viewModel::togglePause,
                            modifier = Modifier.testTag("speed_pause_button")
                        )
                        listOf(1f to "1x", 2f to "2x", 3f to "3x").forEach { (mult, text) ->
                            SpeedPill(
                                label = text,
                                selected = !snapshot.isPaused && kotlin.math.abs(snapshot.speedMultiplier - mult) < 0.1f,
                                onClick = { viewModel.setSpeedMultiplier(mult) },
                                modifier = Modifier.testTag("speed_${text}_button")
                            )
                        }
                        IconButton(
                            onClick = viewModel::resetCamera,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CenterFocusStrong,
                                contentDescription = "Fit railway map",
                                tint = WarmIvoryPaper
                            )
                        }
                        IconButton(
                            onClick = viewModel::toggleSoundMute,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (state.settings.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = "Toggle audio",
                                tint = if (state.settings.soundEnabled) BrassGoldBright else WarmIvoryMuted
                            )
                        }
                        if (state.mode == GameMode.RUSH_HOUR) {
                            IconButton(
                                onClick = viewModel::finishRushHourEarly,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Flag,
                                    contentDescription = "Finish Rush Hour session",
                                    tint = BrassGoldBright
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.setShowPauseMenu(true) },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("pause_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Pause menu",
                                tint = WarmIvoryPaper
                            )
                        }
                    }
                }
            }
        }

        // Dynamic Tutorial Instruction Banner (Top-Center)
        state.tutorialHint?.let { hint ->
            Surface(
                color = WarmIvoryPaper.copy(alpha = 0.96f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(2.dp, BrassGoldDark),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 74.dp)
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = ForestGreen,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "STEP ${hint.stepNumber}",
                            style = MaterialTheme.typography.labelMedium,
                            color = BrassGoldBright,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hint.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = GraphiteDeep
                        )
                        Text(
                            text = hint.instruction,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Graphite
                        )
                    }
                }
            }
        }

        // Transient Action / Score Feedback Toast
        AnimatedVisibility(
            visible = state.feedbackBanner != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 14.dp)
        ) {
            state.feedbackBanner?.let { msg ->
                Surface(
                    color = GraphiteDeep.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, BrassGold)
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelLarge,
                        color = WarmIvoryPaper,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Bottom-Left: Operational Alerts & Zen Mode Spawner
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (snapshot != null && snapshot.alerts.isNotEmpty()) {
                val topAlert = snapshot.alerts.first()
                val alertBg = when (topAlert.severity) {
                    AlertSeverity.CRITICAL -> SignalRed.copy(alpha = 0.95f)
                    AlertSeverity.WARNING -> Color(0xFFB3781E).copy(alpha = 0.95f)
                    AlertSeverity.INFO -> ForestGreenDeep.copy(alpha = 0.93f)
                }
                Surface(
                    color = alertBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, WarmIvory),
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .clickable { viewModel.focusOnWorldPoint(topAlert.focusX, topAlert.focusY) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Operational alert",
                            tint = WarmIvoryPaper,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = topAlert.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = WarmIvoryPaper
                            )
                            Text(
                                text = topAlert.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = WarmIvoryPaper.copy(alpha = 0.92f)
                            )
                        }
                    }
                }
            }

            if (state.mode == GameMode.ZEN_DISPATCH) {
                Surface(
                    color = ForestGreenDeep.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, BrassGold)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddCircleOutline,
                                contentDescription = null,
                                tint = BrassGoldBright,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "DISPATCH TRAIN (ZEN)",
                                style = MaterialTheme.typography.labelLarge,
                                color = BrassGoldBright
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ZenSpawnChip("Local W", TrainCategory.LOCAL_PASSENGER, false, viewModel)
                            ZenSpawnChip("Express W", TrainCategory.EXPRESS_PASSENGER, false, viewModel)
                            ZenSpawnChip("Freight W", TrainCategory.FREIGHT, false, viewModel)
                            ZenSpawnChip("Heavy W", TrainCategory.HEAVY_FREIGHT, false, viewModel)
                            ZenSpawnChip("Local E", TrainCategory.LOCAL_PASSENGER, true, viewModel)
                        }
                    }
                }
            }
        }

        // Bottom-Right: Train or Station Inspector Card
        if (snapshot != null && graph != null) {
            val selectedTrain = snapshot.trains.firstOrNull { it.id == state.selectedTrainId }
            val selectedStation = graph.stations[state.selectedStationId]

            if (selectedTrain != null) {
                TrainInspectorCard(
                    train = selectedTrain,
                    graph = graph,
                    isZenMode = state.mode == GameMode.ZEN_DISPATCH,
                    onFocus = {
                        val head = snapshot.trainPoses[selectedTrain.id]?.firstOrNull()
                        if (head != null) viewModel.focusOnWorldPoint(head.x, head.y)
                    },
                    onRemove = viewModel::removeSelectedTrain,
                    onClose = { viewModel.onTapTarget(DioramaTapTarget.Empty) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(12.dp)
                )
            } else if (selectedStation != null) {
                StationInspectorCard(
                    station = selectedStation,
                    trains = snapshot.trains,
                    onClose = { viewModel.onTapTarget(DioramaTapTarget.Empty) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(12.dp)
                )
            }
        }

        // Pause Overlay Dialog
        if (state.showPauseMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x990E1411))
                    .clickable(enabled = true) {},
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = ForestGreenDeep,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, BrassGold),
                    modifier = Modifier.widthIn(min = 320.dp, max = 420.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "OPERATIONS PAUSED",
                            style = MaterialTheme.typography.headlineMedium,
                            color = BrassGoldBright
                        )
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = WarmIvory
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { viewModel.setShowPauseMenu(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = GraphiteDeep),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Resume Dispatch", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = viewModel::restartSession,
                            border = BorderStroke(1.5.dp, WarmSandstone),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Restart Layout", color = WarmIvoryPaper)
                        }
                        OutlinedButton(
                            onClick = onExitToMenu,
                            border = BorderStroke(1.5.dp, SignalRed),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Return to Railway Atlas", color = SignalRedBright)
                        }
                    }
                }
            }
        }

        // Session Completion / Results Report Card
        state.scoreBreakdown?.let { report ->
            SessionResultsModal(
                title = state.title,
                mode = state.mode,
                report = report,
                delivered = snapshot?.deliveredCount ?: 0,
                bestStreak = snapshot?.bestStreak ?: 0,
                isNewBest = state.isNewPersonalBest,
                onReplay = viewModel::restartSession,
                onNextChallenge = {
                    val challenges = ScenarioCatalog.quickDispatchChallenges
                    val currentId = activeScenarioIdFor(state.title)
                    val idx = challenges.indexOfFirst { it.id == currentId }
                    if (state.mode == GameMode.TUTORIAL) {
                        onLaunchNextScenario(challenges.first().id)
                    } else if (idx in 0 until challenges.lastIndex) {
                        onLaunchNextScenario(challenges[idx + 1].id)
                    } else {
                        onExitToMenu()
                    }
                },
                onExit = onExitToMenu
            )
        }
    }
}

private fun activeScenarioIdFor(title: String): String? {
    return ScenarioCatalog.quickDispatchChallenges.firstOrNull { it.title == title }?.id
}

@Composable
private fun HudMetricBadge(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = WarmIvoryMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = if (highlight) SignalRedBright else BrassGoldBright
        )
    }
}

@Composable
private fun SpeedPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) BrassGold else Graphite,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (selected) BrassGoldBright else WarmSandstoneDark),
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) GraphiteDeep else WarmIvoryPaper,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ZenSpawnChip(
    label: String,
    category: TrainCategory,
    fromEast: Boolean,
    viewModel: GameViewModel
) {
    Surface(
        color = ForestGreen,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, WarmSandstone),
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .clickable { viewModel.spawnZenTrain(category, fromEast) }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = "+ $label",
                style = MaterialTheme.typography.labelMedium,
                color = WarmIvoryPaper
            )
        }
    }
}

@Composable
private fun TrainInspectorCard(
    train: Train,
    graph: TrackGraph,
    isZenMode: Boolean,
    onFocus: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nextStopName = train.nextStopStationId?.let { graph.stations[it]?.name } ?: "Direct to Destination"
    val destName = graph.nodes[train.destinationNodeId]?.label ?: train.destinationNodeId

    Surface(
        color = ForestGreenDeep.copy(alpha = 0.96f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, BrassGold),
        tonalElevation = 6.dp,
        modifier = modifier.widthIn(min = 270.dp, max = 330.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = BrassGold, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = train.serviceNumber,
                            style = MaterialTheme.typography.labelLarge,
                            color = GraphiteDeep,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = train.category.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = WarmIvoryPaper
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close inspector", tint = WarmIvoryMuted, modifier = Modifier.size(18.dp))
                }
            }

            HorizontalDivider(color = BrassGoldDark.copy(alpha = 0.45f))

            InspectorRow("Status", train.statusReason)
            InspectorRow("Next Stop", nextStopName)
            InspectorRow("Destination", destName)
            InspectorRow("Speed", "${train.speed.toInt()} / ${train.category.maxSpeed.toInt()} u/s")
            if (train.isDelayed) {
                InspectorRow("Schedule", "+${train.delaySeconds.toInt()}s Delayed", highlightError = true)
            } else {
                InspectorRow("Schedule", "On Time")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                OutlinedButton(
                    onClick = onFocus,
                    border = BorderStroke(1.dp, BrassGold),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Text("Follow", style = MaterialTheme.typography.labelMedium, color = BrassGoldBright)
                }
                if (isZenMode) {
                    OutlinedButton(
                        onClick = onRemove,
                        border = BorderStroke(1.dp, SignalRed),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text("Retire", style = MaterialTheme.typography.labelMedium, color = SignalRedBright)
                    }
                }
            }
        }
    }
}

@Composable
private fun StationInspectorCard(
    station: StationInfo,
    trains: List<Train>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inbound = trains.filter { station.id in it.stopStationIds && station.id !in it.completedStationIds }
    Surface(
        color = ForestGreenDeep.copy(alpha = 0.96f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, BrassGold),
        modifier = modifier.widthIn(min = 260.dp, max = 310.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${station.code} • ${station.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = BrassGoldBright
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close station card", tint = WarmIvoryMuted, modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(color = BrassGoldDark.copy(alpha = 0.45f))
            InspectorRow("Platforms", "${station.platformCount} Active")
            if (inbound.isEmpty()) {
                Text(
                    text = "No inbound scheduled stops.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WarmIvoryMuted
                )
            } else {
                Text("Inbound Services:", style = MaterialTheme.typography.labelMedium, color = WarmIvoryMuted)
                inbound.take(3).forEach { t ->
                    Text(
                        text = "• ${t.serviceNumber} (${t.category.shortCode}) — ${t.statusReason}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarmIvoryPaper
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectorRow(label: String, value: String, highlightError: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = WarmIvoryMuted)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlightError) SignalRedBright else WarmIvoryPaper,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SessionResultsModal(
    title: String,
    mode: GameMode,
    report: SessionScoreBreakdown,
    delivered: Int,
    bestStreak: Int,
    isNewBest: Boolean,
    onReplay: () -> Unit,
    onNextChallenge: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB30F1512)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = WarmIvoryPaper,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(3.dp, BrassGoldDark),
            modifier = Modifier
                .widthIn(min = 380.dp, max = 540.dp)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "OPERATIONS REPORT",
                            style = MaterialTheme.typography.labelLarge,
                            color = ForestGreen
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = GraphiteDeep
                        )
                    }
                    Surface(
                        color = ForestGreen,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(2.dp, BrassGold)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = report.gradeLabel,
                                style = MaterialTheme.typography.headlineLarge,
                                color = BrassGoldBright
                            )
                            Text(
                                text = report.medal.displayName.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = WarmIvoryPaper
                            )
                        }
                    }
                }

                if (isNewBest) {
                    Surface(
                        color = BrassGold.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "★ NEW PERSONAL RECORD ★",
                            style = MaterialTheme.typography.labelLarge,
                            color = ForestGreenDeep,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = WarmSandstoneDark.copy(alpha = 0.5f))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ReportStat("FINAL SCORE", "${report.finalScore}")
                    ReportStat("DELIVERED", "$delivered")
                    ReportStat("ON-TIME", "${report.onTimePercent}%")
                    ReportStat("BEST STREAK", "${bestStreak}x")
                }

                HorizontalDivider(color = WarmSandstoneDark.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onExit,
                        border = BorderStroke(1.5.dp, Graphite),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Atlas Menu", color = GraphiteDeep)
                    }
                    OutlinedButton(
                        onClick = onReplay,
                        border = BorderStroke(1.5.dp, ForestGreen),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Replay", color = ForestGreen)
                    }
                    if (mode == GameMode.TUTORIAL || mode == GameMode.QUICK_DISPATCH) {
                        Button(
                            onClick = onNextChallenge,
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = WarmIvoryPaper),
                            modifier = Modifier.weight(1.2f).height(48.dp)
                        ) {
                            Text("Next Dispatch", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = GraphiteSlate)
        Text(text = value, style = MaterialTheme.typography.headlineMedium, color = ForestGreenDeep)
    }
}
