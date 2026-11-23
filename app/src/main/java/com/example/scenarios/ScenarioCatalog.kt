package com.example.scenarios

import com.example.sim.ScheduledTrainSpawn
import com.example.sim.TrackGraph
import com.example.sim.TrainCategory
import com.example.sim.TraversalDir
import kotlin.random.Random

enum class GameMode {
    TUTORIAL,
    QUICK_DISPATCH,
    RUSH_HOUR,
    ZEN_DISPATCH
}

data class TutorialHint(
    val stepNumber: Int,
    val title: String,
    val instruction: String
)

data class ScenarioDefinition(
    val id: String,
    val title: String,
    val subtitle: String,
    val regionName: String,
    val mode: GameMode,
    val difficultyLabel: String,
    val estimatedMinutes: Int,
    val objectiveSummary: String,
    val graphFactory: () -> TrackGraph,
    val initialSwitchOverrides: Map<String, Int> = emptyMap(),
    val spawns: List<ScheduledTrainSpawn>,
    val targetDeliveryCount: Int,
    val bronzeScore: Int,
    val silverScore: Int,
    val goldScore: Int
)

object ScenarioCatalog {

    val tutorialScenario = ScenarioDefinition(
        id = "tutorial_valley",
        title = "Signal School: Valley Junction",
        subtitle = "Learn switches, block signals, and opposing train passes",
        regionName = "Countryside Division",
        mode = GameMode.TUTORIAL,
        difficultyLabel = "Tutorial",
        estimatedMinutes = 3,
        objectiveSummary = "Safely route 2 opposing passenger trains through Valley Central Station.",
        graphFactory = { DioramaMaps.valleyJunction() },
        initialSwitchOverrides = mapOf("SW_1" to 0, "SW_2" to 1),
        spawns = listOf(
            ScheduledTrainSpawn(
                id = "tut_1",
                serviceNumber = "LOC-101",
                name = "Valley Local East",
                category = TrainCategory.LOCAL_PASSENGER,
                entrySegmentId = "w_in_app",
                entryDir = TraversalDir.A_TO_B,
                stopStationIds = listOf("STN_VALLEY"),
                destinationNodeId = "E_OUT",
                spawnAtSeconds = 0f,
                targetTravelSeconds = 55f
            ),
            ScheduledTrainSpawn(
                id = "tut_2",
                serviceNumber = "LOC-102",
                name = "Valley Local West",
                category = TrainCategory.LOCAL_PASSENGER,
                entrySegmentId = "ebr_eout",
                entryDir = TraversalDir.B_TO_A,
                stopStationIds = listOf("STN_VALLEY"),
                destinationNodeId = "W_IN",
                spawnAtSeconds = 3f,
                targetTravelSeconds = 60f
            )
        ),
        targetDeliveryCount = 2,
        bronzeScore = 300,
        silverScore = 550,
        goldScore = 750
    )

