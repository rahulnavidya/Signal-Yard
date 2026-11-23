package com.example.ui.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.*

@Composable
fun MainMenuScreen(
    totalTrainsDelivered: Int,
    rushHourHighScore: Int,
    bestStreak: Int,
    onStartTutorial: () -> Unit,
    onOpenQuickDispatch: () -> Unit,
    onOpenRushHour: () -> Unit,
    onOpenZenDispatch: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ForestGreenDeep)
    ) {
        // Miniature Railway Diorama Hero Backdrop
        Image(
            painter = painterResource(id = R.drawable.img_menu_diorama),
            contentDescription = "Signal Yard miniature railway diorama",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Rich Forest & Graphite gradient overlay for crisp atlas legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xEB18251D),
                            Color(0xCC1E2F24),
                            Color(0xDC18221D)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Brass Title Plaque & Signal School CTA
            Column(
                modifier = Modifier
                    .weight(0.44f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = ForestGreen.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.5.dp, BrassGold)
                    ) {
                        Text(
                            text = "RAILWAY DISPATCHING ATLAS",
                            style = MaterialTheme.typography.labelMedium,
                            color = BrassGoldBright,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "SIGNAL YARD",
                        style = MaterialTheme.typography.displayLarge,
                        color = WarmIvoryPaper
                    )

                    Text(
                        text = "Every train has somewhere to be. Coordinate switches, clear block signals, and keep the miniature railway moving.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = WarmIvory.copy(alpha = 0.9f)
                    )
                }

                // Live Telemetry Ribbon
                Surface(
                    color = GraphiteDeep.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BrassGoldDark)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryStat("DELIVERED", "$totalTrainsDelivered")
                        TelemetryStat("RUSH BEST", "$rushHourHighScore")
                        TelemetryStat("BEST STREAK", "${bestStreak}x")
                    }
                }

                // Primary Tutorial / Vertical Slice Entry
                Button(
                    onClick = onStartTutorial,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrassGold,
                        contentColor = GraphiteDeep
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_tutorial_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PLAY SIGNAL SCHOOL (TUTORIAL)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Right Column: Mode Cards
            Column(
                modifier = Modifier
                    .weight(0.56f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AtlasModeCard(
                        title = "Quick Dispatch",
                        subtitle = "4 concise 2–5 min railway puzzles",
                        badge = "CHALLENGES",
                        icon = Icons.Filled.AltRoute,
                        accent = SignalGreen,
                        onClick = onOpenQuickDispatch,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("menu_quick_dispatch")
                    )
                    AtlasModeCard(
                        title = "Rush Hour",
                        subtitle = "5m & 10m escalating traffic ops",
                        badge = "REAL-TIME",
                        icon = Icons.Filled.Schedule,
                        accent = SignalRedBright,
                        onClick = onOpenRushHour,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("menu_rush_hour")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AtlasModeCard(
                        title = "Zen Dispatch",
                        subtitle = "Unrestricted miniature free-play",
                        badge = "RELAXED",
                        icon = Icons.Filled.Spa,
                        accent = BrassGoldBright,
                        onClick = onOpenZenDispatch,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("menu_zen_dispatch")
                    )
                    AtlasModeCard(
                        title = "Records & Log",
                        subtitle = "Medals, streaks & safety history",
                        badge = "ARCHIVE",
                        icon = Icons.Filled.EmojiEvents,
                        accent = WarmSandstoneLight,
                        onClick = onOpenRecords,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("menu_records")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = ForestGreenDeep.copy(alpha = 0.90f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, WarmSandstoneDark),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onOpenSettings)
                            .testTag("menu_settings")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = BrassGoldBright)
                            Text(
                                text = "Dispatch Settings",
                                style = MaterialTheme.typography.titleMedium,
                                color = WarmIvoryPaper
                            )
                        }
                    }

                    // Non-interactive roadmap availability plaque per Part 11
                    Surface(
                        color = GraphiteDeep.copy(alpha = 0.80f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GraphiteSlate),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Construction, contentDescription = null, tint = WarmIvoryMuted, modifier = Modifier.size(18.dp))
                            Column {
                                Text(
                                    text = "Career & Track Sandbox",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = WarmIvoryMuted
                                )
                                Text(
                                    text = "Upcoming Phases 4 & 5",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarmSandstone
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryStat(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = WarmIvoryMuted)
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = BrassGoldBright)
    }
}

@Composable
private fun AtlasModeCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ForestGreenDeep.copy(alpha = 0.93f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, BrassGoldDark),
        tonalElevation = 4.dp,
        modifier = modifier
            .defaultMinSize(minHeight = 114.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                Surface(
                    color = accent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = WarmIvoryPaper
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = WarmIvoryMuted
            )
        }
    }
}
