package com.example.sim

import kotlin.math.atan2
import kotlin.math.hypot

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)

    fun distanceTo(other: Vec2): Float = hypot(x - other.x, y - other.y)
}

data class Pose2D(
    val x: Float,
    val y: Float,
    val angleRadians: Float
)

/**
 * Arc-length parameterized curve for straight lines and quadratic Bezier track segments.
 * Ensures trains and carriages move at exact physical speeds around curves without stretching or bunching.
 */
class TrackCurve(
    val start: Vec2,
    val control: Vec2?,
    val end: Vec2
) {
    private val sampleCount = 32
    private val cumulativeLengths = FloatArray(sampleCount + 1)
    private val samplePoints = Array(sampleCount + 1) { start }
    val totalLength: Float

    init {
        var acc = 0f
        var prev = start
        samplePoints[0] = start
        cumulativeLengths[0] = 0f
        for (i in 1..sampleCount) {
            val t = i.toFloat() / sampleCount
            val pt = evalRaw(t)
            acc += prev.distanceTo(pt)
            samplePoints[i] = pt
            cumulativeLengths[i] = acc
            prev = pt
        }
        totalLength = acc.coerceAtLeast(1f)
    }

    private fun evalRaw(t: Float): Vec2 {
        return if (control == null) {
            Vec2(
                start.x + (end.x - start.x) * t,
                start.y + (end.y - start.y) * t
            )
        } else {
            val inv = 1f - t
            Vec2(
                inv * inv * start.x + 2f * inv * t * control.x + t * t * end.x,
                inv * inv * start.y + 2f * inv * t * control.y + t * t * end.y
            )
        }
    }

    fun sampleAtDistance(distance: Float, dir: TraversalDir): Pose2D {
        val clamped = distance.coerceIn(0f, totalLength)
        val forwardDist = if (dir == TraversalDir.A_TO_B) clamped else (totalLength - clamped)

        // Binary search on cumulativeLengths
        var low = 0
        var high = sampleCount
        while (low < high - 1) {
            val mid = (low + high) ushr 1
            if (cumulativeLengths[mid] <= forwardDist) {
                low = mid
            } else {
                high = mid
            }
        }

        val segStartLen = cumulativeLengths[low]
        val segEndLen = cumulativeLengths[high]
        val frac = if (segEndLen > segStartLen) {
            (forwardDist - segStartLen) / (segEndLen - segStartLen)
        } else {
            0f
        }

        val p0 = samplePoints[low]
        val p1 = samplePoints[high]
        val x = p0.x + (p1.x - p0.x) * frac
        val y = p0.y + (p1.y - p0.y) * frac

        val dx = p1.x - p0.x
        val dy = p1.y - p0.y
        val baseAngle = atan2(dy, dx)
        val angle = if (dir == TraversalDir.A_TO_B) baseAngle else (baseAngle + Math.PI.toFloat())
        return Pose2D(x, y, angle)
    }

    fun getPolylinePoints(): List<Vec2> = samplePoints.toList()
}