    val quickDispatchChallenges: List<ScenarioDefinition> = listOf(
        ScenarioDefinition(
            id = "qd_crossing",
            title = "Countryside Crossing",
            subtitle = "Coordinate opposing locals and a fast express across Valley Central",
            regionName = "Countryside",
            mode = GameMode.QUICK_DISPATCH,
            difficultyLabel = "Easy",
            estimatedMinutes = 3,
            objectiveSummary = "Deliver all 3 trains cleanly without holding the Express behind a local.",
            graphFactory = { DioramaMaps.valleyJunction() },
            initialSwitchOverrides = mapOf("SW_1" to 0, "SW_2" to 1),
            spawns = listOf(
                ScheduledTrainSpawn(
                    id = "qd1_t1",
                    serviceNumber = "LOC-104",
                    name = "Meadow Local",
                    category = TrainCategory.LOCAL_PASSENGER,
                    entrySegmentId = "w_in_app",
                    entryDir = TraversalDir.A_TO_B,
                    stopStationIds = listOf("STN_VALLEY"),
                    destinationNodeId = "E_OUT",
                    spawnAtSeconds = 0f,
                    targetTravelSeconds = 55f
                ),
                ScheduledTrainSpawn(
                    id = "qd1_t2",
                    serviceNumber = "LOC-107",
                    name = "Brookside Local",
                    category = TrainCategory.LOCAL_PASSENGER,
                    entrySegmentId = "ebr_eout",
                    entryDir = TraversalDir.B_TO_A,
                    stopStationIds = listOf("STN_VALLEY"),
                    destinationNodeId = "W_IN",
                    spawnAtSeconds = 2f,
                    targetTravelSeconds = 62f
                ),
                ScheduledTrainSpawn(
                    id = "qd1_t3",
                    serviceNumber = "EXP-201",
                    name = "Midday Express",
                    category = TrainCategory.EXPRESS_PASSENGER,
                    entrySegmentId = "w_in_app",
                    entryDir = TraversalDir.A_TO_B,
                    stopStationIds = emptyList(),
                    destinationNodeId = "E_OUT",
                    spawnAtSeconds = 12f,
                    targetTravelSeconds = 42f
                )
            ),
            targetDeliveryCount = 3,
            bronzeScore = 500,
            silverScore = 850,
            goldScore = 1100
        ),
        ScenarioDefinition(
            id = "qd_express_priority",
            title = "Express Priority",
            subtitle = "Loop a slow local onto Timber Creek Siding so the Express sweeps past",
            regionName = "Forest Line",
            mode = GameMode.QUICK_DISPATCH,
            difficultyLabel = "Moderate",
            estimatedMinutes = 3,
            objectiveSummary = "Siding-hold LOC-301 for its station stop while EXP-105 clears the mainline on time.",
            graphFactory = { DioramaMaps.timberCreek() },
            initialSwitchOverrides = mapOf("SW_1" to 0, "SW_2" to 0),
            spawns = listOf(
                ScheduledTrainSpawn(
                    id = "qd2_t1",
                    serviceNumber = "LOC-301",
                    name = "Timber Stopping Service",
                    category = TrainCategory.LOCAL_PASSENGER,
                    entrySegmentId = "w_in_app",
                    entryDir = TraversalDir.A_TO_B,
                    stopStationIds = listOf("STN_TIMBER"),
                    destinationNodeId = "E_OUT",
                    spawnAtSeconds = 0f,
                    targetTravelSeconds = 62f
                ),
                ScheduledTrainSpawn(
                    id = "qd2_t2",
                    serviceNumber = "EXP-105",
                    name = "Forest Flyer Express",
                    category = TrainCategory.EXPRESS_PASSENGER,
                    entrySegmentId = "w_in_app",
                    entryDir = TraversalDir.A_TO_B,
                    stopStationIds = emptyList(),
                    destinationNodeId = "E_OUT",
                    spawnAtSeconds = 11f,
                    targetTravelSeconds = 30f
                ),
                ScheduledTrainSpawn(
                    id = "qd2_t3",
                    serviceNumber = "MNT-09",
                    name = "Track Inspection Car",
                    category = TrainCategory.MAINTENANCE,
                    entrySegmentId = "eapp_eout",
                    entryDir = TraversalDir.B_TO_A,
                    stopStationIds = listOf("STN_TIMBER"),
                    destinationNodeId = "W_IN",
                    spawnAtSeconds = 36f,
                    targetTravelSeconds = 56f
                )
            ),
            targetDeliveryCount = 3,
            bronzeScore = 550,
            silverScore = 900,
            goldScore = 1150
        ),
        ScenarioDefinition(
            id = "qd_foundry_shunt",
            title = "Foundry Yard Shunt",
            subtitle = "Keep heavy coal consists off passenger platforms while clearing commuter trains",
            regionName = "Industrial District",
            mode = GameMode.QUICK_DISPATCH,
            difficultyLabel = "Challenging",
            estimatedMinutes = 4,
            objectiveSummary = "Berth Heavy Freight HVY-401 in Foundry Bay and deliver 2 passenger & freight runs.",
            graphFactory = { DioramaMaps.foundrySiding() },
            initialSwitchOverrides = mapOf("SW_1" to 0, "SW_2" to 0, "SW_3" to 0),
            spawns = listOf(
                ScheduledTrainSpawn(
                    id = "qd3_t1",
                    serviceNumber = "HVY-401",
                    name = "Ironstone Heavy Haul",
                    category = TrainCategory.HEAVY_FREIGHT,
                    entrySegmentId = "w_in_app",
                    entryDir = TraversalDir.A_TO_B,
                    stopStationIds = emptyList(),
                    destinationNodeId = "FRT_BAY",
                    spawnAtSeconds = 0f,
                    targetTravelSeconds = 52f
                ),
                ScheduledTrainSpawn(
                    id = "qd3_t2",
                    serviceNumber = "LOC-204",
                    name = "Foundry Commuter",
                    category = TrainCategory.LOCAL_PASSENGER,
                    entrySegmentId = "je_eout",
                    entryDir = TraversalDir.B_TO_A,
                    stopStationIds = listOf("STN_FOUNDRY"),
                    destinationNodeId = "W_IN",
                    spawnAtSeconds = 3f,
                    targetTravelSeconds = 58f
                ),
                ScheduledTrainSpawn(
                    id = "qd3_t3",
                    serviceNumber = "FRT-402",
                    name = "Slag Hopper Freight",
                    category = TrainCategory.FREIGHT,
                    entrySegmentId = "w_in_app",
                    entryDir = TraversalDir.A_TO_B,
                    stopStationIds = emptyList(),
                    destinationNodeId = "E_OUT",
                    spawnAtSeconds = 14f,
                    targetTravelSeconds = 54f
                )
            ),
            targetDeliveryCount = 3,
            bronzeScore = 550,
            silverScore = 900,
            goldScore = 1150
        ),
        ScenarioDefinition(
            id = "qd_twin_viaduct",
            title = "Twin Viaduct Interlocking",
            subtitle = "Balance four converging services between North and South Gorge stations",
            regionName = "Mountain Network",
            mode = GameMode.QUICK_DISPATCH,
            difficultyLabel = "Expert",
            estimatedMinutes = 5,
            objectiveSummary = "Prevent single-track gorge standoffs and deliver all 4 mountain services.",
            graphFactory = { DioramaMaps.twinViaduct() },
            initialSwitchOverrides = mapOf("SW_1" to 0, "SW_2" to 1),
            spawns = listOf(
                ScheduledTrainSpawn(
                    id = "qd4_t1",
                    serviceNumber = "LOC-501",
                    name = "North Gorge Local",
                    category = TrainCategory.LOCAL_PASSENGER,
                    entrySegmentId = "w_in_v",
                    entryDir = TraversalDir.A_TO_B,
                    stopStationIds = listOf("STN_GORGE_N"),
                    destinationNodeId = "E_OUT",
                    spawnAtSeconds = 0f,
                    targetTravelSeconds = 60f
                ),
                ScheduledTrainSpawn(
                    id = "qd4_t2",
                    serviceNumber = "LOC-504",
                    name = "South Gorge Local",
                    category = TrainCategory.LOCAL_PASSENGER,
                    entrySegmentId = "ev_eout",
                    entryDir = TraversalDir.B_TO_A,
                    stopStationIds = listOf("STN_GORGE_S"),
                    destinationNodeId = "W_IN",
                    spawnAtSeconds = 2f,
                    targetTravelSeconds = 62f
                ),
                ScheduledTrainSpawn(
                    id = "qd4_t3",
                    serviceNumber = "FRT-503",
                    name = "Alpine Timber Freight",
                    category = TrainCategory.FREIGHT,
                    entrySegmentId = "w_in_v",
                    entryDir = TraversalDir.A_TO_B,
                    stopStationIds = emptyList(),
                    destinationNodeId = "E_OUT",
                    spawnAtSeconds = 14f,
                    targetTravelSeconds = 64f
                ),
                ScheduledTrainSpawn(
                    id = "qd4_t4",
                    serviceNumber = "EXP-502",
                    name = "Summit Express",
                    category = TrainCategory.EXPRESS_PASSENGER,
                    entrySegmentId = "ev_eout",
                    entryDir = TraversalDir.B_TO_A,
                    stopStationIds = emptyList(),
                    destinationNodeId = "W_IN",
                    spawnAtSeconds = 18f,
                    targetTravelSeconds = 48f
                )
            ),
            targetDeliveryCount = 4,
            bronzeScore = 700,
            silverScore = 1150,
            goldScore = 1450
        )
    )

