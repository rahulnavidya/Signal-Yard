package com.example

import com.example.scenarios.DioramaMaps
import com.example.scenarios.ScenarioCatalog
import com.example.sim.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class RailwaySimulationTest {

    @Test
    fun `all diorama maps pass structural graph validation`() {
        val maps = listOf(
            DioramaMaps.valleyJunction(),
            DioramaMaps.timberCreek(),
            DioramaMaps.foundrySiding(),
            DioramaMaps.twinViaduct(),
            DioramaMaps.alderfordNetwork()
        )
        maps.forEach { map ->
            val errors = map.validate()
            assertTrue("Map ${map.name} had validation errors: $errors", errors.isEmpty())
        }
    }

    @Test
    fun `route planner finds valid route and rejects restricted heavy freight passenger platform`() {
        val foundry = DioramaMaps.foundrySiding()

        // Valid route for Heavy Freight into Freight Bay
        val validHeavyRoute = RoutePlanner.findRoute(
            graph = foundry,
            startSegmentId = "w_in_app",
            startDir = TraversalDir.A_TO_B,
            remainingStops = emptyList(),
            destinationNodeId = "FRT_BAY",
            category = TrainCategory.HEAVY_FREIGHT
        )
        assertTrue(validHeavyRoute.isNotEmpty())
        assertFalse(validHeavyRoute.contains("p1_w_stn"))

        // Invalid route: Heavy Freight attempting to stop at restricted Passenger Platform
        val invalidHeavyRoute = RoutePlanner.findRoute(
            graph = foundry,
            startSegmentId = "w_in_app",
            startDir = TraversalDir.A_TO_B,
            remainingStops = listOf("STN_FOUNDRY"),
            destinationNodeId = "E_OUT",
            category = TrainCategory.HEAVY_FREIGHT
        )
        assertTrue(invalidHeavyRoute.isEmpty())
    }

    @Test
    fun `red signal prevents train entry and train stops safely before junction`() {
        val graph = DioramaMaps.valleyJunction()
        val spawn = ScheduledTrainSpawn(
            id = "t1",
            serviceNumber = "LOC-101",
            name = "Test Local",
            category = TrainCategory.LOCAL_PASSENGER,
            entrySegmentId = "w_in_app",
            entryDir = TraversalDir.A_TO_B,
            stopStationIds = listOf("STN_VALLEY"),
            destinationNodeId = "E_OUT",
            spawnAtSeconds = 0f,
            targetTravelSeconds = 50f
        )
        val sim = RailwaySimulation(graph, listOf(spawn))

        // Hold W-1 signal manually at RED
        assertTrue(sim.toggleSignalManualHold("SIG_W"))

        // Advance 15 seconds of simulated time
        repeat(15 * 60) { sim.stepFixed() }
        val snap = sim.getSnapshot()

        val train = snap.trains.single()
        assertEquals(TrainStatus.WAITING_SIGNAL, train.status)
        assertEquals(0f, train.speed, 0.5f)
        // Must remain on approach segment before J_WEST
        assertTrue(train.currentSegmentId == "w_app_jw" || train.currentSegmentId == "w_in_app")
    }

    @Test
    fun `opposing train on single track causes opposing signal to display red`() {
        val graph = DioramaMaps.valleyJunction()
        val eastbound = ScheduledTrainSpawn(
            id = "t_eb",
            serviceNumber = "LOC-1",
            name = "Eastbound",
            category = TrainCategory.LOCAL_PASSENGER,
            entrySegmentId = "w_in_app",
            entryDir = TraversalDir.A_TO_B,
            stopStationIds = listOf("STN_VALLEY"),
            destinationNodeId = "E_OUT",
            spawnAtSeconds = 0f,
            targetTravelSeconds = 50f
        )
        val westbound = ScheduledTrainSpawn(
            id = "t_wb",
            serviceNumber = "LOC-2",
            name = "Westbound",
            category = TrainCategory.LOCAL_PASSENGER,
            entrySegmentId = "ebr_eout",
            entryDir = TraversalDir.B_TO_A,
            stopStationIds = listOf("STN_VALLEY"),
            destinationNodeId = "W_IN",
            spawnAtSeconds = 0f,
            targetTravelSeconds = 50f
        )
        // Both switches initially aligned to Platform 1 -> opposing corridor conflict!
        val switches = graph.switches.toMutableMap().apply {
            put("SW_1", getValue("SW_1").copy(activeBranchIndex = 0))
            put("SW_2", getValue("SW_2").copy(activeBranchIndex = 0))
        }
        val sim = RailwaySimulation(graph, listOf(eastbound, westbound), switches)
        repeat(20 * 60) { sim.stepFixed() }

        val snap = sim.getSnapshot()
        // At least one train must be safely held rather than colliding head-on
        assertEquals(2, snap.trains.size)
        assertTrue(snap.trains.any { it.status == TrainStatus.WAITING_SIGNAL })
    }

    @Test
    fun `long heavy freight occupies multiple segments and carriages follow curves`() {
        val graph = DioramaMaps.foundrySiding()
        val heavy = ScheduledTrainSpawn(
            id = "hvy_1",
            serviceNumber = "HVY-99",
            name = "Heavy Coal",
            category = TrainCategory.HEAVY_FREIGHT,
            entrySegmentId = "w_in_app",
            entryDir = TraversalDir.A_TO_B,
            stopStationIds = emptyList(),
            destinationNodeId = "FRT_BAY",
            spawnAtSeconds = 0f,
            targetTravelSeconds = 60f
        )
        val switches = graph.switches.toMutableMap().apply {
            put("SW_1", getValue("SW_1").copy(activeBranchIndex = 1)) // Route onto freight line
            put("SW_3", getValue("SW_3").copy(activeBranchIndex = 1)) // Route into FRT_BAY
        }
        val sim = RailwaySimulation(graph, listOf(heavy), switches)

        var multiSegObserved = false
        repeat(15 * 60) {
            sim.stepFixed()
            val snap = sim.getSnapshot()
            val occCount = snap.occupiedSegmentIds.values.count { "hvy_1" in it }
            if (occCount >= 2) multiSegObserved = true

            // Verify carriages maintain cohesive spacing behind locomotive
            val poses = snap.trainPoses["hvy_1"].orEmpty()
            if (poses.size >= 2) {
                for (i in 0 until poses.lastIndex) {
                    val dist = hypot(poses[i].x - poses[i + 1].x, poses[i].y - poses[i + 1].y)
                    assertTrue("Carriage spacing out of range: $dist", dist in 15f..45f)
                }
            }
        }
        assertTrue("Expected Heavy Freight to occupy >= 2 segments simultaneously", multiSegObserved)
    }

    @Test
    fun `pausing stops simulation and speed multiplier scales cleanly`() {
        val graph = DioramaMaps.valleyJunction()
        val sim = RailwaySimulation(graph, listOf(ScenarioCatalog.tutorialScenario.spawns.first()))
        sim.advanceFrame(0.5f)
        val beforePause = sim.getSnapshot()

        sim.setPaused(true)
        sim.advanceFrame(2.0f)
        val afterPause = sim.getSnapshot()
        assertEquals(beforePause.simTimeSeconds, afterPause.simTimeSeconds, 0.0001f)
        assertEquals(beforePause.trains.first().headTotalDistance, afterPause.trains.first().headTotalDistance, 0.0001f)

        sim.setPaused(false)
        sim.setSpeedMultiplier(2.0f)
        repeat(20) { sim.advanceFrame(0.05f) }
        val afterDouble = sim.getSnapshot()
        assertEquals(beforePause.simTimeSeconds + 2.0f, afterDouble.simTimeSeconds, 0.05f)
    }

    @Test
    fun `switch cannot be toggled while train is actively spanning junction`() {
        val graph = DioramaMaps.valleyJunction()
        val trainSpawn = ScenarioCatalog.tutorialScenario.spawns.first()
        val sim = RailwaySimulation(graph, listOf(trainSpawn))

        var lockedAttemptRejected = false
        repeat(12 * 60) {
            sim.stepFixed()
            val sw1 = sim.getSnapshot().switches.getValue("SW_1")
            if (sw1.isLocked) {
                val toggled = sim.toggleSwitch("SW_1")
                assertFalse("Locked switch should reject toggle", toggled)
                lockedAttemptRejected = true
            }
        }
        assertTrue("Train should have locked SW_1 while traversing junction", lockedAttemptRejected)
    }

    @Test
    fun `tutorial scenario plays cleanly to completion when switches route opposite platforms`() {
        val scenario = ScenarioCatalog.tutorialScenario
        val graph = scenario.graphFactory()
        val switches = graph.switches.toMutableMap().apply {
            // SW_1 -> Platform 1 (0), SW_2 -> Platform 2 (1) so opposing trains never collide
            put("SW_1", getValue("SW_1").copy(activeBranchIndex = 0))
            put("SW_2", getValue("SW_2").copy(activeBranchIndex = 1))
        }
        val sim = RailwaySimulation(graph, scenario.spawns, switches)

        // Let both trains arrive and board at Platform 1 and Platform 2 respectively
        repeat(28 * 60) { sim.stepFixed() }
        // Now flip exit switches so each train exits to its destination portal!
        // First wait until LOC-101 is ready at P1_E and LOC-102 is ready at P2_W ->
        // Actually LOC-101 exits via SW_2 (needs SW_2 = 0 for P1), LOC-102 exits via SW_1 (needs SW_1 = 1 for P2)
        // Flip SW_2 to 0 to release LOC-101 Eastbound, then flip SW_1 to 1 to release LOC-102 Westbound!
        sim.toggleSwitch("SW_2") // 1 -> 0 (P1 to East)
        repeat(20 * 60) { sim.stepFixed() }
        sim.toggleSwitch("SW_1") // 0 -> 1 (P2 to West)
        repeat(25 * 60) { sim.stepFixed() }

        val finalSnap = sim.getSnapshot()
        assertEquals(2, finalSnap.deliveredCount)
        assertTrue(finalSnap.trains.isEmpty())
    }

    @Test
    fun `rush hour generator produces valid routable schedule`() {
        val graph = DioramaMaps.alderfordNetwork()
        val schedule = ScenarioCatalog.generateRushHourSchedule(durationMinutes = 5, seed = 42L)
        assertTrue("Rush hour schedule should contain multiple trains", schedule.size >= 10)
        schedule.forEach { spawn ->
            val route = RoutePlanner.findRoute(
                graph = graph,
                startSegmentId = spawn.entrySegmentId,
                startDir = spawn.entryDir,
                remainingStops = spawn.stopStationIds,
                destinationNodeId = spawn.destinationNodeId,
                category = spawn.category
            )
            assertTrue("Generated spawn ${spawn.serviceNumber} had invalid route", route.isNotEmpty())
        }
    }
}
