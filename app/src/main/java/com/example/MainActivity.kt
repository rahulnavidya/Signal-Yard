package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.audio.SoundManager
import com.example.data.GameRecordsRepository
import com.example.data.SettingsRepository
import com.example.data.SignalYardDatabase
import com.example.scenarios.ScenarioCatalog
import com.example.ui.game.GameScreen
import com.example.ui.game.GameViewModel
import com.example.ui.menu.*
import com.example.ui.theme.SignalYardTheme
import kotlinx.coroutines.launch

object SignalYardRoutes {
    const val ROUTE_MENU = "menu"
    const val ROUTE_QUICK_DISPATCH = "quick_dispatch"
    const val ROUTE_RUSH_HOUR = "rush_hour"
    const val ROUTE_ZEN = "zen"
    const val ROUTE_RECORDS = "records"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_GAME = "game"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = SignalYardDatabase.getInstance(applicationContext)
        val recordsRepository = GameRecordsRepository(db.signalYardDao())
        val settingsRepository = SettingsRepository(applicationContext)
        val soundManager = SoundManager()

        setContent {
            SignalYardTheme(darkTheme = true) {
                SignalYardApp(
                    recordsRepository = recordsRepository,
                    settingsRepository = settingsRepository,
                    soundManager = soundManager
                )
            }
        }
    }
}

@Composable
fun SignalYardApp(
    recordsRepository: GameRecordsRepository,
    settingsRepository: SettingsRepository,
    soundManager: SoundManager
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    val gameViewModel: GameViewModel = viewModel(
        factory = remember(recordsRepository, settingsRepository, soundManager) {
            GameViewModel.Factory(recordsRepository, settingsRepository, soundManager)
        }
    )

    val scenarioRecords by recordsRepository.scenarioRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    val recentSessions by recordsRepository.recentSessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val totalDelivered by recordsRepository.totalTrainsDelivered.collectAsStateWithLifecycle(initialValue = 0)
    val bestStreak by recordsRepository.bestEverStreak.collectAsStateWithLifecycle(initialValue = 0)
    val rushHighScore by recordsRepository.rushHourHighScore.collectAsStateWithLifecycle(initialValue = 0)
    val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = com.example.data.AppSettings())

    NavHost(
        navController = navController,
        startDestination = SignalYardRoutes.ROUTE_MENU
    ) {
        composable(SignalYardRoutes.ROUTE_MENU) {
            MainMenuScreen(
                totalTrainsDelivered = totalDelivered,
                rushHourHighScore = rushHighScore,
                bestStreak = bestStreak,
                onStartTutorial = {
                    gameViewModel.startScenario(ScenarioCatalog.tutorialScenario.id)
                    navController.navigate(SignalYardRoutes.ROUTE_GAME)
                },
                onOpenQuickDispatch = { navController.navigate(SignalYardRoutes.ROUTE_QUICK_DISPATCH) },
                onOpenRushHour = { navController.navigate(SignalYardRoutes.ROUTE_RUSH_HOUR) },
                onOpenZenDispatch = { navController.navigate(SignalYardRoutes.ROUTE_ZEN) },
                onOpenRecords = { navController.navigate(SignalYardRoutes.ROUTE_RECORDS) },
                onOpenSettings = { navController.navigate(SignalYardRoutes.ROUTE_SETTINGS) }
            )
        }

        composable(SignalYardRoutes.ROUTE_QUICK_DISPATCH) {
            QuickDispatchScreen(
                records = scenarioRecords.associateBy { it.scenarioId },
                onSelectScenario = { id ->
                    gameViewModel.startScenario(id)
                    navController.navigate(SignalYardRoutes.ROUTE_GAME)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(SignalYardRoutes.ROUTE_RUSH_HOUR) {
            RushHourSetupScreen(
                highScore = rushHighScore,
                onStartRushHour = { minutes ->
                    gameViewModel.startRushHour(minutes)
                    navController.navigate(SignalYardRoutes.ROUTE_GAME)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(SignalYardRoutes.ROUTE_ZEN) {
            ZenDispatchSetupScreen(
                onLaunchZen = { large ->
                    gameViewModel.startZenDispatch(large)
                    navController.navigate(SignalYardRoutes.ROUTE_GAME)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(SignalYardRoutes.ROUTE_RECORDS) {
            RecordsScreen(
                scenarioRecords = scenarioRecords,
                recentSessions = recentSessions,
                totalTrainsDelivered = totalDelivered,
                bestEverStreak = bestStreak,
                onBack = { navController.popBackStack() }
            )
        }

        composable(SignalYardRoutes.ROUTE_SETTINGS) {
            SettingsScreen(
                settings = settings,
                onUpdateSettings = { transform ->
                    coroutineScope.launch {
                        settingsRepository.updateSettings(transform)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(SignalYardRoutes.ROUTE_GAME) {
            GameScreen(
                viewModel = gameViewModel,
                onExitToMenu = {
                    gameViewModel.setShowPauseMenu(false)
                    navController.popBackStack(SignalYardRoutes.ROUTE_MENU, inclusive = false)
                },
                onLaunchNextScenario = { nextId ->
                    gameViewModel.startScenario(nextId)
                }
            )
        }
    }
}
