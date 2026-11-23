package com.example

import com.example.scenarios.DioramaMaps
import com.example.scenarios.ScenarioCatalog
import com.example.sim.*
import org.junit.Assert.*
import org.junit.Test

class QuickDispatchScenariosTest {

    @Test
    fun `all quick dispatch challenges have valid routes for every scheduled train`() {
        val allScenarios = listOf(ScenarioCatalog.tutorialScenario) + ScenarioCatalog.quickDispatchChallenges
        allScenarios.forEach { scenario ->
            val graph = scenario.graphFactory()
            scenario.spawns.forEach { spawn ->
                val route = RoutePlanner.findRoute(
                    graph = graph,
                    startSegmentId = spawn.entrySegmentId,
                    startDir = spawn.entryDir,
                    remainingStops = spawn.stopStationIds,
                    destinationNodeId = spawn.destinationNodeId,
                    category = spawn.category
                )
                assertTrue(
                    "Scenario ${scenario.id} spawn ${spawn.serviceNumber} has no valid route!",
                    route.isNotEmpty()
                )
            }
        }
    }

    @Test
    fun `express priority scenario delivers express past siding-held local`() {
        val scenario = ScenarioCatalog.findById("qd_express_priority")!!
        val graph = scenario.graphFactory()
        // Route LOC-301 onto Timber Creek siding (SW_1 = 1, SW_2 = 0 initially)
        val switches = graph.switches.toMutableMap().apply {
            put("SW_1", getValue("SW_1").copy(activeBranchIndex = 1))
            put("SW_2", getValue("SW_2").copy(activeBranchIndex = 0))
        }
        val sim = RailwaySimulation(graph, scenario.spawns, switches)

        // Step until LOC-301 has cleared SW_1 onto the siding, then restore SW_1 to mainline (0) for EXP-105
        var switchedBack = false
        repeat(15 * 60) {
            sim.stepFixed()
            val snap = sim.getSnapshot()
            val loc = snap.trains.firstOrNull { it.serviceNumber == "LOC-301" }
            val sw1 = snap.switches.getValue("SW_1")
            if (!switchedBack && loc != null && loc.currentSegmentId !in setOf("w_in_app", "w_app_jw") && !sw1.isLocked) {
                switchedBack = sim.toggleSwitch("SW_1")
            }
        }
        assertTrue("SW_1 should be unlocked and toggled back once LOC-301 clears onto siding", switchedBack)

        // Advance 11 seconds (to t=26s) so EXP-105 sweeps past on mainline and exits E_OUT
        repeat(11 * 60) { sim.stepFixed() }
        val midSnap = sim.getSnapshot()
        assertTrue("Expected EXP-105 to be delivered via mainline", midSnap.deliveredCount >= 1)

        // Flip SW_2 to 1 to clear LOC-301 out of Timber Creek siding
        assertTrue(sim.toggleSwitch("SW_2"))
        repeat(18 * 60) { sim.stepFixed() }
        assertTrue("Expected at least 2 trains delivered", sim.getSnapshot().deliveredCount >= 2)
    }

    @Test
    fun `score manager rewards safety and penalizes incidents fairly`() {
        val safeResult = ScoreManager.calculateSessionResult(
            deliveredCount = 4,
            onTimeDeliveredCount = 4,
            bestStreak = 4,
            safetyIncidents = 0,
            totalDelaySeconds = 0f,
            goldTargetScore = 1450,
            silverTargetScore = 1150,
            bronzeTargetScore = 700
        )
        assertEquals(MedalRank.GOLD, safeResult.medal)
        assertEquals("S", safeResult.gradeLabel)

        val unsafeResult = ScoreManager.calculateSessionResult(
            deliveredCount = 4,
            onTimeDeliveredCount = 4,
            bestStreak = 4,
            safetyIncidents = 1,
            totalDelaySeconds = 0f,
            goldTargetScore = 1450,
            silverTargetScore = 1150,
            bronzeTargetScore = 700
        )
        assertNotEquals(MedalRank.GOLD, unsafeResult.medal)
        assertTrue(safeResult.finalScore > unsafeResult.finalScore)
    }

    @Test
    fun `alderford network supports zen dispatch train spawning`() {
        val graph = DioramaMaps.alderfordNetwork()
        val sim = RailwaySimulation(graph)
        val okWest = sim.spawnNow(
            category = TrainCategory.EXPRESS_PASSENGER,
            entrySegmentId = "wm_jw",
            entryDir = TraversalDir.A_TO_B,
            stopStationIds = listOf("STN_ALDERFORD"),
            destinationNodeId = "SE_OUT"
        )
        val okEast = sim.spawnNow(
            category = TrainCategory.LOCAL_PASSENGER,
            entrySegmentId = "je_seout",
            entryDir = TraversalDir.B_TO_A,
            stopStationIds = listOf("STN_ALDERFORD"),
            destinationNodeId = "W_MAIN_IN"
        )
        assertTrue(okWest)
        assertTrue(okEast)
        assertEquals(2, sim.getSnapshot().trains.size)
    }
}
