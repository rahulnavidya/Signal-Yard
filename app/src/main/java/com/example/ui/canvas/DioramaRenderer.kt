package com.example.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.sim.*
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class CameraState(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f
)

private data class CachedSegmentGeometry(
    val segment: TrackSegment,
    val centerPath: Path,
    val sleepers: List<Pair<Offset, Offset>>,
    val midpoint: Offset,
    val midAngleDeg: Float
)

private data class CachedTree(
    val x: Float,
    val y: Float,
    val radius: Float,
    val shade: Color
)

private class MapGeometryCache(val graph: TrackGraph) {
    val segmentGeometries: Map<String, CachedSegmentGeometry>
    val riverPaths: List<Pair<Path, Float>>
    val trees: List<CachedTree>

    init {
        segmentGeometries = graph.segments.mapValues { (segId, seg) ->
            val curve = graph.curveOf(segId)
            val pts = curve.getPolylinePoints()
            val path = Path().apply {
                if (pts.isNotEmpty()) {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) {
                        lineTo(pts[i].x, pts[i].y)
                    }
                }
            }
            val sleepers = mutableListOf<Pair<Offset, Offset>>()
            val sleeperStep = 14f
            var d = 6f
            while (d < curve.totalLength - 4f) {
                val pose = curve.sampleAtDistance(d, TraversalDir.A_TO_B)
                val nx = -sin(pose.angleRadians) * 8.5f
                val ny = cos(pose.angleRadians) * 8.5f
                sleepers.add(
                    Offset(pose.x - nx, pose.y - ny) to Offset(pose.x + nx, pose.y + ny)
                )
                d += sleeperStep
            }
            val mid = curve.sampleAtDistance(curve.totalLength * 0.5f, TraversalDir.A_TO_B)
            CachedSegmentGeometry(
                segment = seg,
                centerPath = path,
                sleepers = sleepers,
                midpoint = Offset(mid.x, mid.y),
                midAngleDeg = (mid.angleRadians * 180f / PI.toFloat())
            )
        }

        riverPaths = graph.props.filterIsInstance<DioramaProp.River>().map { river ->
            val path = Path().apply {
                if (river.points.isNotEmpty()) {
                    moveTo(river.points.first().x, river.points.first().y)
                    for (i in 1 until river.points.size - 1) {
                        val curr = river.points[i]
                        val next = river.points[i + 1]
                        quadraticTo(curr.x, curr.y, (curr.x + next.x) * 0.5f, (curr.y + next.y) * 0.5f)
                    }
                    if (river.points.size > 1) {
                        val last = river.points.last()
                        lineTo(last.x, last.y)
                    }
                }
            }
            path to river.width
        }

        val generatedTrees = mutableListOf<CachedTree>()
        val shades = listOf(Color(0xFF274232), Color(0xFF314B3B), Color(0xFF3E6147), Color(0xFF4F7552))
        graph.props.filterIsInstance<DioramaProp.TreeCluster>().forEach { cluster ->
            val rng = Random(cluster.seed)
            repeat(cluster.count) {
                val angle = rng.nextFloat() * 2f * PI.toFloat()
                val dist = rng.nextFloat() * 64f
                val tx = cluster.x + cos(angle) * dist
                val ty = cluster.y + sin(angle) * dist
                val radius = 11f + rng.nextFloat() * 9f
                generatedTrees += CachedTree(tx, ty, radius, shades[rng.nextInt(shades.size)])
            }
        }
        trees = generatedTrees
    }
}

sealed class DioramaTapTarget {
    data class Switch(val switchId: String) : DioramaTapTarget()
    data class Signal(val signalId: String) : DioramaTapTarget()
    data class Train(val trainId: String) : DioramaTapTarget()
    data class Station(val stationId: String) : DioramaTapTarget()
    data object Empty : DioramaTapTarget()
}

