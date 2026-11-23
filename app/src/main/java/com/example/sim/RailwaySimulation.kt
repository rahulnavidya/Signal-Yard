package com.example.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

sealed class SimulationEvent {
    data class SwitchToggled(val switchId: String, val label: String) : SimulationEvent()
    data class SignalHeldToggled(val signalId: String, val heldRed: Boolean) : SimulationEvent()
    data class TrainSpawned(val trainId: String, val serviceNumber: String, val category: TrainCategory) : SimulationEvent()
    data class TrainArrivedAtStation(val trainId: String, val stationName: String) : SimulationEvent()
    data class TrainDelivered(val trainId: String, val serviceNumber: String, val onTime: Boolean, val points: Int) : SimulationEvent()
    data class ActionRejected(val reason: String) : SimulationEvent()
}

/**
 * Deterministic, frame-rate independent railway simulation engine.
 * Operates on a fixed internal physics step of 1/60s.
 */
class RailwaySimulation(
    val graph: TrackGraph,
    initialSpawns: List<ScheduledTrainSpawn> = emptyList(),
    initialSwitches: Map<String, TrackSwitch> = graph.switches,
    private val onEvent: (SimulationEvent) -> Unit = {}
) {
    companion object {
        const val FIXED_DT = 1f / 60f
        const val SIGNAL_STOP_MARGIN = 15f
        const val TRAIN_FOLLOW_MARGIN = 22f
    }

    var simTimeSeconds: Float = 0f
        private set
    var isPaused: Boolean = false
        private set
    var speedMultiplier: Float = 1f
        private set

    private var switches: MutableMap<String, TrackSwitch> = initialSwitches.toMutableMap()
    private var signals: MutableMap<String, RailwaySignal> = graph.signals.toMutableMap()
    private val trains: MutableList<Train> = mutableListOf()
    private val spawnQueue: MutableList<ScheduledTrainSpawn> = initialSpawns.sortedBy { it.spawnAtSeconds }.toMutableList()

    private var occupiedBySegments: Map<String, Set<String>> = emptyMap()
    private var reservedSegments: Map<String, String> = emptyMap()
    private var alerts: List<OperationalAlert> = emptyList()

    private var deliveredCount: Int = 0
    private var onTimeDeliveredCount: Int = 0
    private var totalScore: Int = 0
    private var currentStreak: Int = 0
    private var bestStreak: Int = 0
    private var safetyIncidents: Int = 0
    private var totalDelaySeconds: Float = 0f

    private var accumulator: Float = 0f
    private var nextDynamicTrainIndex: Int = 1

    init {
        recomputeNetworkState()
        // Immediately spawn trains scheduled at t == 0
        processSpawnQueue()
        recomputeNetworkState()
    }

    fun setPaused(paused: Boolean) {
        isPaused = paused
    }

    fun setSpeedMultiplier(multiplier: Float) {
        speedMultiplier = multiplier.coerceIn(0.25f, 4f)
    }

    fun toggleSwitch(switchId: String): Boolean {
        val (allowed, reason) = InterlockingSystem.canToggleSwitch(
            graph = graph,
            switchId = switchId,
            switches = switches,
            occupiedBySegments = occupiedBySegments,
            reservedSegments = reservedSegments,
            trains = trains
        )
        if (!allowed) {
            onEvent(SimulationEvent.ActionRejected(reason ?: "Switch locked"))
            return false
        }
        val current = switches[switchId] ?: return false
        val nextIndex = (current.activeBranchIndex + 1) % current.branchSegmentIds.size
        switches[switchId] = current.copy(activeBranchIndex = nextIndex)
        recomputeNetworkState()
        onEvent(SimulationEvent.SwitchToggled(switchId, current.label))
        return true
    }

    fun toggleSignalManualHold(signalId: String): Boolean {
        val current = signals[signalId] ?: return false
        val nextHeld = !current.manualHoldRed
        signals[signalId] = current.copy(manualHoldRed = nextHeld)
        recomputeNetworkState()
        onEvent(SimulationEvent.SignalHeldToggled(signalId, nextHeld))
        return true
    }

    fun enqueueSpawn(spawn: ScheduledTrainSpawn) {
        spawnQueue.add(spawn)
        spawnQueue.sortBy { it.spawnAtSeconds }
        if (spawn.spawnAtSeconds <= simTimeSeconds) {
            processSpawnQueue()
            recomputeNetworkState()
        }
    }

    fun spawnNow(
        category: TrainCategory,
        entrySegmentId: String,
        entryDir: TraversalDir,
        stopStationIds: List<String>,
        destinationNodeId: String,
        servicePrefix: String = category.shortCode
    ): Boolean {
        val num = nextDynamicTrainIndex++
        val id = "dyn_${num}_${(simTimeSeconds * 10).toInt()}"
        val serviceNum = "$servicePrefix-${100 + num}"
        val spawn = ScheduledTrainSpawn(
            id = id,
            serviceNumber = serviceNum,
            name = "${category.displayName} $serviceNum",
            category = category,
            entrySegmentId = entrySegmentId,
            entryDir = entryDir,
            stopStationIds = stopStationIds,
            destinationNodeId = destinationNodeId,
            spawnAtSeconds = simTimeSeconds,
            targetTravelSeconds = 50f
        )
        return trySpawnTrain(spawn)
    }

    fun removeTrain(trainId: String) {
        trains.removeAll { it.id == trainId }
        recomputeNetworkState()
    }

    /**
     * Advances simulation by [frameDeltaSeconds] using fixed physics substeps.
     * Frame rate independent; does nothing when paused.
     */
    fun advanceFrame(frameDeltaSeconds: Float) {
        if (isPaused) return
        val clampedDelta = frameDeltaSeconds.coerceIn(0f, 0.1f) * speedMultiplier
        accumulator += clampedDelta
        while (accumulator >= FIXED_DT) {
            stepFixed(FIXED_DT)
            accumulator -= FIXED_DT
        }
    }

    /**
     * Direct fixed-step advance for deterministic testing.
     */
    fun stepFixed(dt: Float = FIXED_DT) {
        if (isPaused) return
        simTimeSeconds += dt

        // Animate switch blades smoothly
        switches.keys.forEach { id ->
            val sw = switches.getValue(id)
            val target = sw.activeBranchIndex.toFloat()
            if (abs(sw.animProgress - target) > 0.005f) {
                val step = dt * 6f
                val next = if (sw.animProgress < target) min(target, sw.animProgress + step) else max(target, sw.animProgress - step)
                switches[id] = sw.copy(animProgress = next)
            } else if (sw.animProgress != target) {
                switches[id] = sw.copy(animProgress = target)
            }
        }

        processSpawnQueue()
        recomputeNetworkState()
        updateTrains(dt)
        recomputeNetworkState()
    }

    private fun processSpawnQueue() {
        val iterator = spawnQueue.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (pending.spawnAtSeconds <= simTimeSeconds) {
                if (trySpawnTrain(pending)) {
                    iterator.remove()
                }
            }
        }
    }

    private fun trySpawnTrain(spawn: ScheduledTrainSpawn): Boolean {
        val entrySeg = graph.segments[spawn.entrySegmentId] ?: return false
        // Ensure entry segment is currently unoccupied so spawned trains never overlap
        if (occupiedBySegments[entrySeg.id].orEmpty().isNotEmpty()) {
            return false
        }
        val route = RoutePlanner.findRoute(
            graph = graph,
            startSegmentId = spawn.entrySegmentId,
            startDir = spawn.entryDir,
            remainingStops = spawn.stopStationIds,
            destinationNodeId = spawn.destinationNodeId,
            category = spawn.category
        )
        if (route.isEmpty()) {
            onEvent(SimulationEvent.ActionRejected("Invalid route for ${spawn.serviceNumber}"))
            return false
        }

        val initialDist = min(entrySeg.length * 0.35f, 45f)
        val history = listOf(
            TraversedSegment(
                segmentId = entrySeg.id,
                dir = spawn.entryDir,
                length = entrySeg.length,
                cumulativeStart = 0f
            )
        )
        val train = Train(
            id = spawn.id,
            serviceNumber = spawn.serviceNumber,
            name = spawn.name,
            category = spawn.category,
            originNodeId = entrySeg.entryNodeFor(spawn.entryDir),
            stopStationIds = spawn.stopStationIds,
            destinationNodeId = spawn.destinationNodeId,
            currentSegmentId = entrySeg.id,
            currentDir = spawn.entryDir,
            distanceOnSegment = initialDist,
            headTotalDistance = initialDist,
            pathHistory = history,
            speed = spawn.category.maxSpeed * 0.45f,
            status = TrainStatus.MOVING,
            scheduledDurationSec = spawn.targetTravelSeconds,
            intendedRouteSegments = route
        )
        trains.add(train)
        onEvent(SimulationEvent.TrainSpawned(train.id, train.serviceNumber, train.category))
        return true
    }

    private fun updateTrains(dt: Float) {
        val deliveredThisTick = mutableListOf<Train>()

        for (i in trains.indices) {
            var train = trains[i]
            if (train.status == TrainStatus.DELIVERED) continue

            train = train.copy(elapsedSec = train.elapsedSec + dt)

            // Handle station boarding dwell
            if (train.status == TrainStatus.BOARDING) {
                val remDwell = train.dwellRemaining - dt
                if (remDwell > 0f) {
                    trains[i] = train.copy(dwellRemaining = remDwell, speed = 0f)
                    continue
                } else {
                    val seg = graph.segments.getValue(train.currentSegmentId)
                    val stationNode = graph.nodes[seg.exitNodeFor(train.currentDir)]
                    val stationId = stationNode?.stationId
                    val updatedCompleted = if (stationId != null) {
                        train.completedStationIds + stationId
                    } else {
                        train.completedStationIds
                    }
                    // If this station was also the final destination, deliver train now!
                    if (stationNode?.id == train.destinationNodeId) {
                        deliveredThisTick += train.copy(completedStationIds = updatedCompleted, status = TrainStatus.DELIVERED, speed = 0f)
                        continue
                    }
                    // Otherwise attempt to transition past the station node if clear
                    train = train.copy(
                        completedStationIds = updatedCompleted,
                        dwellRemaining = 0f,
                        status = TrainStatus.MOVING,
                        statusReason = "Departure ready"
                    )
                }
            }

            // Compute distance to nearest stopping constraint & target speed ahead
            val lookahead = evaluateLookahead(train)
            val maxAllowedSpeed = min(train.category.maxSpeed, lookahead.targetSpeedLimit)
            val stopDist = lookahead.distanceToStop

            var desiredSpeed = maxAllowedSpeed
            if (stopDist != null) {
                val decel = train.category.brakingDecel
                // v <= sqrt(2 * a * d)
                val safeStopSpeed = if (stopDist <= 0.5f) 0f else kotlin.math.sqrt(2f * decel * (stopDist - 0.5f).coerceAtLeast(0f))
                desiredSpeed = min(desiredSpeed, safeStopSpeed)
            }

            val newSpeed = if (train.speed < desiredSpeed) {
                min(desiredSpeed, train.speed + train.category.acceleration * dt)
            } else if (train.speed > desiredSpeed) {
                max(desiredSpeed, train.speed - train.category.brakingDecel * dt)
            } else {
                train.speed
            }

            // Check arrival at station stop or destination when stopped at stopDist
            if (stopDist != null && stopDist <= 1.2f && newSpeed <= 1.5f) {
                if (lookahead.stopReasonKind == StopReasonKind.STATION_STOP) {
                    val stationName = lookahead.stopLabel ?: "Station"
                    onEvent(SimulationEvent.TrainArrivedAtStation(train.id, stationName))
                    trains[i] = train.copy(
                        speed = 0f,
                        status = TrainStatus.BOARDING,
                        dwellRemaining = train.category.dwellSeconds,
                        statusReason = "Boarding at $stationName"
                    )
                    continue
                } else if (lookahead.stopReasonKind == StopReasonKind.DESTINATION_TERMINUS) {
                    deliveredThisTick += train.copy(speed = 0f, status = TrainStatus.DELIVERED)
                    continue
                }
            }

            // Move forward along segment(s)
            val moveDist = newSpeed * dt
            var curSegId = train.currentSegmentId
            var curDir = train.currentDir
            var segDist = train.distanceOnSegment + moveDist
            var headTotal = train.headTotalDistance + moveDist
            var history = train.pathHistory.toMutableList()
            var delivered = false

            while (true) {
                val curSeg = graph.segments.getValue(curSegId)
                if (segDist < curSeg.length) break

                val exitNodeId = curSeg.exitNodeFor(curDir)
                val exitNode = graph.nodes.getValue(exitNodeId)

                // Check destination arrival at portal exit
                if (exitNodeId == train.destinationNodeId &&
                    (train.nextStopStationId == null || exitNode.stationId == train.nextStopStationId)
                ) {
                    delivered = true
                    break
                }

                // If train hit a station stop right on transition
                val nextStopId = train.nextStopStationId
                if (nextStopId != null && exitNode.stationId == nextStopId) {
                    segDist = curSeg.length - 0.5f
                    headTotal = history.last().cumulativeStart + segDist
                    onEvent(SimulationEvent.TrainArrivedAtStation(train.id, graph.stations[nextStopId]?.name ?: "Station"))
                    train = train.copy(
                        currentSegmentId = curSegId,
                        currentDir = curDir,
                        distanceOnSegment = segDist,
                        headTotalDistance = headTotal,
                        pathHistory = pruneHistory(history, headTotal, train.category.totalLength),
                        speed = 0f,
                        status = TrainStatus.BOARDING,
                        dwellRemaining = train.category.dwellSeconds,
                        statusReason = "Boarding at ${graph.stations[nextStopId]?.name ?: "Station"}"
                    )
                    break
                }

                // Transition to next aligned segment if signal allows
                val signal = liveSignalFacing(exitNodeId, curSegId)
                if (signal != null && signal.aspect == SignalAspect.RED) {
                    segDist = (curSeg.length - SIGNAL_STOP_MARGIN).coerceAtLeast(0f)
                    headTotal = history.last().cumulativeStart + segDist
                    break
                }

                val nextSegId = graph.alignedNextSegment(exitNodeId, curSegId, switches)
                if (nextSegId == null) {
                    // Safety stop before misaligned switch / buffer
                    segDist = (curSeg.length - SIGNAL_STOP_MARGIN).coerceAtLeast(0f)
                    headTotal = history.last().cumulativeStart + segDist
                    break
                }

                val nextSeg = graph.segments.getValue(nextSegId)
                val nextDir = nextSeg.directionFrom(exitNodeId)
                val overflow = segDist - curSeg.length
                val prevCumStart = history.last().cumulativeStart
                history.add(
                    TraversedSegment(
                        segmentId = nextSegId,
                        dir = nextDir,
                        length = nextSeg.length,
                        cumulativeStart = prevCumStart + curSeg.length
                    )
                )
                curSegId = nextSegId
                curDir = nextDir
                segDist = overflow
            }

            if (delivered) {
                deliveredThisTick += train.copy(status = TrainStatus.DELIVERED, speed = 0f)
                continue
            }

            if (train.status == TrainStatus.BOARDING) {
                trains[i] = train
                continue
            }

            val updatedHistory = pruneHistory(history, headTotal, train.category.totalLength)

            val newStatus = when {
                newSpeed < 1.2f && lookahead.stopReasonKind == StopReasonKind.SIGNAL_RED -> TrainStatus.WAITING_SIGNAL
                newSpeed < 1.2f && lookahead.stopReasonKind == StopReasonKind.TRAIN_AHEAD -> TrainStatus.WAITING_SIGNAL
                newSpeed < train.speed - 2f -> TrainStatus.BRAKING
                else -> TrainStatus.MOVING
            }

            val nextWait = if (newStatus == TrainStatus.WAITING_SIGNAL) train.waitTimeSec + dt else 0f
            val updatedDelay = if (train.elapsedSec > train.scheduledDurationSec) totalDelaySeconds + dt else totalDelaySeconds
            totalDelaySeconds = updatedDelay

            // Refresh intended route from current position
            val remainingStops = train.stopStationIds.filter { it !in train.completedStationIds }
            val updatedRoute = RoutePlanner.findRoute(
                graph = graph,
                startSegmentId = curSegId,
                startDir = curDir,
                remainingStops = remainingStops,
                destinationNodeId = train.destinationNodeId,
                category = train.category
            )

            trains[i] = train.copy(
                currentSegmentId = curSegId,
                currentDir = curDir,
                distanceOnSegment = segDist,
                headTotalDistance = headTotal,
                pathHistory = updatedHistory,
                speed = newSpeed,
                status = newStatus,
                statusReason = lookahead.statusText,
                waitTimeSec = nextWait,
                intendedRouteSegments = updatedRoute
            )
        }

        if (deliveredThisTick.isNotEmpty()) {
            for (done in deliveredThisTick) {
                val onTime = !done.isDelayed
                deliveredCount++
                if (onTime) {
                    onTimeDeliveredCount++
                    currentStreak++
                    bestStreak = max(bestStreak, currentStreak)
                } else {
                    currentStreak = 0
                }
                val pts = 200 + (if (onTime) 100 else 0) + (currentStreak * 20)
                totalScore += pts
                trains.removeAll { it.id == done.id }
                onEvent(SimulationEvent.TrainDelivered(done.id, done.serviceNumber, onTime, pts))
            }
        }
    }

    private enum class StopReasonKind {
        NONE,
        SIGNAL_RED,
        STATION_STOP,
        DESTINATION_TERMINUS,
        TRAIN_AHEAD,
        SWITCH_AGAINST
    }

    private data class LookaheadResult(
        val distanceToStop: Float?,
        val targetSpeedLimit: Float,
        val stopReasonKind: StopReasonKind,
        val stopLabel: String?,
        val statusText: String
    )

    private fun evaluateLookahead(train: Train): LookaheadResult {
        val curSeg = graph.segments.getValue(train.currentSegmentId)
        var targetLimit = curSeg.speedLimit
        var statusText = "Proceeding on schedule"

        // 1. Check same-segment train ahead
        val sameSegAhead = trains.filter {
            it.id != train.id &&
                it.currentSegmentId == train.currentSegmentId &&
                it.currentDir == train.currentDir &&
                it.distanceOnSegment > train.distanceOnSegment
        }.minByOrNull { it.distanceOnSegment }

        if (sameSegAhead != null) {
            val tailPos = sameSegAhead.distanceOnSegment - sameSegAhead.category.totalLength
            val gap = (tailPos - train.distanceOnSegment - TRAIN_FOLLOW_MARGIN).coerceAtLeast(0f)
            return LookaheadResult(
                distanceToStop = gap,
                targetSpeedLimit = targetLimit,
                stopReasonKind = StopReasonKind.TRAIN_AHEAD,
                stopLabel = sameSegAhead.serviceNumber,
                statusText = "Following ${sameSegAhead.serviceNumber}"
            )
        }

        val sameSegOpposing = trains.firstOrNull {
            it.id != train.id &&
                it.currentSegmentId == train.currentSegmentId &&
                it.currentDir != train.currentDir &&
                (curSeg.length - it.distanceOnSegment) > train.distanceOnSegment
        }
        if (sameSegOpposing != null) {
            val oppNoseDistFromEntry = curSeg.length - sameSegOpposing.distanceOnSegment
            val gap = (oppNoseDistFromEntry - train.distanceOnSegment - TRAIN_FOLLOW_MARGIN).coerceAtLeast(0f)
            return LookaheadResult(
                distanceToStop = gap,
                targetSpeedLimit = targetLimit,
                stopReasonKind = StopReasonKind.SIGNAL_RED,
                stopLabel = sameSegOpposing.serviceNumber,
                statusText = "Held: opposing ${sameSegOpposing.serviceNumber} on track"
            )
        }

        // 2. Trace aligned path forward up to braking horizon (~280u)
        var accumDist = curSeg.length - train.distanceOnSegment
        var walkSeg = curSeg
        var walkDir = train.currentDir

        for (stepIdx in 0..8) {
            val exitNodeId = walkSeg.exitNodeFor(walkDir)
            val exitNode = graph.nodes.getValue(exitNodeId)

            // Check destination terminus or portal
            if (exitNodeId == train.destinationNodeId && train.nextStopStationId == null) {
                if (exitNode.type == NodeType.BUFFER || exitNode.type == NodeType.STATION_STOP) {
                    val dist = (accumDist - 6f).coerceAtLeast(0f)
                    return LookaheadResult(
                        distanceToStop = dist,
                        targetSpeedLimit = targetLimit,
                        stopReasonKind = StopReasonKind.DESTINATION_TERMINUS,
                        stopLabel = exitNode.label ?: "Terminus",
                        statusText = "Approaching terminus"
                    )
                }
                // Portal exit: no stopping needed, clear through!
                return LookaheadResult(null, targetLimit, StopReasonKind.NONE, null, statusText)
            }

            // Check scheduled station stop
            val nextStop = train.nextStopStationId
            if (nextStop != null && exitNode.stationId == nextStop) {
                val stName = graph.stations[nextStop]?.name ?: "Station"
                val dist = (accumDist - 6f).coerceAtLeast(0f)
                return LookaheadResult(
                    distanceToStop = dist,
                    targetSpeedLimit = min(targetLimit, 68f),
                    stopReasonKind = StopReasonKind.STATION_STOP,
                    stopLabel = stName,
                    statusText = "Stopping at $stName"
                )
            }

            // Check signal at exitNode (from live evaluated signals map)
            val sig = liveSignalFacing(exitNodeId, walkSeg.id)
            if (sig != null) {
                when (sig.aspect) {
                    SignalAspect.RED -> {
                        val dist = (accumDist - SIGNAL_STOP_MARGIN).coerceAtLeast(0f)
                        return LookaheadResult(
                            distanceToStop = dist,
                            targetSpeedLimit = targetLimit,
                            stopReasonKind = StopReasonKind.SIGNAL_RED,
                            stopLabel = sig.label,
                            statusText = sig.reason
                        )
                    }
                    SignalAspect.YELLOW -> {
                        targetLimit = min(targetLimit, train.category.maxSpeed * 0.62f)
                        statusText = sig.reason
                    }
                    SignalAspect.GREEN -> Unit
                }
            }

            // Advance to next aligned segment
            val nextSegId = graph.alignedNextSegment(exitNodeId, walkSeg.id, switches)
            if (nextSegId == null) {
                if (exitNode.type == NodeType.PORTAL_EXIT) {
                    return LookaheadResult(null, targetLimit, StopReasonKind.NONE, null, statusText)
                }
                val dist = (accumDist - SIGNAL_STOP_MARGIN).coerceAtLeast(0f)
                val sw = graph.switchAt(exitNodeId)
                val reason = if (sw != null) "Switch ${sw.label} set against route" else "End of track ahead"
                return LookaheadResult(
                    distanceToStop = dist,
                    targetSpeedLimit = targetLimit,
                    stopReasonKind = StopReasonKind.SWITCH_AGAINST,
                    stopLabel = sw?.label,
                    statusText = reason
                )
            }

            val nextSeg = graph.segments.getValue(nextSegId)
            val nextDir = nextSeg.directionFrom(exitNodeId)
            targetLimit = min(targetLimit, nextSeg.speedLimit)

            // Check if another train occupies the tail of nextSeg
            val trainOnNext = trains.filter {
                it.id != train.id && it.currentSegmentId == nextSegId && it.currentDir == nextDir
            }.minByOrNull { it.distanceOnSegment }
            if (trainOnNext != null) {
                val tailDistOnNext = (trainOnNext.distanceOnSegment - trainOnNext.category.totalLength).coerceAtLeast(0f)
                val totalGap = (accumDist + tailDistOnNext - TRAIN_FOLLOW_MARGIN).coerceAtLeast(0f)
                return LookaheadResult(
                    distanceToStop = totalGap,
                    targetSpeedLimit = targetLimit,
                    stopReasonKind = StopReasonKind.TRAIN_AHEAD,
                    stopLabel = trainOnNext.serviceNumber,
                    statusText = "Waiting behind ${trainOnNext.serviceNumber}"
                )
            }
            val opposingOnNext = trains.firstOrNull {
                it.id != train.id && it.currentSegmentId == nextSegId && it.currentDir != nextDir
            }
            if (opposingOnNext != null) {
                val gap = (accumDist - SIGNAL_STOP_MARGIN).coerceAtLeast(0f)
                return LookaheadResult(
                    distanceToStop = gap,
                    targetSpeedLimit = targetLimit,
                    stopReasonKind = StopReasonKind.SIGNAL_RED,
                    stopLabel = opposingOnNext.serviceNumber,
                    statusText = "Held: opposing ${opposingOnNext.serviceNumber} ahead"
                )
            }

            accumDist += nextSeg.length
            if (accumDist > 300f) break
            walkSeg = nextSeg
            walkDir = nextDir
        }

        return LookaheadResult(null, targetLimit, StopReasonKind.NONE, null, statusText)
    }

    private fun pruneHistory(
        history: MutableList<TraversedSegment>,
        headTotalDistance: Float,
        trainLength: Float
    ): List<TraversedSegment> {
        val tailDistance = (headTotalDistance - trainLength - 16f).coerceAtLeast(0f)
        while (history.size > 1) {
            val first = history.first()
            if (first.cumulativeStart + first.length < tailDistance) {
                history.removeAt(0)
            } else {
                break
            }
        }
        return history
    }

    private fun recomputeNetworkState() {
        val occMap = HashMap<String, MutableSet<String>>()
        for (train in trains) {
            if (train.status == TrainStatus.DELIVERED) continue
            val tailStart = (train.headTotalDistance - train.category.totalLength).coerceAtLeast(0f)
            val headEnd = train.headTotalDistance
            for (hist in train.pathHistory) {
                val segStart = hist.cumulativeStart
                val segEnd = hist.cumulativeStart + hist.length
                if (segEnd >= tailStart && segStart <= headEnd) {
                    occMap.getOrPut(hist.segmentId) { mutableSetOf() }.add(train.id)
                }
            }
        }
        occupiedBySegments = occMap

        val interlock = InterlockingSystem.evaluateInterlocking(
            graph = graph,
            switches = switches,
            signals = signals,
            occupiedBySegments = occupiedBySegments,
            trains = trains
        )
        switches = interlock.switches.toMutableMap()
        signals = interlock.signals.toMutableMap()
        reservedSegments = interlock.reservedSegments

        alerts = InterlockingSystem.detectAlerts(
            graph = graph,
            switches = switches,
            signals = signals,
            trains = trains
        )
    }

    fun getSnapshot(): SimulationSnapshot {
        val poses = HashMap<String, List<CarriagePose>>()
        for (train in trains) {
            poses[train.id] = computeCarriagePoses(train)
        }
        return SimulationSnapshot(
            simTimeSeconds = simTimeSeconds,
            isPaused = isPaused,
            speedMultiplier = speedMultiplier,
            switches = switches.toMap(),
            signals = signals.toMap(),
            occupiedSegmentIds = occupiedBySegments,
            reservedSegmentIds = reservedSegments,
            trains = trains.toList(),
            trainPoses = poses,
            pendingSpawns = spawnQueue.toList(),
            alerts = alerts,
            deliveredCount = deliveredCount,
            onTimeDeliveredCount = onTimeDeliveredCount,
            totalScore = totalScore,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            safetyIncidents = safetyIncidents,
            totalDelaySeconds = totalDelaySeconds
        )
    }

    private fun liveSignalFacing(nodeId: String, fromSegmentId: String): RailwaySignal? {
        return signals.values.firstOrNull { it.nodeId == nodeId && it.facingFromSegmentId == fromSegmentId }
    }

    private fun computeCarriagePoses(train: Train): List<CarriagePose> {
        val totalUnits = train.category.carriageCount + 1
        val spacing = train.category.totalLength / totalUnits.toFloat()
        val poses = ArrayList<CarriagePose>(totalUnits)

        for (idx in 0 until totalUnits) {
            val distBehindHead = idx * spacing + spacing * 0.42f
            val targetGlobalDist = train.headTotalDistance - distBehindHead
            val pose = samplePoseAtGlobalDistance(train.pathHistory, targetGlobalDist)
            poses.add(
                CarriagePose(
                    index = idx,
                    isLocomotive = idx == 0,
                    x = pose.x,
                    y = pose.y,
                    angleRadians = pose.angleRadians
                )
            )
        }
        return poses
    }

    private fun samplePoseAtGlobalDistance(history: List<TraversedSegment>, globalDist: Float): Pose2D {
        if (globalDist < 0f) {
            val first = history.first()
            val entryPose = graph.curveOf(first.segmentId).sampleAtDistance(0f, first.dir)
            val dx = kotlin.math.cos(entryPose.angleRadians) * globalDist
            val dy = kotlin.math.sin(entryPose.angleRadians) * globalDist
            return Pose2D(entryPose.x + dx, entryPose.y + dy, entryPose.angleRadians)
        }
        for (i in history.indices.reversed()) {
            val h = history[i]
            if (globalDist >= h.cumulativeStart || i == 0) {
                val localDist = (globalDist - h.cumulativeStart).coerceIn(0f, h.length)
                return graph.curveOf(h.segmentId).sampleAtDistance(localDist, h.dir)
            }
        }
        val first = history.first()
        return graph.curveOf(first.segmentId).sampleAtDistance(0f, first.dir)
    }
}