    fun findById(id: String): ScenarioDefinition? {
        if (id == tutorialScenario.id) return tutorialScenario
        return quickDispatchChallenges.firstOrNull { it.id == id }
    }

    /**
     * Generates a validated, solvable Rush Hour timetable on Alderford Central Network.
     * Enforces safe minimum headways per entry portal so trains never spawn overlapping.
     */
    fun generateRushHourSchedule(
        durationMinutes: Int,
        seed: Long = System.currentTimeMillis()
    ): List<ScheduledTrainSpawn> {
        val rng = Random(seed)
        val totalDurationSec = durationMinutes * 60f
        val spawns = mutableListOf<ScheduledTrainSpawn>()

        data class CorridorTemplate(
            val entrySeg: String,
            val entryDir: TraversalDir,
            val stops: List<String>,
            val destNode: String,
            val categoryPool: List<TrainCategory>,
            val baseTravelSec: Float
        )

        val templates = listOf(
            CorridorTemplate("wm_jw", TraversalDir.A_TO_B, listOf("STN_ALDERFORD"), "SE_OUT", listOf(TrainCategory.LOCAL_PASSENGER, TrainCategory.EXPRESS_PASSENGER), 58f),
            CorridorTemplate("wm_jw", TraversalDir.A_TO_B, listOf("STN_ALDERFORD", "STN_PINEWOOD"), "NE_OUT", listOf(TrainCategory.LOCAL_PASSENGER), 68f),
            CorridorTemplate("wt_jw", TraversalDir.A_TO_B, emptyList(), "SE_OUT", listOf(TrainCategory.FREIGHT, TrainCategory.HEAVY_FREIGHT), 66f),
            CorridorTemplate("je_seout", TraversalDir.B_TO_A, listOf("STN_ALDERFORD"), "W_MAIN_IN", listOf(TrainCategory.LOCAL_PASSENGER, TrainCategory.EXPRESS_PASSENGER), 60f),
            CorridorTemplate("pnw_neout", TraversalDir.B_TO_A, listOf("STN_PINEWOOD", "STN_ALDERFORD"), "W_MAIN_IN", listOf(TrainCategory.LOCAL_PASSENGER), 72f),
            CorridorTemplate("je_seout", TraversalDir.B_TO_A, emptyList(), "W_TUNNEL_IN", listOf(TrainCategory.FREIGHT), 64f)
        )

        val lastSpawnPerEntry = mutableMapOf<String, Float>()
        var currentT = 1.5f
        var trainCounter = 101

        while (currentT < totalDurationSec - 28f) {
            val progress = currentT / totalDurationSec
            val baseInterval = when {
                progress < 0.30f -> 18f
                progress < 0.65f -> 14f
                else -> 11f
            }

            // Pick a template whose entry portal has sufficient headway (>= 12s)
            val eligible = templates.filter { tpl ->
                val last = lastSpawnPerEntry[tpl.entrySeg] ?: -99f
                (currentT - last) >= 12f
            }
            if (eligible.isNotEmpty()) {
                val chosen = eligible[rng.nextInt(eligible.size)]
                val category = chosen.categoryPool[rng.nextInt(chosen.categoryPool.size)]
                val serviceCode = "${category.shortCode}-${trainCounter++}"
                spawns += ScheduledTrainSpawn(
                    id = "rush_${trainCounter}",
                    serviceNumber = serviceCode,
                    name = "${category.displayName} $serviceCode",
                    category = category,
                    entrySegmentId = chosen.entrySeg,
                    entryDir = chosen.entryDir,
                    stopStationIds = chosen.stops,
                    destinationNodeId = chosen.destNode,
                    spawnAtSeconds = currentT,
                    targetTravelSeconds = chosen.baseTravelSec
                )
                lastSpawnPerEntry[chosen.entrySeg] = currentT
            }

            currentT += baseInterval + rng.nextFloat() * 4f
        }
        return spawns
    }
}