@Composable
fun DioramaCanvas(
    graph: TrackGraph,
    snapshot: SimulationSnapshot,
    camera: CameraState,
    selectedTrainId: String?,
    selectedStationId: String?,
    colorblindSymbols: Boolean,
    showRouteGuidance: Boolean,
    onCameraChange: (CameraState) -> Unit,
    onTapTarget: (DioramaTapTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val cache = remember(graph.id) { MapGeometryCache(graph) }
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(graph.id, camera) {
                detectTransformGestures { centroid, pan, zoomChange, _ ->
                    val newZoom = (camera.zoom * zoomChange).coerceIn(0.65f, 2.5f)
                    val baseScale = min(
                        size.width / graph.worldWidth,
                        size.height / graph.worldHeight
                    ) * 0.94f
                    val oldEffectiveScale = baseScale * camera.zoom
                    val newEffectiveScale = baseScale * newZoom

                    // Keep zoom centered under pinch centroid
                    val worldAtCentroidX = (centroid.x - size.width * 0.5f - camera.panX) / oldEffectiveScale
                    val worldAtCentroidY = (centroid.y - size.height * 0.5f - camera.panY) / oldEffectiveScale

                    val nextPanX = centroid.x - size.width * 0.5f - worldAtCentroidX * newEffectiveScale + pan.x
                    val nextPanY = centroid.y - size.height * 0.5f - worldAtCentroidY * newEffectiveScale + pan.y

                    val maxPanX = graph.worldWidth * newEffectiveScale * 0.55f
                    val maxPanY = graph.worldHeight * newEffectiveScale * 0.55f

                    onCameraChange(
                        CameraState(
                            panX = nextPanX.coerceIn(-maxPanX, maxPanX),
                            panY = nextPanY.coerceIn(-maxPanY, maxPanY),
                            zoom = newZoom
                        )
                    )
                }
            }
            .pointerInput(graph.id, snapshot, camera) {
                detectTapGestures { tapOffset ->
                    val baseScale = min(
                        size.width / graph.worldWidth,
                        size.height / graph.worldHeight
                    ) * 0.94f
                    val effectiveScale = baseScale * camera.zoom
                    val worldX = (tapOffset.x - size.width * 0.5f - camera.panX) / effectiveScale + graph.worldWidth * 0.5f
                    val worldY = (tapOffset.y - size.height * 0.5f - camera.panY) / effectiveScale + graph.worldHeight * 0.5f
                    val target = hitTestWorld(graph, snapshot, worldX, worldY)
                    onTapTarget(target)
                }
            }
    ) {
        // Outer workbench surface behind the wooden railway table
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF282E2B), Color(0xFF1A1F1D)),
                center = center,
                radius = maxOf(size.width, size.height) * 0.75f
            )
        )

        val baseScale = min(
            size.width / graph.worldWidth,
            size.height / graph.worldHeight
        ) * 0.94f
        val effectiveScale = baseScale * camera.zoom
        val originX = size.width * 0.5f + camera.panX - (graph.worldWidth * 0.5f) * effectiveScale
        val originY = size.height * 0.5f + camera.panY - (graph.worldHeight * 0.5f) * effectiveScale

        withTransform({
            translate(left = originX, top = originY)
            scale(scaleX = effectiveScale, scaleY = effectiveScale, pivot = Offset.Zero)
        }) {
            drawDioramaTableBase(graph)
            drawHillsAndRivers(graph, cache, snapshot.simTimeSeconds)
            drawTracksAndPlatforms(graph, cache, snapshot, selectedTrainId, showRouteGuidance)
            drawBridgesAndBuildings(graph, textMeasurer, selectedStationId)
            drawTrees(cache)
            drawSwitchLevers(graph, snapshot, textMeasurer)
            drawSignals(graph, snapshot, colorblindSymbols, textMeasurer)
            drawTrains(graph, snapshot, selectedTrainId, textMeasurer)
        }
    }
}

private fun hitTestWorld(
    graph: TrackGraph,
    snapshot: SimulationSnapshot,
    wx: Float,
    wy: Float
): DioramaTapTarget {
    // 1. Switches (highest operational priority)
    val hitSwitch = snapshot.switches.values.minByOrNull { sw ->
        val node = graph.nodes.getValue(sw.nodeId)
        hypot(wx - node.x, wy - node.y)
    }
    if (hitSwitch != null) {
        val node = graph.nodes.getValue(hitSwitch.nodeId)
        if (hypot(wx - node.x, wy - node.y) <= 50f) {
            return DioramaTapTarget.Switch(hitSwitch.id)
        }
    }

    // 2. Trains (check any carriage or locomotive)
    for (train in snapshot.trains) {
        val poses = snapshot.trainPoses[train.id].orEmpty()
        for (pose in poses) {
            if (hypot(wx - pose.x, wy - pose.y) <= 42f) {
                return DioramaTapTarget.Train(train.id)
            }
        }
    }

    // 3. Signals (offset slightly from node so test matches drawn post)
    for (sig in snapshot.signals.values) {
        val pos = computeSignalPostPosition(graph, sig)
        if (hypot(wx - pos.x, wy - pos.y) <= 38f) {
            return DioramaTapTarget.Signal(sig.id)
        }
    }

    // 4. Stations
    for (station in graph.stations.values) {
        if (hypot(wx - station.x, wy - station.y) <= 68f) {
            return DioramaTapTarget.Station(station.id)
        }
    }

    return DioramaTapTarget.Empty
}

