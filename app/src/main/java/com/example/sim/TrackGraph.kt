package com.example.sim

enum class NodeType {
    PORTAL_ENTRY,
    PORTAL_EXIT,
    PORTAL_BIDIRECTIONAL,
    JUNCTION,
    STATION_STOP,
    BUFFER
}

enum class TrackCategory {
    MAINLINE,
    SIDING,
    PLATFORM,
    VIADUCT,
    TUNNEL
}

enum class DirectionMode {
    BIDIRECTIONAL,
    A_TO_B,
    B_TO_A
}

enum class TraversalDir {
    A_TO_B,
    B_TO_A
}

enum class SignalAspect {
    GREEN,
    YELLOW,
    RED
}

data class TrackNode(
    val id: String,
    val x: Float,
    val y: Float,
    val type: NodeType,
    val label: String? = null,
    val stationId: String? = null,
    val platformNumber: Int? = null
) {
    val position: Vec2 get() = Vec2(x, y)
}

data class TrackSegment(
    val id: String,
    val nodeA: String,
    val nodeB: String,
    val controlPoint: Vec2? = null,
    val category: TrackCategory = TrackCategory.MAINLINE,
    val speedLimit: Float = 95f,
    val allowedDirections: DirectionMode = DirectionMode.BIDIRECTIONAL,
    val allowHeavyFreight: Boolean = true,
    val stationId: String? = null,
    val platformNumber: Int? = null,
    val length: Float
) {
    fun otherNode(nodeId: String): String = when (nodeId) {
        nodeA -> nodeB
        nodeB -> nodeA
        else -> error("Node $nodeId does not belong to segment $id")
    }

    fun directionFrom(entryNodeId: String): TraversalDir = when (entryNodeId) {
        nodeA -> TraversalDir.A_TO_B
        nodeB -> TraversalDir.B_TO_A
        else -> error("Node $entryNodeId does not belong to segment $id")
    }

    fun entryNodeFor(dir: TraversalDir): String = when (dir) {
        TraversalDir.A_TO_B -> nodeA
        TraversalDir.B_TO_A -> nodeB
    }

    fun exitNodeFor(dir: TraversalDir): String = when (dir) {
        TraversalDir.A_TO_B -> nodeB
        TraversalDir.B_TO_A -> nodeA
    }

    fun supportsDirection(dir: TraversalDir): Boolean = when (allowedDirections) {
        DirectionMode.BIDIRECTIONAL -> true
        DirectionMode.A_TO_B -> dir == TraversalDir.A_TO_B
        DirectionMode.B_TO_A -> dir == TraversalDir.B_TO_A
    }
}

data class TrackSwitch(
    val id: String,
    val nodeId: String,
    val stemSegmentId: String,
    val branchSegmentIds: List<String>,
    val activeBranchIndex: Int = 0,
    val label: String,
    val isLocked: Boolean = false,
    val animProgress: Float = 0f
) {
    val activeBranchSegmentId: String
        get() = branchSegmentIds[activeBranchIndex.coerceIn(branchSegmentIds.indices)]

    fun connectedExitFor(incomingSegmentId: String): String? {
        return when (incomingSegmentId) {
            stemSegmentId -> activeBranchSegmentId
            activeBranchSegmentId -> stemSegmentId
            else -> null
        }
    }

    fun connects(seg1: String, seg2: String): Boolean {
        return (seg1 == stemSegmentId && seg2 in branchSegmentIds) ||
            (seg2 == stemSegmentId && seg1 in branchSegmentIds)
    }
}

data class RailwaySignal(
    val id: String,
    val nodeId: String,
    val facingFromSegmentId: String,
    val label: String,
    val aspect: SignalAspect = SignalAspect.GREEN,
    val manualHoldRed: Boolean = false,
    val reason: String = "Route clear"
)

data class StationInfo(
    val id: String,
    val name: String,
    val code: String,
    val x: Float,
    val y: Float,
    val platformCount: Int
)

sealed class DioramaProp {
    data class Hill(val x: Float, val y: Float, val rx: Float, val ry: Float, val elevation: Int) : DioramaProp()
    data class River(val points: List<Vec2>, val width: Float) : DioramaProp()
    data class TreeCluster(val x: Float, val y: Float, val count: Int, val seed: Int) : DioramaProp()
    data class Building(val x: Float, val y: Float, val width: Float, val height: Float, val style: BuildingStyle, val angleDeg: Float = 0f) : DioramaProp()
    data class BridgeDeck(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val width: Float) : DioramaProp()
}

enum class BuildingStyle {
    STATION_HOUSE,
    WAREHOUSE,
    COTTAGE,
    SIGNAL_BOX,
    WATER_TOWER
}

