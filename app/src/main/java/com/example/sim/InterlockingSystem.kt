package com.example.sim

object InterlockingSystem {

    data class InterlockingResult(
        val switches: Map<String, TrackSwitch>,
        val signals: Map<String, RailwaySignal>,
        val reservedSegments: Map<String, String>
    )

    /**
     * Determines whether a switch can be safely toggled.
     * Prevents changing a switch while a train physically occupies the switch throat
     * or has a committed reservation across it.
     */
    fun canToggleSwitch(
        graph: TrackGraph,
        switchId: String,
        switches: Map<String, TrackSwitch>,
        occupiedBySegments: Map<String, Set<String>>,
        reservedSegments: Map<String, String>,
        trains: List<Train>
    ): Pair<Boolean, String?> {
        val sw = switches[switchId] ?: return false to "Unknown switch"
        val connectedSegs = listOf(sw.stemSegmentId) + sw.branchSegmentIds

        // 1. Check if any train spans across two connected segments of this switch (physically on the turnout)
        for (train in trains) {
            if (train.status == TrainStatus.DELIVERED) continue
            val occupied = connectedSegs.count { segId -> train.id in occupiedBySegments[segId].orEmpty() }
            if (occupied >= 2) {
                return false to "Switch ${sw.label} locked: ${train.serviceNumber} is traversing junction"
            }
            // Also lock if train is actively moving right up to an already-cleared switch on its current segment
            if (train.currentSegmentId in connectedSegs && train.speed > 5f) {
                val seg = graph.segments.getValue(train.currentSegmentId)
                val exitNode = seg.exitNodeFor(train.currentDir)
                if (exitNode == sw.nodeId) {
                    val remaining = seg.length - train.distanceOnSegment
                    val stopDist = (train.speed * train.speed) / (2f * train.category.brakingDecel) + 16f
                    val alignedExit = sw.connectedExitFor(train.currentSegmentId)
                    if (alignedExit != null && remaining < stopDist) {
                        return false to "Switch ${sw.label} locked: ${train.serviceNumber} committed at speed"
                    }
                }
            }
        }

        // 2. Check active reservations on connected branch/stem segments if a train is actively moving on approach
        val activeMovingTrainIds = trains.filter { it.status != TrainStatus.DELIVERED && it.speed > 2f }.map { it.id }.toSet()
        for (segId in connectedSegs) {
            val resTrainId = reservedSegments[segId]
            if (resTrainId != null && resTrainId in activeMovingTrainIds) {
                val resTrain = trains.firstOrNull { it.id == resTrainId }
                if (resTrain != null && resTrain.currentSegmentId != segId) {
                    return false to "Switch ${sw.label} reserved by ${resTrain.serviceNumber}"
                }
            }
        }

        return true to null
    }

    fun evaluateInterlocking(
        graph: TrackGraph,
        switches: Map<String, TrackSwitch>,
        signals: Map<String, RailwaySignal>,
        occupiedBySegments: Map<String, Set<String>>,
        trains: List<Train>
    ): InterlockingResult {
        // Build directional occupancy map to detect opposing movements in single-track blocks
        val activeTrains = trains.filter { it.status != TrainStatus.DELIVERED }
        val reserved = HashMap<String, String>()

        // Each moving or committed train reserves its current segment and the next aligned block up to the next signal
        // (unless stopped at a red signal).
        for (train in activeTrains) {
            occupiedBySegments.forEach { (segId, trainIds) ->
                if (train.id in trainIds) {
                    reserved[segId] = train.id
                }
            }
            if (train.status == TrainStatus.MOVING || train.status == TrainStatus.BRAKING) {
                val seg = graph.segments.getValue(train.currentSegmentId)
                val exitNodeId = seg.exitNodeFor(train.currentDir)
                val sig = signals.values.firstOrNull { it.nodeId == exitNodeId && it.facingFromSegmentId == seg.id }
                val remaining = seg.length - train.distanceOnSegment
                val commitDist = (train.speed * train.speed) / (2f * train.category.brakingDecel) + 28f
                if ((sig == null || sig.aspect != SignalAspect.RED) && remaining <= commitDist) {
                    val nextSegId = graph.alignedNextSegment(exitNodeId, seg.id, switches)
                    if (nextSegId != null && nextSegId !in reserved) {
                        reserved[nextSegId] = train.id
                    }
                }
            }
        }

        val updatedSignals = signals.mapValues { (_, sig) ->
            evaluateSignal(graph, sig, switches, occupiedBySegments, reserved, activeTrains)
        }

        val updatedSwitches = switches.mapValues { (swId, sw) ->
            val (canToggle, _) = canToggleSwitch(graph, swId, switches, occupiedBySegments, reserved, trains)
            sw.copy(isLocked = !canToggle)
        }

        return InterlockingResult(
            switches = updatedSwitches,
            signals = updatedSignals,
            reservedSegments = reserved
        )
    }

