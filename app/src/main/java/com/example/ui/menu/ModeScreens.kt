package com.example.ui.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.data.ScenarioRecordEntity
import com.example.data.SessionRecordEntity
import com.example.scenarios.ScenarioCatalog
import com.example.sim.MedalRank
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun AtlasSubScreenShell(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GraphiteDeep)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = BrassGoldBright
                )
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.headlineLarge, color = WarmIvoryPaper)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = WarmIvoryMuted)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = BrassGoldDark.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(14.dp))
        content()
    }
}

@Composable
fun QuickDispatchScreen(
    records: Map<String, ScenarioRecordEntity>,
    onSelectScenario: (String) -> Unit,
    onBack: () -> Unit
) {
    AtlasSubScreenShell(
        title = "QUICK DISPATCH",
        subtitle = "Concise 2–5 minute railway challenges. Select any puzzle to begin immediately.",
        onBack = onBack
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(ScenarioCatalog.quickDispatchChallenges, key = { it.id }) { scenario ->
                val rec = records[scenario.id]
                val medal = MedalRank.entries.firstOrNull { it.rankOrder == (rec?.bestMedalOrdinal ?: 0) } ?: MedalRank.NONE

                Surface(
                    color = ForestGreenDeep,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, BrassGoldDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectScenario(scenario.id) }
                        .testTag("scenario_${scenario.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(color = ForestGreen, shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        text = scenario.regionName.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrassGoldBright,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(color = Graphite, shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        text = "${scenario.difficultyLabel} • ~${scenario.estimatedMinutes}m",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = WarmIvory,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(text = scenario.title, style = MaterialTheme.typography.titleLarge, color = WarmIvoryPaper)
                            Text(text = scenario.objectiveSummary, style = MaterialTheme.typography.bodyMedium, color = WarmIvoryMuted)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (rec != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = medal.displayName.uppercase(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = BrassGoldBright
                                    )
                                    Text(
                                        text = "Best ${rec.bestScore}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = WarmIvoryMuted
                                    )
                                }
                            }
                            Button(
                                onClick = { onSelectScenario(scenario.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = GraphiteDeep),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Dispatch", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RushHourSetupScreen(
    highScore: Int,
    onStartRushHour: (Int) -> Unit,
    onBack: () -> Unit
) {
    AtlasSubScreenShell(
        title = "RUSH HOUR OPERATIONS",
        subtitle = "Real-time Alderford Central Network. Escalating timetable waves with streak multipliers.",
        onBack = onBack
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RushSessionOptionCard(
                title = "Quick Rush",
                durationMinutes = 5,
                description = "5-minute brisk wave of locals, expresses, and freight transfers.",
                highScore = highScore,
                onSelect = { onStartRushHour(5) },
                modifier = Modifier.weight(1f).testTag("rush_5m_button")
            )
            RushSessionOptionCard(
                title = "Standard Rush",
                durationMinutes = 10,
                description = "10-minute full network peak with heavy consists and tight headways.",
                highScore = highScore,
                onSelect = { onStartRushHour(10) },
                modifier = Modifier.weight(1f).testTag("rush_10m_button")
            )
            RushSessionOptionCard(
                title = "Extended Rush",
                durationMinutes = 15,
                description = "15-minute endurance timetable testing sustained safety & streaks.",
                highScore = highScore,
                onSelect = { onStartRushHour(15) },
                modifier = Modifier.weight(1f).testTag("rush_15m_button")
            )
        }
    }
}

@Composable
private fun RushSessionOptionCard(
    title: String,
    durationMinutes: Int,
    description: String,
    highScore: Int,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ForestGreenDeep,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, BrassGold),
        modifier = modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "${durationMinutes} MINUTES", style = MaterialTheme.typography.labelLarge, color = SignalRedBright)
                Text(text = title, style = MaterialTheme.typography.headlineMedium, color = WarmIvoryPaper)
                Text(text = description, style = MaterialTheme.typography.bodyMedium, color = WarmIvoryMuted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Personal Best: $highScore pts",
                    style = MaterialTheme.typography.labelMedium,
                    color = BrassGoldBright
                )
                Button(
                    onClick = onSelect,
                    colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = GraphiteDeep),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Start Shift", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ZenDispatchSetupScreen(
    onLaunchZen: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    AtlasSubScreenShell(
        title = "ZEN DISPATCH",
        subtitle = "No countdowns, no score penalties. Spawn trains and enjoy the miniature diorama.",
        onBack = onBack
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                color = ForestGreenDeep,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, BrassGold),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("COUNTRYSIDE DIORAMA", style = MaterialTheme.typography.labelLarge, color = SignalGreen)
                        Text("Valley Junction", style = MaterialTheme.typography.headlineMedium, color = WarmIvoryPaper)
                        Text(
                            "Cozy two-platform station, river viaduct, and passing loop for relaxed dispatching.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WarmIvoryMuted
                        )
                    }
                    Button(
                        onClick = { onLaunchZen(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = GraphiteDeep),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("zen_valley_button")
                    ) {
                        Text("Open Valley Junction", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Surface(
                color = ForestGreenDeep,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, BrassGold),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("METROPOLITAN HUB", style = MaterialTheme.typography.labelLarge, color = BrassGoldBright)
                        Text("Alderford Central", style = MaterialTheme.typography.headlineMedium, color = WarmIvoryPaper)
                        Text(
                            "3 platforms, North tunnel, Pinewood Halt branch, and 6 interlocking switches.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WarmIvoryMuted
                        )
                    }
                    Button(
                        onClick = { onLaunchZen(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = GraphiteDeep),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("zen_alderford_button")
                    ) {
                        Text("Open Alderford Central", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RecordsScreen(
    scenarioRecords: List<ScenarioRecordEntity>,
    recentSessions: List<SessionRecordEntity>,
    totalTrainsDelivered: Int,
    bestEverStreak: Int,
    onBack: () -> Unit
) {
    AtlasSubScreenShell(
        title = "RECORDS & ATLAS LOG",
        subtitle = "Local career telemetry, challenge medals, and recent operating shifts.",
        onBack = onBack
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                modifier = Modifier.weight(0.4f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = ForestGreenDeep,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, BrassGold)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LIFETIME DISPATCH", style = MaterialTheme.typography.labelLarge, color = BrassGoldBright)
                        Text("Trains Delivered: $totalTrainsDelivered", style = MaterialTheme.typography.titleMedium, color = WarmIvoryPaper)
                        Text("Best Streak: ${bestEverStreak}x", style = MaterialTheme.typography.titleMedium, color = WarmIvoryPaper)
                        Text("Challenges Cleared: ${scenarioRecords.size}", style = MaterialTheme.typography.titleMedium, color = WarmIvoryPaper)
                    }
                }
            }

            Surface(
                color = ForestGreenDeep,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, WarmSandstoneDark),
                modifier = Modifier.weight(0.6f).fillMaxHeight()
            ) {
                if (recentSessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Train, contentDescription = null, tint = BrassGoldDark, modifier = Modifier.size(36.dp))
                            Text("No completed shifts recorded yet.", style = MaterialTheme.typography.titleMedium, color = WarmIvoryMuted)
                            Text("Complete a Tutorial, Quick Dispatch, or Rush Hour shift!", style = MaterialTheme.typography.bodyMedium, color = WarmSandstone)
                        }
                    }
                } else {
                    val fmt = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    LazyColumn(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentSessions, key = { it.id }) { session ->
                            Surface(
                                color = GraphiteDeep,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, GraphiteSlate)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("${session.modeName} • ${session.mapName}", style = MaterialTheme.typography.titleMedium, color = WarmIvoryPaper)
                                        Text(
                                            "${session.trainsDelivered} trains • ${session.onTimePercent}% on time • ${fmt.format(Date(session.timestampMillis))}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = WarmIvoryMuted
                                        )
                                    }
                                    Text("${session.score} pts", style = MaterialTheme.typography.labelLarge, color = BrassGoldBright)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit
) {
    AtlasSubScreenShell(
        title = "DISPATCH SETTINGS",
        subtitle = "Audio, accessibility symbols, route guidance, and simulation pacing.",
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingToggleRow(
                title = "Railway Sound Effects",
                subtitle = "Synthesized mechanical switches, signal relays, station bells, and express horns",
                checked = settings.soundEnabled,
                onCheckedChange = { enabled -> onUpdateSettings { it.copy(soundEnabled = enabled) } }
            )
            Surface(
                color = ForestGreenDeep,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, WarmSandstoneDark)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(
                        text = "Master Audio Volume (${(settings.masterVolume * 100).toInt()}%)",
                        style = MaterialTheme.typography.titleMedium,
                        color = WarmIvoryPaper
                    )
                    Slider(
                        value = settings.masterVolume,
                        onValueChange = { vol -> onUpdateSettings { it.copy(masterVolume = vol) } },
                        colors = SliderDefaults.colors(thumbColor = BrassGold, activeTrackColor = BrassGold)
                    )
                }
            }
            SettingToggleRow(
                title = "Colorblind Signal Symbols",
                subtitle = "Display geometric shapes (● Clear, ▲ Caution, ■ Stop) inside signal lenses",
                checked = settings.colorblindSymbols,
                onCheckedChange = { enabled -> onUpdateSettings { it.copy(colorblindSymbols = enabled) } }
            )
            SettingToggleRow(
                title = "Intended Route Ribbons",
                subtitle = "Highlight a selected train's planned path to its next station or portal",
                checked = settings.showRouteGuidance,
                onCheckedChange = { enabled -> onUpdateSettings { it.copy(showRouteGuidance = enabled) } }
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = ForestGreenDeep,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, WarmSandstoneDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = WarmIvoryPaper)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = WarmIvoryMuted)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = GraphiteDeep, checkedTrackColor = BrassGold)
            )
        }
    }
}