data class TrackGraph(
    val id: String,
    val name: String,
    val worldWidth: Float,
    val worldHeight: Float,
    val nodes: Map<String, TrackNode>,
    val segments: Map<String, TrackSegment>,
    val switches: Map<String, TrackSwitch>,
    val signals: Map<String, RailwaySignal>,
    val stations: Map<String, StationInfo>,
    val props: List<DioramaProp>
) {
    private val curves: Map<String, TrackCurve> = segments.mapValues { (_, seg) ->
        val a = nodes.getValue(seg.nodeA).position
        val b = nodes.getValue(seg.nodeB).position
        TrackCurve(a, seg.controlPoint, b)
    }

    private val nodeSegments: Map<String, List<TrackSegment>> = buildMap {
        nodes.keys.forEach { put(it, mutableListOf()) }
        segments.values.forEach { seg ->
            (get(seg.nodeA) as MutableList).add(seg)
            (get(seg.nodeB) as MutableList).add(seg)
        }
    }

    private val switchByNode: Map<String, TrackSwitch> = switches.values.associateBy { it.nodeId }

    fun curveOf(segmentId: String): TrackCurve = curves.getValue(segmentId)

    fun segmentsAt(nodeId: String): List<TrackSegment> = nodeSegments[nodeId].orEmpty()

    fun switchAt(nodeId: String): TrackSwitch? = switchByNode[nodeId]

    fun signalFacing(nodeId: String, fromSegmentId: String): RailwaySignal? =
        signals.values.firstOrNull { it.nodeId == nodeId && it.facingFromSegmentId == fromSegmentId }

    /**
     * Given a train arriving at [nodeId] via [incomingSegmentId], returns the next segment
     * according to the CURRENT mechanical switch position (or direct connection).
     */
    fun alignedNextSegment(nodeId: String, incomingSegmentId: String, currentSwitches: Map<String, TrackSwitch>): String? {
        val sw = currentSwitches.values.firstOrNull { it.nodeId == nodeId }
        if (sw != null) {
            return sw.connectedExitFor(incomingSegmentId)
        }
        val candidates = segmentsAt(nodeId).filter { it.id != incomingSegmentId }
        return candidates.firstOrNull { seg ->
            val dir = seg.directionFrom(nodeId)
            seg.supportsDirection(dir)
        }?.id
    }

    /**
     * All structurally possible exit segments from [nodeId] when arriving from [incomingSegmentId]
     * (ignoring current switch state, respecting track directionality & train category).
     */
    fun possibleNextSegments(
        nodeId: String,
        incomingSegmentId: String,
        trainCategory: TrainCategory? = null
    ): List<TrackSegment> {
        val sw = switchByNode[nodeId]
        val candidates = if (sw != null) {
            when {
                incomingSegmentId == sw.stemSegmentId -> sw.branchSegmentIds.mapNotNull { segments[it] }
                incomingSegmentId in sw.branchSegmentIds -> listOfNotNull(segments[sw.stemSegmentId])
                else -> emptyList()
            }
        } else {
            segmentsAt(nodeId).filter { it.id != incomingSegmentId }
        }
        return candidates.filter { seg ->
            val dir = seg.directionFrom(nodeId)
            seg.supportsDirection(dir) && (trainCategory != TrainCategory.HEAVY_FREIGHT || seg.allowHeavyFreight)
        }
    }

    /**
     * Validates structural integrity of the graph (no dangling node refs, valid switch refs, positive lengths).
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        segments.values.forEach { seg ->
            if (seg.nodeA !in nodes) errors += "Segment ${seg.id} missing nodeA ${seg.nodeA}"
            if (seg.nodeB !in nodes) errors += "Segment ${seg.id} missing nodeB ${seg.nodeB}"
            if (seg.length <= 0f) errors += "Segment ${seg.id} has non-positive length"
            if (seg.nodeA == seg.nodeB) errors += "Segment ${seg.id} is a self-loop"
        }
        switches.values.forEach { sw ->
            if (sw.nodeId !in nodes) errors += "Switch ${sw.id} missing node ${sw.nodeId}"
            if (sw.stemSegmentId !in segments) errors += "Switch ${sw.id} missing stem ${sw.stemSegmentId}"
            if (sw.branchSegmentIds.isEmpty()) errors += "Switch ${sw.id} has no branches"
            sw.branchSegmentIds.forEach { bId ->
                if (bId !in segments) errors += "Switch ${sw.id} missing branch $bId"
            }
        }
        signals.values.forEach { sig ->
            if (sig.nodeId !in nodes) errors += "Signal ${sig.id} missing node ${sig.nodeId}"
            if (sig.facingFromSegmentId !in segments) errors += "Signal ${sig.id} missing facing segment ${sig.facingFromSegmentId}"
        }
        return errors
    }
}