    private fun evaluateSignal(
        graph: TrackGraph,
        sig: RailwaySignal,
        switches: Map<String, TrackSwitch>,
        occupiedBySegments: Map<String, Set<String>>,
        reservedSegments: Map<String, String>,
        activeTrains: List<Train>
    ): RailwaySignal {
        if (sig.manualHoldRed) {
            return sig.copy(aspect = SignalAspect.RED, reason = "Held at Danger by Dispatcher")
        }

        val exitSegId = graph.alignedNextSegment(sig.nodeId, sig.facingFromSegmentId, switches)
            ?: run {
                val sw = graph.switchAt(sig.nodeId)
                val reason = if (sw != null) "Switch ${sw.label} set against route" else "No aligned exit route"
                return sig.copy(aspect = SignalAspect.RED, reason = reason)
            }

        val approachingTrain = activeTrains.firstOrNull {
            it.currentSegmentId == sig.facingFromSegmentId &&
                graph.segments.getValue(it.currentSegmentId).exitNodeFor(it.currentDir) == sig.nodeId
        }

        // Check block occupancy ahead up to the next opposing/following signal or terminal
        val blockSegs = traceSignalBlock(graph, switches, sig.nodeId, exitSegId)
        for (bSegId in blockSegs) {
            val occTrains = occupiedBySegments[bSegId].orEmpty()
            val conflictingOcc = occTrains.firstOrNull { it != approachingTrain?.id }
            if (conflictingOcc != null) {
                val blockingName = activeTrains.firstOrNull { it.id == conflictingOcc }?.serviceNumber ?: "Train"
                return sig.copy(
                    aspect = SignalAspect.RED,
                    reason = "Section ahead occupied by $blockingName"
                )
            }
            val resTrainId = reservedSegments[bSegId]
            if (resTrainId != null && resTrainId != approachingTrain?.id) {
                val resName = activeTrains.firstOrNull { it.id == resTrainId }?.serviceNumber ?: "Train"
                return sig.copy(
                    aspect = SignalAspect.RED,
                    reason = "Track reserved by $resName"
                )
            }
        }

        // Also check opposing head-on trains further down a single-track corridor before the next passing loop
        val corridorConflict = findOpposingTrainInCorridor(
            graph, switches, sig.nodeId, exitSegId, approachingTrain?.id, activeTrains
        )
        if (corridorConflict != null) {
            return sig.copy(
                aspect = SignalAspect.RED,
                reason = "Opposing ${corridorConflict.serviceNumber} approaching single track"
            )
        }

        // Look ahead to next signal/switch for YELLOW caution aspect
        val lastSegId = blockSegs.lastOrNull() ?: exitSegId
        val lastSeg = graph.segments.getValue(lastSegId)
        val firstSeg = graph.segments.getValue(exitSegId)
        var walkNode = firstSeg.otherNode(sig.nodeId)
        var walkSeg = exitSegId
        for (i in 1 until blockSegs.size) {
            walkSeg = blockSegs[i]
            walkNode = graph.segments.getValue(walkSeg).otherNode(walkNode)
        }

        val nextNode = graph.nodes[walkNode]
        val nextExit = graph.alignedNextSegment(walkNode, walkSeg, switches)
        if (nextNode != null && nextNode.type == NodeType.BUFFER) {
            return sig.copy(aspect = SignalAspect.YELLOW, reason = "Caution: Terminus buffer ahead")
        }
        if (nextExit == null && nextNode?.type != NodeType.PORTAL_EXIT) {
            return sig.copy(aspect = SignalAspect.YELLOW, reason = "Caution: Switch ahead set against")
        }
        if (nextExit != null) {
            val nextOcc = occupiedBySegments[nextExit].orEmpty()
            if (nextOcc.any { it != approachingTrain?.id }) {
                return sig.copy(aspect = SignalAspect.YELLOW, reason = "Caution: Next block occupied")
            }
        }
        if (lastSeg.speedLimit < 65f) {
            return sig.copy(aspect = SignalAspect.YELLOW, reason = "Caution: Diverging speed limit")
        }

        return sig.copy(aspect = SignalAspect.GREEN, reason = "Route clear")
    }