private fun computeSignalPostPosition(graph: TrackGraph, sig: RailwaySignal): Offset {
    val node = graph.nodes.getValue(sig.nodeId)
    val seg = graph.segments.getValue(sig.facingFromSegmentId)
    val otherNode = graph.nodes.getValue(seg.otherNode(sig.nodeId))
    val dx = node.x - otherNode.x
    val dy = node.y - otherNode.y
    val len = hypot(dx, dy).coerceAtLeast(1f)
    val ux = dx / len
    val uy = dy / len
    // Place signal 26u back from node and 22u to the right side of incoming direction
    val px = node.x - ux * 26f - uy * 22f
    val py = node.y - uy * 26f + ux * 22f
    return Offset(px, py)
}

private fun DrawScope.drawDioramaTableBase(graph: TrackGraph) {
    val w = graph.worldWidth
    val h = graph.worldHeight

    // Tabletop drop shadow
    drawRoundRect(
        color = Color(0x66000000),
        topLeft = Offset(10f, 16f),
        size = Size(w, h),
        cornerRadius = CornerRadius(24f, 24f)
    )

    // Mahogany diorama frame
    drawRoundRect(
        color = DioramaWoodFrame,
        topLeft = Offset(-16f, -16f),
        size = Size(w + 32f, h + 32f),
        cornerRadius = CornerRadius(28f, 28f)
    )
    drawRoundRect(
        color = BrassGoldDark,
        topLeft = Offset(-16f, -16f),
        size = Size(w + 32f, h + 32f),
        cornerRadius = CornerRadius(28f, 28f),
        style = Stroke(width = 3f)
    )

    // Meadow base felt with warm sunlit radial glow
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF567855), DioramaMeadow, Color(0xFF3E5B42)),
            center = Offset(w * 0.48f, h * 0.45f),
            radius = w * 0.72f
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = CornerRadius(14f, 14f)
    )

    // Subtle railway atlas survey grid lines
    val gridColor = Color(0x14E3E0D2)
    var gx = 100f
    while (gx < w) {
        drawLine(gridColor, Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
        gx += 100f
    }
    var gy = 100f
    while (gy < h) {
        drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
        gy += 100f
    }

    // Brass corner brackets
    val cornerLen = 36f
    val bracketStroke = Stroke(width = 4f, cap = StrokeCap.Round)
    val corners = listOf(
        Offset(12f, 12f) to (1f to 1f),
        Offset(w - 12f, 12f) to (-1f to 1f),
        Offset(12f, h - 12f) to (1f to -1f),
        Offset(w - 12f, h - 12f) to (-1f to -1f)
    )
    corners.forEach { (pt, dir) ->
        drawLine(BrassGold, pt, Offset(pt.x + dir.first * cornerLen, pt.y), strokeWidth = 4f, cap = StrokeCap.Round)
        drawLine(BrassGold, pt, Offset(pt.x, pt.y + dir.second * cornerLen), strokeWidth = 4f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawHillsAndRivers(
    graph: TrackGraph,
    cache: MapGeometryCache,
    simTime: Float
) {
    // Sculpted layered plaster hills
    graph.props.filterIsInstance<DioramaProp.Hill>().forEach { hill ->
        for (layer in 0 until hill.elevation) {
            val shrink = 1f - layer * 0.24f
            val rx = hill.rx * shrink
            val ry = hill.ry * shrink
            val layerColor = when (layer) {
                0 -> Color(0xFF426246)
                1 -> Color(0xFF4E7251)
                else -> Color(0xFF5D835C)
            }
            // Shadow rim
            drawOval(
                color = Color(0x2E1A2A1F),
                topLeft = Offset(hill.x - rx + 6f, hill.y - ry + 9f),
                size = Size(rx * 2f, ry * 2f)
            )
            drawOval(
                color = layerColor,
                topLeft = Offset(hill.x - rx, hill.y - ry),
                size = Size(rx * 2f, ry * 2f)
            )
            drawOval(
                color = Color(0x33D5C8A3),
                topLeft = Offset(hill.x - rx, hill.y - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = 1.5f)
            )
        }
    }

    // Winding rivers
    cache.riverPaths.forEach { (path, width) ->
        drawPath(
            path = path,
            color = Color(0xFF5D5748),
            style = Stroke(width = width + 14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = path,
            color = DioramaRiver,
            style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        val phase = (simTime * 18f) % 48f
        drawPath(
            path = path,
            color = DioramaRiverHighlight.copy(alpha = 0.45f),
            style = Stroke(
                width = width * 0.28f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 30f), phase)
            )
        )
    }
}

private fun DrawScope.drawTracksAndPlatforms(
    graph: TrackGraph,
    cache: MapGeometryCache,
    snapshot: SimulationSnapshot,
    selectedTrainId: String?,
    showRouteGuidance: Boolean
) {
    val selectedTrain = snapshot.trains.firstOrNull { it.id == selectedTrainId }
    val selectedRouteSet = selectedTrain?.intendedRouteSegments?.toSet().orEmpty()

    // 1. Stone platforms alongside PLATFORM segments
    cache.segmentGeometries.values.forEach { geom ->
        if (geom.segment.category == TrackCategory.PLATFORM) {
            drawPath(
                path = geom.centerPath,
                color = Color(0x441B231E),
                style = Stroke(width = 48f, cap = StrokeCap.Butt)
            )
            drawPath(
                path = geom.centerPath,
                color = DioramaPlatformStone,
                style = Stroke(width = 42f, cap = StrokeCap.Butt)
            )
            drawPath(
                path = geom.centerPath,
                color = BrassGold,
                style = Stroke(width = 34f, cap = StrokeCap.Butt)
            )
        }
    }

    // 2. Crushed granite ballast & tunnel casing
    cache.segmentGeometries.values.forEach { geom ->
        val ballastWidth = when (geom.segment.category) {
            TrackCategory.TUNNEL -> 30f
            TrackCategory.VIADUCT -> 26f
            else -> 25f
        }
        val ballastColor = if (geom.segment.category == TrackCategory.TUNNEL) {
            Color(0xFF2B2F33)
        } else {
            DioramaBallast
        }
        drawPath(
            path = geom.centerPath,
            color = ballastColor,
            style = Stroke(width = ballastWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    // 3. Wooden sleepers perpendicular to track
    cache.segmentGeometries.values.forEach { geom ->
        geom.sleepers.forEach { (a, b) ->
            drawLine(
                color = DioramaSleeper,
                start = a,
                end = b,
                strokeWidth = 3.2f,
                cap = StrokeCap.Round
            )
        }
    }

    // 4. Twin steel rails (rendered via wide outer stroke + inner dark groove)
    cache.segmentGeometries.values.forEach { geom ->
        val isRestricted = !geom.segment.allowHeavyFreight
        drawPath(
            path = geom.centerPath,
            color = if (isRestricted) Color(0xFFE6DFCF) else DioramaRailSteel,
            style = Stroke(width = 11.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = geom.centerPath,
            color = Color(0xFF3E352D),
            style = Stroke(width = 6.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    // 5. Route reservations & active occupancy overlays
    cache.segmentGeometries.forEach { (segId, geom) ->
        val isOccupied = snapshot.occupiedSegmentIds[segId]?.isNotEmpty() == true
        val isReserved = segId in snapshot.reservedSegmentIds
        val inSelectedRoute = segId in selectedRouteSet

        if (inSelectedRoute && showRouteGuidance) {
            drawPath(
                path = geom.centerPath,
                color = SignalGreen.copy(alpha = 0.72f),
                style = Stroke(
                    width = 5f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), -(snapshot.simTimeSeconds * 38f) % 24f)
                )
            )
        } else if (isOccupied) {
            drawPath(
                path = geom.centerPath,
                color = SignalRed.copy(alpha = 0.60f),
                style = Stroke(width = 4.5f, cap = StrokeCap.Round)
            )
        } else if (isReserved) {
            drawPath(
                path = geom.centerPath,
                color = BrassGold.copy(alpha = 0.55f),
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
        }
    }

    // 6. Active turnout path highlight at each switch
    snapshot.switches.values.forEach { sw ->
        val branchGeom = cache.segmentGeometries[sw.activeBranchSegmentId]
        val node = graph.nodes.getValue(sw.nodeId)
        if (branchGeom != null) {
            val curve = graph.curveOf(sw.activeBranchSegmentId)
            val fromA = branchGeom.segment.nodeA == sw.nodeId
            val p0 = node.position
            val p1 = curve.sampleAtDistance(
                if (fromA) min(38f, curve.totalLength) else (curve.totalLength - min(38f, curve.totalLength)),
                TraversalDir.A_TO_B
            )
            drawLine(
                color = if (sw.isLocked) SignalRedBright else BrassGoldBright,
                start = Offset(p0.x, p0.y),
                end = Offset(p1.x, p1.y),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawBridgesAndBuildings(
    graph: TrackGraph,
    textMeasurer: TextMeasurer,
    selectedStationId: String?
) {
    // Stone viaduct bridge parapets
    graph.props.filterIsInstance<DioramaProp.BridgeDeck>().forEach { bridge ->
        val dx = bridge.x2 - bridge.x1
        val dy = bridge.y2 - bridge.y1
        val len = hypot(dx, dy)
        val angleDeg = atan2(dy, dx) * 180f / PI.toFloat()
        val cx = (bridge.x1 + bridge.x2) * 0.5f
        val cy = (bridge.y1 + bridge.y2) * 0.5f

        rotate(degrees = angleDeg, pivot = Offset(cx, cy)) {
            val halfW = bridge.width * 0.5f
            // Parapet walls
            drawRoundRect(
                color = WarmSandstoneDark,
                topLeft = Offset(cx - len * 0.5f, cy - halfW - 5f),
                size = Size(len, 6f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            drawRoundRect(
                color = WarmSandstoneDark,
                topLeft = Offset(cx - len * 0.5f, cy + halfW - 1f),
                size = Size(len, 6f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            // Stone buttress piers
            var px = cx - len * 0.42f
            while (px <= cx + len * 0.45f) {
                drawRect(
                    color = WarmSandstoneLight,
                    topLeft = Offset(px - 4f, cy - halfW - 7f),
                    size = Size(8f, 9f)
                )
                drawRect(
                    color = WarmSandstoneLight,
                    topLeft = Offset(px - 4f, cy + halfW - 2f),
                    size = Size(8f, 9f)
                )
                px += 32f
            }
        }
    }

    // Miniature buildings
    graph.props.filterIsInstance<DioramaProp.Building>().forEach { b ->
        rotate(degrees = b.angleDeg, pivot = Offset(b.x, b.y)) {
            val left = b.x - b.width * 0.5f
            val top = b.y - b.height * 0.5f
            // Ground shadow
            drawRoundRect(
                color = Color(0x40121815),
                topLeft = Offset(left + 5f, top + 6f),
                size = Size(b.width, b.height),
                cornerRadius = CornerRadius(5f, 5f)
            )
            when (b.style) {
                BuildingStyle.STATION_HOUSE -> {
                    drawRoundRect(
                        color = WarmIvory,
                        topLeft = Offset(left, top),
                        size = Size(b.width, b.height),
                        cornerRadius = CornerRadius(5f, 5f)
                    )
                    // Terracotta roof ridge
                    drawRoundRect(
                        color = Color(0xFF9E4E3D),
                        topLeft = Offset(left + 3f, top + 3f),
                        size = Size(b.width - 6f, b.height - 6f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawLine(
                        color = Color(0xFFD59B82),
                        start = Offset(left + 10f, b.y),
                        end = Offset(left + b.width - 10f, b.y),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }
                BuildingStyle.SIGNAL_BOX -> {
                    drawRoundRect(
                        color = ForestGreen,
                        topLeft = Offset(left, top),
                        size = Size(b.width, b.height),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawRoundRect(
                        color = WarmIvory,
                        topLeft = Offset(left + 3f, top + 3f),
                        size = Size(b.width - 6f, b.height - 6f),
                        cornerRadius = CornerRadius(3f, 3f),
                        style = Stroke(width = 2f)
                    )
                }
                BuildingStyle.WAREHOUSE -> {
                    drawRoundRect(
                        color = GraphiteSlate,
                        topLeft = Offset(left, top),
                        size = Size(b.width, b.height),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    var ribX = left + 12f
                    while (ribX < left + b.width - 6f) {
                        drawLine(Color(0x33E3E0D2), Offset(ribX, top + 4f), Offset(ribX, top + b.height - 4f), 2f)
                        ribX += 14f
                    }
                }
                BuildingStyle.COTTAGE -> {
                    drawRoundRect(
                        color = WarmSandstoneLight,
                        topLeft = Offset(left, top),
                        size = Size(b.width, b.height),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawRoundRect(
                        color = Color(0xFF7A4B3A),
                        topLeft = Offset(left + 2f, top + 2f),
                        size = Size(b.width - 4f, b.height - 4f),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                }
                BuildingStyle.WATER_TOWER -> {
                    drawCircle(
                        color = Color(0xFF6B5340),
                        radius = b.width * 0.45f,
                        center = Offset(b.x, b.y)
                    )
                    drawCircle(
                        color = BrassGoldDark,
                        radius = b.width * 0.36f,
                        center = Offset(b.x, b.y)
                    )
                }
            }
        }
    }

    // Station enamel plaques
    graph.stations.values.forEach { st ->
        val isSelected = st.id == selectedStationId
        val labelLayout = textMeasurer.measure(
            text = "${st.code} • ${st.name.uppercase()}",
            style = TextStyle(
                color = WarmIvoryPaper,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp
            )
        )
        val padH = 10f
        val padV = 4f
        val boxW = labelLayout.size.width + padH * 2f
        val boxH = labelLayout.size.height + padV * 2f
        val bx = st.x - boxW * 0.5f
        val by = st.y - boxH * 0.5f

        drawRoundRect(
            color = ForestGreenDeep.copy(alpha = 0.92f),
            topLeft = Offset(bx, by),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = if (isSelected) BrassGoldBright else BrassGold,
            topLeft = Offset(bx, by),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = if (isSelected) 2.5f else 1.5f)
        )
        drawText(
            textLayoutResult = labelLayout,
            topLeft = Offset(bx + padH, by + padV)
        )
    }

    // Portal entry/exit arrowheads
    graph.nodes.values.filter {
        it.type == NodeType.PORTAL_ENTRY || it.type == NodeType.PORTAL_EXIT || it.type == NodeType.PORTAL_BIDIRECTIONAL
    }.forEach { portal ->
        drawCircle(
            color = GraphiteDeep,
            radius = 12f,
            center = Offset(portal.x, portal.y)
        )
        drawCircle(
            color = BrassGold,
            radius = 12f,
            center = Offset(portal.x, portal.y),
            style = Stroke(width = 2.5f)
        )
        portal.label?.let { lbl ->
            val measured = textMeasurer.measure(
                text = lbl,
                style = TextStyle(color = WarmIvory, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
            )
            val tx = (portal.x - measured.size.width * 0.5f).coerceIn(8f, graph.worldWidth - measured.size.width - 8f)
            val ty = portal.y + 16f
            drawText(measured, topLeft = Offset(tx, ty))
        }
    }
}

private fun DrawScope.drawTrees(cache: MapGeometryCache) {
    cache.trees.forEach { t ->
        drawCircle(
            color = Color(0x36142018),
            radius = t.radius,
            center = Offset(t.x + 4f, t.y + 5f)
        )
        drawCircle(
            color = t.shade,
            radius = t.radius,
            center = Offset(t.x, t.y)
        )
        drawCircle(
            color = Color(0x33D5C8A3),
            radius = t.radius * 0.42f,
            center = Offset(t.x - t.radius * 0.22f, t.y - t.radius * 0.22f)
        )
    }
}

private fun DrawScope.drawSwitchLevers(
    graph: TrackGraph,
    snapshot: SimulationSnapshot,
    textMeasurer: TextMeasurer
) {
    snapshot.switches.values.forEach { sw ->
        val node = graph.nodes.getValue(sw.nodeId)
        val badgeColor = when {
            sw.isLocked -> SignalRed
            sw.activeBranchIndex == 0 -> ForestGreenDeep
            else -> BrassGoldDark
        }
        val borderColor = if (sw.isLocked) SignalRedBright else BrassGoldBright

        // Tactile switch lever disc
        drawCircle(
            color = Color(0x4D000000),
            radius = 21f,
            center = Offset(node.x + 2f, node.y + 24f)
        )
        drawCircle(
            color = badgeColor,
            radius = 20f,
            center = Offset(node.x, node.y + 22f)
        )
        drawCircle(
            color = borderColor,
            radius = 20f,
            center = Offset(node.x, node.y + 22f),
            style = Stroke(width = 2.4f)
        )

        val lbl = textMeasurer.measure(
            text = if (sw.isLocked) "LOCK" else sw.label,
            style = TextStyle(
                color = WarmIvoryPaper,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold
            )
        )
        drawText(
            textLayoutResult = lbl,
            topLeft = Offset(node.x - lbl.size.width * 0.5f, node.y + 22f - lbl.size.height * 0.5f)
        )
    }
}

private fun DrawScope.drawSignals(
    graph: TrackGraph,
    snapshot: SimulationSnapshot,
    colorblindSymbols: Boolean,
    textMeasurer: TextMeasurer
) {
    snapshot.signals.values.forEach { sig ->
        val postPos = computeSignalPostPosition(graph, sig)
        val aspectColor = when (sig.aspect) {
            SignalAspect.GREEN -> SignalGreen
            SignalAspect.YELLOW -> SignalAmber
            SignalAspect.RED -> SignalRed
        }

        // Glow halo
        drawCircle(
            color = aspectColor.copy(alpha = 0.28f),
            radius = 19f,
            center = postPos
        )
        // Signal housing
        drawCircle(
            color = GraphiteDeep,
            radius = 13f,
            center = postPos
        )
        drawCircle(
            color = if (sig.manualHoldRed) SignalRedBright else BrassGold,
            radius = 13f,
            center = postPos,
            style = Stroke(width = 2f)
        )
        // Aspect lens
        drawCircle(
            color = aspectColor,
            radius = 7.5f,
            center = postPos
        )

        if (colorblindSymbols) {
            val symbol = when (sig.aspect) {
                SignalAspect.GREEN -> "●"
                SignalAspect.YELLOW -> "▲"
                SignalAspect.RED -> "■"
            }
            val symLayout = textMeasurer.measure(
                text = symbol,
                style = TextStyle(color = GraphiteDeep, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            )
            drawText(
                symLayout,
                topLeft = Offset(postPos.x - symLayout.size.width * 0.5f, postPos.y - symLayout.size.height * 0.5f)
            )
        }

        if (sig.manualHoldRed) {
            val holdLbl = textMeasurer.measure(
                text = "HOLD",
                style = TextStyle(color = SignalRedBright, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
            )
            drawText(
                holdLbl,
                topLeft = Offset(postPos.x - holdLbl.size.width * 0.5f, postPos.y - 22f)
            )
        }
    }
}

private fun DrawScope.drawTrains(
    graph: TrackGraph,
    snapshot: SimulationSnapshot,
    selectedTrainId: String?,
    textMeasurer: TextMeasurer
) {
    snapshot.trains.forEach { train ->
        val poses = snapshot.trainPoses[train.id].orEmpty()
        if (poses.isEmpty()) return@forEach
        val isSelected = train.id == selectedTrainId

        val (bodyColor, trimColor, roofColor) = when (train.category) {
            TrainCategory.LOCAL_PASSENGER -> Triple(ForestGreen, WarmIvory, Color(0xFF223328))
            TrainCategory.EXPRESS_PASSENGER -> Triple(Color(0xFFB3382E), BrassGoldBright, Color(0xFF422220))
            TrainCategory.FREIGHT -> Triple(Color(0xFF5A534B), Color(0xFFD99B43), Color(0xFF363330))
            TrainCategory.HEAVY_FREIGHT -> Triple(Color(0xFF393E46), Color(0xFFC7663C), Color(0xFF22252A))
            TrainCategory.MAINTENANCE -> Triple(Color(0xFFD9A326), GraphiteDeep, Color(0xFF343A40))
        }

        // Draw carriages from tail to head so locomotive renders cleanly on top
        for (i in poses.indices.reversed()) {
            val pose = poses[i]
            val unitW = if (pose.isLocomotive) 30f else 26f
            val unitH = if (pose.isLocomotive) 15f else 13.5f
            val deg = pose.angleRadians * 180f / PI.toFloat()

            rotate(degrees = deg, pivot = Offset(pose.x, pose.y)) {
                // Headlight beam for locomotive
                if (pose.isLocomotive && train.status != TrainStatus.WAITING_SIGNAL) {
                    val beam = Path().apply {
                        moveTo(pose.x + unitW * 0.45f, pose.y)
                        lineTo(pose.x + unitW * 0.5f + 42f, pose.y - 14f)
                        lineTo(pose.x + unitW * 0.5f + 42f, pose.y + 14f)
                        close()
                    }
                    drawPath(
                        path = beam,
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0x66FFF1B8), Color.Transparent),
                            startX = pose.x + unitW * 0.45f,
                            endX = pose.x + unitW * 0.5f + 42f
                        )
                    )
                }

                // Ground drop shadow
                drawRoundRect(
                    color = Color(0x55000000),
                    topLeft = Offset(pose.x - unitW * 0.5f + 2f, pose.y - unitH * 0.5f + 4f),
                    size = Size(unitW, unitH),
                    cornerRadius = CornerRadius(4f, 4f)
                )

                // Selection halo
                if (isSelected) {
                    drawRoundRect(
                        color = BrassGoldBright,
                        topLeft = Offset(pose.x - unitW * 0.5f - 3.5f, pose.y - unitH * 0.5f - 3.5f),
                        size = Size(unitW + 7f, unitH + 7f),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(width = 2.5f)
                    )
                }

                // Unit body
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(pose.x - unitW * 0.5f, pose.y - unitH * 0.5f),
                    size = Size(unitW, unitH),
                    cornerRadius = CornerRadius(if (pose.isLocomotive) 5f else 3f, if (pose.isLocomotive) 5f else 3f)
                )

                // Roof / livery stripe
                drawRoundRect(
                    color = if (pose.isLocomotive) roofColor else trimColor.copy(alpha = 0.85f),
                    topLeft = Offset(pose.x - unitW * 0.36f, pose.y - unitH * 0.25f),
                    size = Size(unitW * 0.72f, unitH * 0.5f),
                    cornerRadius = CornerRadius(2f, 2f)
                )

                // Locomotive nose brass trim & cab window
                if (pose.isLocomotive) {
                    drawRect(
                        color = trimColor,
                        topLeft = Offset(pose.x + unitW * 0.22f, pose.y - unitH * 0.42f),
                        size = Size(4f, unitH * 0.84f)
                    )
                    // Express brass stripe
                    drawLine(
                        color = trimColor,
                        start = Offset(pose.x - unitW * 0.42f, pose.y),
                        end = Offset(pose.x + unitW * 0.2f, pose.y),
                        strokeWidth = 2f
                    )
                }
            }
        }

        // Floating service badge above locomotive
        val head = poses.first()
        val badgeBg = when {
            train.status == TrainStatus.WAITING_SIGNAL -> SignalRed
            train.status == TrainStatus.BOARDING -> SignalAmber
            isSelected -> BrassGold
            else -> GraphiteDeep.copy(alpha = 0.90f)
        }
        val textColor = if (badgeBg == BrassGold || badgeBg == SignalAmber) GraphiteDeep else WarmIvoryPaper
        val badgeText = textMeasurer.measure(
            text = train.serviceNumber,
            style = TextStyle(color = textColor, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        )
        val bw = badgeText.size.width + 10f
        val bh = badgeText.size.height + 4f
        val bx = head.x - bw * 0.5f
        val by = head.y - 30f

        drawRoundRect(
            color = badgeBg,
            topLeft = Offset(bx, by),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(5f, 5f)
        )
        drawRoundRect(
            color = BrassGold,
            topLeft = Offset(bx, by),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(5f, 5f),
            style = Stroke(width = 1.2f)
        )
        drawText(badgeText, topLeft = Offset(bx + 5f, by + 2f))

        // Station boarding progress arc
        if (train.status == TrainStatus.BOARDING && train.category.dwellSeconds > 0f) {
            val pct = 1f - (train.dwellRemaining / train.category.dwellSeconds).coerceIn(0f, 1f)
            drawArc(
                color = SignalGreen,
                startAngle = -90f,
                sweepAngle = 360f * pct,
                useCenter = false,
                topLeft = Offset(head.x - 19f, head.y - 19f),
                size = Size(38f, 38f),
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
        }
    }
}
