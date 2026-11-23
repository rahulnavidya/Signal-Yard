package com.example.sim

import java.util.ArrayDeque

object RoutePlanner {

    /**
     * Finds a structurally valid sequence of segments from [startSegmentId] (traversed in [startDir])
     * visiting all remaining [stopStationIds] in order and ending at [destinationNodeId].
     * Respects track directionality and train category restrictions.
     * Returns emptyList() if no valid route exists.
     */
    fun findRoute(
        graph: TrackGraph,
        startSegmentId: String,
        startDir: TraversalDir,
        remainingStops: List<String>,
        destinationNodeId: String,
        category: TrainCategory
    ): List<String> {
        val startSeg = graph.segments[startSegmentId] ?: return emptyList()
        if (!startSeg.supportsDirection(startDir)) return emptyList()
        if (category == TrainCategory.HEAVY_FREIGHT && !startSeg.allowHeavyFreight) return emptyList()

        data class SearchState(
            val segmentId: String,
            val dir: TraversalDir,
            val stopIndex: Int
        )

        val initialStopIdx = computeNextStopIndex(
            node = graph.nodes.getValue(startSeg.exitNodeFor(startDir)),
            remainingStops = remainingStops,
            currentIndex = 0
        )

        val startState = SearchState(startSegmentId, startDir, initialStopIdx)
        val queue = ArrayDeque<SearchState>()
        val parent = HashMap<SearchState, SearchState?>()
        queue.add(startState)
        parent[startState] = null

        var goalState: SearchState? = null

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val curSeg = graph.segments.getValue(current.segmentId)
            val exitNodeId = curSeg.exitNodeFor(current.dir)

            if (current.stopIndex >= remainingStops.size && exitNodeId == destinationNodeId) {
                goalState = current
                break
            }

            val nextSegs = graph.possibleNextSegments(exitNodeId, current.segmentId, category)
            for (nextSeg in nextSegs) {
                val nextDir = nextSeg.directionFrom(exitNodeId)
                val nextExitNode = graph.nodes.getValue(nextSeg.exitNodeFor(nextDir))
                val nextStopIdx = computeNextStopIndex(nextExitNode, remainingStops, current.stopIndex)
                val nextState = SearchState(nextSeg.id, nextDir, nextStopIdx)
                if (nextState !in parent) {
                    parent[nextState] = current
                    queue.add(nextState)
                }
            }
        }

        if (goalState == null) return emptyList()

        val result = ArrayList<String>()
        var cursor: SearchState? = goalState
        while (cursor != null) {
            result.add(cursor.segmentId)
            cursor = parent[cursor]
        }
        result.reverse()
        return result
    }

    private fun computeNextStopIndex(
        node: TrackNode,
        remainingStops: List<String>,
        currentIndex: Int
    ): Int {
        if (currentIndex < remainingStops.size && node.stationId == remainingStops[currentIndex]) {
            return currentIndex + 1
        }
        return currentIndex
    }

    data class AlignedStep(
        val segmentId: String,
        val dir: TraversalDir,
        val exitNodeId: String
    )

    /**
     * Traces the path a train would currently follow from [startSegmentId]/[startDir]
     * given the current switch positions. Stops if a trailing switch is set against the train
     * or a terminal node is reached.
     */
    fun traceAlignedPath(
        graph: TrackGraph,
        switches: Map<String, TrackSwitch>,
        startSegmentId: String,
        startDir: TraversalDir,
        maxSteps: Int = 14
    ): List<AlignedStep> {
        val seg0 = graph.segments[startSegmentId] ?: return emptyList()
        val steps = mutableListOf(AlignedStep(startSegmentId, startDir, seg0.exitNodeFor(startDir)))
        val visited = HashSet<Pair<String, TraversalDir>>()
        visited.add(startSegmentId to startDir)

        var curSeg = seg0
        var curDir = startDir
        repeat(maxSteps - 1) {
            val exitNodeId = curSeg.exitNodeFor(curDir)
            val exitNode = graph.nodes[exitNodeId] ?: return steps
            if (exitNode.type == NodeType.PORTAL_EXIT || exitNode.type == NodeType.BUFFER) {
                return steps
            }
            val nextSegId = graph.alignedNextSegment(exitNodeId, curSeg.id, switches) ?: return steps
            val nextSeg = graph.segments[nextSegId] ?: return steps
            val nextDir = nextSeg.directionFrom(exitNodeId)
            if (!nextSeg.supportsDirection(nextDir)) return steps
            if (!visited.add(nextSegId to nextDir)) return steps

            steps.add(AlignedStep(nextSegId, nextDir, nextSeg.exitNodeFor(nextDir)))
            curSeg = nextSeg
            curDir = nextDir
        }
        return steps
    }
}