    /**
     * Collects the immediate protected segments from [startNodeId] through [firstSegId]
     * until encountering a junction switch, station stop, portal, or next signal.
     */
    private fun traceSignalBlock(
        graph: TrackGraph,
        switches: Map<String, TrackSwitch>,
        startNodeId: String,
        firstSegId: String
    ): List<String> {
        val result = mutableListOf(firstSegId)
        var curSegId = firstSegId
        var curNodeId = graph.segments.getValue(firstSegId).otherNode(startNodeId)
        val visited = hashSetOf(firstSegId)

        repeat(4) {
            val node = graph.nodes[curNodeId] ?: return result
            if (node.type == NodeType.PORTAL_EXIT ||
                node.type == NodeType.BUFFER ||
                node.type == NodeType.STATION_STOP ||
                graph.signalFacing(curNodeId, curSegId) != null
            ) {
                return result
            }
            val nextSegId = graph.alignedNextSegment(curNodeId, curSegId, switches) ?: return result
            if (!visited.add(nextSegId)) return result
            result.add(nextSegId)
            curSegId = nextSegId
            curNodeId = graph.segments.getValue(nextSegId).otherNode(curNodeId)
        }
        return result
    }

    private fun findOpposingTrainInCorridor(
        graph: TrackGraph,
        switches: Map<String, TrackSwitch>,
        startNodeId: String,
        firstSegId: String,
        selfTrainId: String?,
        activeTrains: List<Train>
    ): Train? {
        var curNodeId = startNodeId
        var curSegId = firstSegId
        val visited = hashSetOf(firstSegId)

        repeat(6) {
            val seg = graph.segments[curSegId] ?: return null
            val nextNodeId = seg.otherNode(curNodeId)
            val expectedForwardDir = seg.directionFrom(curNodeId)

            val opposing = activeTrains.firstOrNull { train ->
                train.id != selfTrainId &&
                    train.currentSegmentId == curSegId &&
                    train.currentDir != expectedForwardDir &&
                    (train.speed > 1f || train.status != TrainStatus.WAITING_SIGNAL)
            }
            if (opposing != null) return opposing

            val node = graph.nodes[nextNodeId] ?: return null
            // Stop scanning only once we reach an actual turnout switch (passing loop throat) or exit portal
            if (graph.switchAt(nextNodeId) != null || node.type == NodeType.PORTAL_EXIT || node.type == NodeType.PORTAL_BIDIRECTIONAL) {
                return null
            }
            val nextSegId = graph.alignedNextSegment(nextNodeId, curSegId, switches) ?: return null
            if (!visited.add(nextSegId)) return null
            curNodeId = nextNodeId
            curSegId = nextSegId
        }
        return null
    }

