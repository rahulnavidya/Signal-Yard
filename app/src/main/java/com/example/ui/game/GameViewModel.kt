package com.example.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundManager
import com.example.data.AppSettings
import com.example.data.GameRecordsRepository
import com.example.data.SettingsRepository
import com.example.scenarios.DioramaMaps
import com.example.scenarios.GameMode
import com.example.scenarios.ScenarioCatalog
import com.example.scenarios.ScenarioDefinition
import com.example.scenarios.TutorialHint
import com.example.sim.*
import com.example.ui.canvas.CameraState
import com.example.ui.canvas.DioramaTapTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class GameSessionUiState(
    val title: String = "",
    val subtitle: String = "",
    val mode: GameMode = GameMode.QUICK_DISPATCH,
    val graph: TrackGraph? = null,
    val snapshot: SimulationSnapshot? = null,
    val camera: CameraState = CameraState(),
    val selectedTrainId: String? = null,
    val selectedStationId: String? = null,
    val showPauseMenu: Boolean = false,
    val showZenSpawner: Boolean = false,
    val feedbackBanner: String? = null,
    val rushHourDurationSeconds: Float = 300f,
    val targetDeliveryCount: Int = 0,
    val tutorialHint: TutorialHint? = null,
    val scoreBreakdown: SessionScoreBreakdown? = null,
    val isNewPersonalBest: Boolean = false,
    val settings: AppSettings = AppSettings()
)

