package com.example.sim

enum class MedalRank(val displayName: String, val rankOrder: Int) {
    NONE("Incomplete", 0),
    BRONZE("Bronze", 1),
    SILVER("Silver", 2),
    GOLD("Gold", 3)
}

data class SessionScoreBreakdown(
    val baseDeliveryScore: Int,
    val punctualityBonus: Int,
    val streakBonus: Int,
    val safetyBonus: Int,
    val delayPenalty: Int,
    val finalScore: Int,
    val onTimePercent: Int,
    val medal: MedalRank,
    val gradeLabel: String
)

object ScoreManager {

    fun calculateSessionResult(
        deliveredCount: Int,
        onTimeDeliveredCount: Int,
        bestStreak: Int,
        safetyIncidents: Int,
        totalDelaySeconds: Float,
        goldTargetScore: Int,
        silverTargetScore: Int,
        bronzeTargetScore: Int,
        allRequiredDelivered: Boolean = true
    ): SessionScoreBreakdown {
        val baseDelivery = deliveredCount * 200
        val punctuality = onTimeDeliveredCount * 100
        val streak = bestStreak * 40
        val safety = if (safetyIncidents == 0 && deliveredCount > 0) 250 else 0
        val penalty = (totalDelaySeconds * 3f).toInt().coerceAtMost(baseDelivery / 2)

        val finalScore = (baseDelivery + punctuality + streak + safety - penalty).coerceAtLeast(0)
        val onTimePct = if (deliveredCount > 0) {
            ((onTimeDeliveredCount.toFloat() / deliveredCount.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else {
            0
        }

        val medal = when {
            !allRequiredDelivered || deliveredCount == 0 -> MedalRank.NONE
            safetyIncidents == 0 && finalScore >= goldTargetScore -> MedalRank.GOLD
            finalScore >= silverTargetScore -> MedalRank.SILVER
            finalScore >= bronzeTargetScore -> MedalRank.BRONZE
            else -> MedalRank.BRONZE
        }

        val grade = when {
            safetyIncidents > 0 -> "C"
            medal == MedalRank.GOLD && onTimePct >= 90 -> "S"
            medal == MedalRank.GOLD -> "A+"
            medal == MedalRank.SILVER -> "A"
            medal == MedalRank.BRONZE -> "B"
            else -> "C"
        }

        return SessionScoreBreakdown(
            baseDeliveryScore = baseDelivery,
            punctualityBonus = punctuality,
            streakBonus = streak,
            safetyBonus = safety,
            delayPenalty = penalty,
            finalScore = finalScore,
            onTimePercent = onTimePct,
            medal = medal,
            gradeLabel = grade
        )
    }
}