    /**
     * Detects operational conflicts, deadlocks, and route misalignments to give clear dispatcher feedback.
     */
    fun detectAlerts(
        graph: TrackGraph,
        switches: Map<String, TrackSwitch>,
        signals: Map<String, RailwaySignal>,
        trains: List<Train>
    ): List<OperationalAlert> {
        val alerts = mutableListOf<OperationalAlert>()
        val active = trains.filter { it.status != TrainStatus.DELIVERED }

        // 1. Check route misalignments (current switch alignment does not reach next stop/destination)
        for (train in active) {
            val aligned = RoutePlanner.traceAlignedPath(graph, switches, train.currentSegmentId, train.currentDir)
            val nextStop = train.nextStopStationId
            val targetNodeReached = if (nextStop != null) {
                aligned.any { graph.nodes[it.exitNodeId]?.stationId == nextStop }
            } else {
                aligned.any { it.exitNodeId == train.destinationNodeId }
            }

            if (!targetNodeReached && aligned.isNotEmpty()) {
                val lastStep = aligned.last()
                val nodePos = graph.nodes[lastStep.exitNodeId]?.position ?: Vec2(graph.worldWidth / 2f, graph.worldHeight / 2f)
                val targetDesc = if (nextStop != null) {
                    graph.stations[nextStop]?.name ?: nextStop
                } else {
                    graph.nodes[train.destinationNodeId]?.label ?: "destination"
                }
                alerts += OperationalAlert(
                    id = "misalign_${train.id}",
                    severity = AlertSeverity.WARNING,
                    title = "${train.serviceNumber} Off Route",
                    message = "Aligned switches do not reach $targetDesc. Check junctions ahead.",
                    focusX = nodePos.x,
                    focusY = nodePos.y,
                    relatedTrainId = train.id
                )
            }
        }

        // 2. Detect head-on deadlocks or mutual circular waits
        val waitingTrains = active.filter { it.status == TrainStatus.WAITING_SIGNAL }
        for (i in waitingTrains.indices) {
            for (j in i + 1 until waitingTrains.size) {
                val t1 = waitingTrains[i]
                val t2 = waitingTrains[j]
                val s1 = graph.segments.getValue(t1.currentSegmentId)
                val s2 = graph.segments.getValue(t2.currentSegmentId)
                val n1 = s1.exitNodeFor(t1.currentDir)
                val n2 = s2.exitNodeFor(t2.currentDir)
                val sig1 = graph.signalFacing(n1, s1.id)
                val sig2 = graph.signalFacing(n2, s2.id)
                val mutuallyBlocked = sig1 != null && sig2 != null &&
                    (t2.serviceNumber in sig1.reason || t1.serviceNumber in sig2.reason ||
                        (t1.waitTimeSec > 6f && t2.waitTimeSec > 6f && s1.otherNode(n1) == s2.otherNode(n2)))

                if (mutuallyBlocked || (t1.currentSegmentId == t2.currentSegmentId && t1.currentDir != t2.currentDir)) {
                    val pos = graph.nodes[n1]?.position ?: Vec2(graph.worldWidth / 2f, graph.worldHeight / 2f)
                    alerts += OperationalAlert(
                        id = "deadlock_${t1.id}_${t2.id}",
                        severity = AlertSeverity.CRITICAL,
                        title = "Deadlock Risk: ${t1.serviceNumber} & ${t2.serviceNumber}",
                        message = "Opposing movements blocking the same corridor. Reroute via passing loop.",
                        focusX = pos.x,
                        focusY = pos.y,
                        relatedTrainId = t1.id
                    )
                }
            }
        }

        // 3. Prolonged signal wait informational alert
        for (train in waitingTrains) {
            if (train.waitTimeSec >= 10f && alerts.none { it.relatedTrainId == train.id }) {
                val seg = graph.segments.getValue(train.currentSegmentId)
                val exitPos = graph.nodes[seg.exitNodeFor(train.currentDir)]?.position ?: Vec2(0f, 0f)
                alerts += OperationalAlert(
                    id = "wait_${train.id}",
                    severity = AlertSeverity.INFO,
                    title = "${train.serviceNumber} Held at Signal",
                    message = train.statusReason,
                    focusX = exitPos.x,
                    focusY = exitPos.y,
                    relatedTrainId = train.id
                )
            }
        }

        return alerts.distinctBy { it.id }
    }
}