class GameViewModel(
    private val recordsRepository: GameRecordsRepository,
    private val settingsRepository: SettingsRepository,
    private val soundManager: SoundManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameSessionUiState())
    val uiState: StateFlow<GameSessionUiState> = _uiState.asStateFlow()

    private var simulation: RailwaySimulation? = null
    private var activeScenario: ScenarioDefinition? = null
    private var loopJob: Job? = null
    private var feedbackClearJob: Job? = null
    private var sessionFinalized: Boolean = false
    private var hasInspectedTrainInTutorial: Boolean = false

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                soundManager.enabled = settings.soundEnabled
                soundManager.volume = settings.masterVolume
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun startScenario(scenarioId: String) {
        val scenario = ScenarioCatalog.findById(scenarioId) ?: ScenarioCatalog.tutorialScenario
        activeScenario = scenario
        sessionFinalized = false
        hasInspectedTrainInTutorial = false

        val graph = scenario.graphFactory()
        val initialSwitches = graph.switches.toMutableMap()
        scenario.initialSwitchOverrides.forEach { (swId, idx) ->
            initialSwitches[swId]?.let { sw ->
                initialSwitches[swId] = sw.copy(activeBranchIndex = idx, animProgress = idx.toFloat())
            }
        }

        val sim = createSimulation(graph, scenario.spawns, initialSwitches)
        val defaultSpeed = _uiState.value.settings.defaultSpeedMultiplier
        sim.setSpeedMultiplier(defaultSpeed)
        simulation = sim

        _uiState.update {
            GameSessionUiState(
                title = scenario.title,
                subtitle = scenario.objectiveSummary,
                mode = scenario.mode,
                graph = graph,
                snapshot = sim.getSnapshot(),
                camera = CameraState(),
                targetDeliveryCount = scenario.targetDeliveryCount,
                tutorialHint = if (scenario.mode == GameMode.TUTORIAL) computeTutorialHint(sim.getSnapshot()) else null,
                settings = it.settings
            )
        }
        startSimulationLoop()
    }

    fun startRushHour(durationMinutes: Int) {
        activeScenario = null
        sessionFinalized = false
        val graph = DioramaMaps.alderfordNetwork()
        val schedule = ScenarioCatalog.generateRushHourSchedule(durationMinutes)
        val sim = createSimulation(graph, schedule, graph.switches)
        sim.setSpeedMultiplier(_uiState.value.settings.defaultSpeedMultiplier)
        simulation = sim

        _uiState.update {
            GameSessionUiState(
                title = "Rush Hour (${durationMinutes}m)",
                subtitle = "Keep Alderford Central moving safely under escalating traffic.",
                mode = GameMode.RUSH_HOUR,
                graph = graph,
                snapshot = sim.getSnapshot(),
                camera = CameraState(),
                rushHourDurationSeconds = durationMinutes * 60f,
                targetDeliveryCount = schedule.size,
                settings = it.settings
            )
        }
        startSimulationLoop()
    }

    fun startZenDispatch(useLargeNetwork: Boolean = true) {
        activeScenario = null
        sessionFinalized = false
        val graph = if (useLargeNetwork) DioramaMaps.alderfordNetwork() else DioramaMaps.valleyJunction()
        val initialSpawns = if (useLargeNetwork) {
            listOf(
                ScheduledTrainSpawn(
                    id = "zen_init_1",
                    serviceNumber = "LOC-101",
                    name = "Alderford Valley Local",
                    category = TrainCategory.LOCAL_PASSENGER,
                    entrySegmentId = "wm_jw",
                    entryDir = TraversalDir.A_TO_B,
                    stopStationIds = listOf("STN_ALDERFORD"),
                    destinationNodeId = "SE_OUT",
                    spawnAtSeconds = 0f,
                    targetTravelSeconds = 65f
                ),
                ScheduledTrainSpawn(
                    id = "zen_init_2",
                    serviceNumber = "EXP-202",
                    name = "Harbor Express",
                    category = TrainCategory.EXPRESS_PASSENGER,
                    entrySegmentId = "je_seout",
                    entryDir = TraversalDir.B_TO_A,
                    stopStationIds = listOf("STN_ALDERFORD"),
                    destinationNodeId = "W_MAIN_IN",
                    spawnAtSeconds = 3f,
                    targetTravelSeconds = 60f
                )
            )
        } else {
            listOf(
                ScheduledTrainSpawn(
                    id = "zen_init_v1",
                    serviceNumber = "LOC-101",
                    name = "Countryside Local",
                    category = TrainCategory.LOCAL_PASSENGER,
                    entrySegmentId = "w_in_app",
                    entryDir = TraversalDir.A_TO_B,
                    stopStationIds = listOf("STN_VALLEY"),
                    destinationNodeId = "E_OUT",
                    spawnAtSeconds = 0f,
                    targetTravelSeconds = 60f
                )
            )
        }

        val sim = createSimulation(graph, initialSpawns, graph.switches)
        sim.setSpeedMultiplier(_uiState.value.settings.defaultSpeedMultiplier)
        simulation = sim

        _uiState.update {
            GameSessionUiState(
                title = "Zen Dispatch • ${graph.name}",
                subtitle = "Free-play miniature railway operations. Spawn trains & experiment freely.",
                mode = GameMode.ZEN_DISPATCH,
                graph = graph,
                snapshot = sim.getSnapshot(),
                camera = CameraState(),
                showZenSpawner = true,
                settings = it.settings
            )
        }
        startSimulationLoop()
    }

    private fun createSimulation(
        graph: TrackGraph,
        spawns: List<ScheduledTrainSpawn>,
        switches: Map<String, TrackSwitch>
    ): RailwaySimulation {
        return RailwaySimulation(
            graph = graph,
            initialSpawns = spawns,
            initialSwitches = switches,
            onEvent = { event ->
                when (event) {
                    is SimulationEvent.SwitchToggled -> {
                        soundManager.playSwitchToggle()
                        postFeedback("Switch ${event.label} aligned")
                    }
                    is SimulationEvent.SignalHeldToggled -> {
                        soundManager.playSignalRelay()
                        postFeedback(if (event.heldRed) "Signal held at RED" else "Signal cleared to Auto")
                    }
                    is SimulationEvent.TrainSpawned -> {
                        if (event.category == TrainCategory.EXPRESS_PASSENGER) {
                            soundManager.playExpressHorn()
                        }
                    }
                    is SimulationEvent.TrainArrivedAtStation -> {
                        soundManager.playStationBell()
                    }
                    is SimulationEvent.TrainDelivered -> {
                        soundManager.playStationBell()
                        val tag = if (event.onTime) "On Time" else "Delayed"
                        postFeedback("+${event.points} pts • ${event.serviceNumber} Delivered ($tag)")
                    }
                    is SimulationEvent.ActionRejected -> {
                        soundManager.playActionRejected()
                        postFeedback(event.reason)
                    }
                }
            }
        )
    }

    private fun startSimulationLoop() {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            var lastNanos = System.nanoTime()
            while (isActive) {
                delay(16L)
                val now = System.nanoTime()
                val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNanos = now

                val sim = simulation ?: continue
                if (!_uiState.value.showPauseMenu && _uiState.value.scoreBreakdown == null) {
                    sim.advanceFrame(dt)
                    val snap = sim.getSnapshot()
                    _uiState.update { state ->
                        state.copy(
                            snapshot = snap,
                            tutorialHint = if (state.mode == GameMode.TUTORIAL) computeTutorialHint(snap) else null
                        )
                    }
                    checkSessionCompletion(snap)
                }
            }
        }
    }

    private fun checkSessionCompletion(snap: SimulationSnapshot) {
        if (sessionFinalized) return
        val state = _uiState.value

        when (state.mode) {
            GameMode.TUTORIAL, GameMode.QUICK_DISPATCH -> {
                val scenario = activeScenario ?: return
                if (snap.trains.isEmpty() && snap.pendingSpawns.isEmpty() && snap.deliveredCount >= scenario.targetDeliveryCount) {
                    finalizeScenarioSession(scenario, snap)
                }
            }
            GameMode.RUSH_HOUR -> {
                if (snap.simTimeSeconds >= state.rushHourDurationSeconds) {
                    finalizeRushHourSession(snap)
                }
            }
            GameMode.ZEN_DISPATCH -> Unit
        }
    }

    private fun finalizeScenarioSession(scenario: ScenarioDefinition, snap: SimulationSnapshot) {
        sessionFinalized = true
        val breakdown = ScoreManager.calculateSessionResult(
            deliveredCount = snap.deliveredCount,
            onTimeDeliveredCount = snap.onTimeDeliveredCount,
            bestStreak = snap.bestStreak,
            safetyIncidents = snap.safetyIncidents,
            totalDelaySeconds = snap.totalDelaySeconds,
            goldTargetScore = scenario.goldScore,
            silverTargetScore = scenario.silverScore,
            bronzeTargetScore = scenario.bronzeScore,
            allRequiredDelivered = snap.deliveredCount >= scenario.targetDeliveryCount
        )
        viewModelScope.launch {
            val isNewBest = recordsRepository.recordScenarioCompletion(
                scenarioId = scenario.id,
                mode = scenario.mode,
                mapName = simulation?.graph?.name ?: scenario.title,
                breakdown = breakdown,
                trainsDelivered = snap.deliveredCount,
                bestStreak = snap.bestStreak,
                safetyIncidents = snap.safetyIncidents,
                durationSeconds = snap.simTimeSeconds.toInt()
            )
            _uiState.update {
                it.copy(scoreBreakdown = breakdown, isNewPersonalBest = isNewBest)
            }
        }
    }

    fun finishRushHourEarly() {
        val snap = simulation?.getSnapshot() ?: return
        if (!sessionFinalized && _uiState.value.mode == GameMode.RUSH_HOUR) {
            finalizeRushHourSession(snap)
        }
    }

    private fun finalizeRushHourSession(snap: SimulationSnapshot) {
        sessionFinalized = true
        val breakdown = ScoreManager.calculateSessionResult(
            deliveredCount = snap.deliveredCount,
            onTimeDeliveredCount = snap.onTimeDeliveredCount,
            bestStreak = snap.bestStreak,
            safetyIncidents = snap.safetyIncidents,
            totalDelaySeconds = snap.totalDelaySeconds,
            goldTargetScore = 2400,
            silverTargetScore = 1500,
            bronzeTargetScore = 700,
            allRequiredDelivered = snap.deliveredCount > 0
        )
        viewModelScope.launch {
            val isNewBest = recordsRepository.recordCasualSession(
                mode = GameMode.RUSH_HOUR,
                mapName = simulation?.graph?.name ?: "Alderford Central",
                score = breakdown.finalScore,
                trainsDelivered = snap.deliveredCount,
                onTimePercent = breakdown.onTimePercent,
                bestStreak = snap.bestStreak,
                safetyIncidents = snap.safetyIncidents,
                durationSeconds = snap.simTimeSeconds.toInt()
            )
            _uiState.update {
                it.copy(scoreBreakdown = breakdown, isNewPersonalBest = isNewBest)
            }
        }
    }

    private fun computeTutorialHint(snap: SimulationSnapshot): TutorialHint {
        val sw1 = snap.switches["SW_1"]
        val sw2 = snap.switches["SW_2"]
        val hasWaiting = snap.trains.any { it.status == TrainStatus.WAITING_SIGNAL }
        return when {
            snap.deliveredCount >= 1 -> TutorialHint(
                stepNumber = 4,
                title = "Clear the Corridor",
                instruction = "Great job! Align the remaining exit switch so both trains clear Valley Central to their portals."
            )
            sw1?.activeBranchIndex == sw2?.activeBranchIndex -> TutorialHint(
                stepNumber = 1,
                title = "Route Opposing Platforms",
                instruction = "Tap SW-1 or SW-2 on the track so Westbound & Eastbound trains take separate platforms (P1 & P2)."
            )
            !hasInspectedTrainInTutorial -> TutorialHint(
                stepNumber = 2,
                title = "Inspect a Train",
                instruction = "Tap any moving train to inspect its timetable, speed, and green intended route ribbon."
            )
            hasWaiting -> TutorialHint(
                stepNumber = 3,
                title = "Clear Held Signal",
                instruction = "A train is waiting at a red signal. Check the switch ahead or tap a held signal to release it."
            )
            else -> TutorialHint(
                stepNumber = 3,
                title = "Signal Interlocking Active",
                instruction = "Signals protect occupied blocks automatically. Tap any signal post to Hold or Release."
            )
        }
    }

    fun onTapTarget(target: DioramaTapTarget) {
        val sim = simulation ?: return
        when (target) {
            is DioramaTapTarget.Switch -> {
                sim.toggleSwitch(target.switchId)
                _uiState.update { it.copy(snapshot = sim.getSnapshot()) }
            }
            is DioramaTapTarget.Signal -> {
                sim.toggleSignalManualHold(target.signalId)
                _uiState.update { it.copy(snapshot = sim.getSnapshot()) }
            }
            is DioramaTapTarget.Train -> {
                hasInspectedTrainInTutorial = true
                _uiState.update {
                    it.copy(
                        selectedTrainId = target.trainId,
                        selectedStationId = null
                    )
                }
            }
            is DioramaTapTarget.Station -> {
                _uiState.update {
                    it.copy(
                        selectedStationId = target.stationId,
                        selectedTrainId = null
                    )
                }
            }
            DioramaTapTarget.Empty -> {
                _uiState.update {
                    it.copy(selectedTrainId = null, selectedStationId = null)
                }
            }
        }
    }

    fun updateCamera(camera: CameraState) {
        _uiState.update { it.copy(camera = camera) }
    }

    fun resetCamera() {
        _uiState.update { it.copy(camera = CameraState()) }
    }

    fun focusOnWorldPoint(wx: Float, wy: Float) {
        val graph = _uiState.value.graph ?: return
        val dx = (graph.worldWidth * 0.5f - wx) * 0.8f
        val dy = (graph.worldHeight * 0.5f - wy) * 0.8f
        _uiState.update {
            it.copy(camera = CameraState(panX = dx, panY = dy, zoom = 1.25f))
        }
    }

    fun togglePause() {
        val sim = simulation ?: return
        val nextPaused = !sim.isPaused
        sim.setPaused(nextPaused)
        _uiState.update { it.copy(snapshot = sim.getSnapshot()) }
    }

    fun setSpeedMultiplier(multiplier: Float) {
        val sim = simulation ?: return
        sim.setPaused(false)
        sim.setSpeedMultiplier(multiplier)
        _uiState.update { it.copy(snapshot = sim.getSnapshot()) }
    }

    fun setShowPauseMenu(show: Boolean) {
        val sim = simulation
        if (sim != null) {
            sim.setPaused(show)
        }
        _uiState.update {
            it.copy(showPauseMenu = show, snapshot = sim?.getSnapshot())
        }
    }

    fun toggleSoundMute() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(soundEnabled = !it.soundEnabled) }
        }
    }

    fun toggleZenSpawner() {
        _uiState.update { it.copy(showZenSpawner = !it.showZenSpawner) }
    }

    fun spawnZenTrain(category: TrainCategory, fromEast: Boolean) {
        val sim = simulation ?: return
        val g = sim.graph
        val spawned = if (g.id == "alderford_network") {
            if (!fromEast) {
                val entrySeg = if (category == TrainCategory.FREIGHT || category == TrainCategory.HEAVY_FREIGHT) "wt_jw" else "wm_jw"
                val stops = if (category == TrainCategory.LOCAL_PASSENGER || category == TrainCategory.EXPRESS_PASSENGER) {
                    listOf("STN_ALDERFORD")
                } else emptyList()
                sim.spawnNow(category, entrySeg, TraversalDir.A_TO_B, stops, "SE_OUT")
            } else {
                val stops = if (category == TrainCategory.LOCAL_PASSENGER) listOf("STN_ALDERFORD") else emptyList()
                sim.spawnNow(category, "je_seout", TraversalDir.B_TO_A, stops, "W_MAIN_IN")
            }
        } else {
            if (!fromEast) {
                sim.spawnNow(category, "w_in_app", TraversalDir.A_TO_B, listOf("STN_VALLEY"), "E_OUT")
            } else {
                sim.spawnNow(category, "ebr_eout", TraversalDir.B_TO_A, listOf("STN_VALLEY"), "W_IN")
            }
        }
        if (spawned) {
            _uiState.update { it.copy(snapshot = sim.getSnapshot()) }
        } else {
            postFeedback("Entry portal occupied — clear track before dispatching")
        }
    }

    fun removeSelectedTrain() {
        val id = _uiState.value.selectedTrainId ?: return
        simulation?.removeTrain(id)
        _uiState.update {
            it.copy(selectedTrainId = null, snapshot = simulation?.getSnapshot())
        }
    }

    fun restartSession() {
        val state = _uiState.value
        when (state.mode) {
            GameMode.TUTORIAL -> startScenario(ScenarioCatalog.tutorialScenario.id)
            GameMode.QUICK_DISPATCH -> activeScenario?.let { startScenario(it.id) }
            GameMode.RUSH_HOUR -> startRushHour((state.rushHourDurationSeconds / 60f).toInt().coerceAtLeast(5))
            GameMode.ZEN_DISPATCH -> startZenDispatch(state.graph?.id == "alderford_network")
        }
    }

    private fun postFeedback(msg: String) {
        feedbackClearJob?.cancel()
        _uiState.update { it.copy(feedbackBanner = msg) }
        feedbackClearJob = viewModelScope.launch {
            delay(2800L)
            _uiState.update { it.copy(feedbackBanner = null) }
        }
    }

    override fun onCleared() {
        loopJob?.cancel()
        super.onCleared()
    }

    class Factory(
        private val recordsRepository: GameRecordsRepository,
        private val settingsRepository: SettingsRepository,
        private val soundManager: SoundManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameViewModel(recordsRepository, settingsRepository, soundManager) as T
        }
    }
}
