package com.example.data

import com.example.scenarios.GameMode
import com.example.sim.MedalRank
import com.example.sim.SessionScoreBreakdown
import kotlinx.coroutines.flow.Flow

class GameRecordsRepository(private val dao: SignalYardDao) {

    val scenarioRecords: Flow<List<ScenarioRecordEntity>> = dao.observeScenarioRecords()
    val recentSessions: Flow<List<SessionRecordEntity>> = dao.observeRecentSessions()
    val totalTrainsDelivered: Flow<Int> = dao.observeTotalTrainsDelivered()
    val bestEverStreak: Flow<Int> = dao.observeBestEverStreak()
    val rushHourHighScore: Flow<Int> = dao.observeHighScoreForMode(GameMode.RUSH_HOUR.name)

    suspend fun recordScenarioCompletion(
        scenarioId: String,
        mode: GameMode,
        mapName: String,
        breakdown: SessionScoreBreakdown,
        trainsDelivered: Int,
        bestStreak: Int,
        safetyIncidents: Int,
        durationSeconds: Int
    ): Boolean {
        val now = System.currentTimeMillis()
        val existing = dao.getScenarioRecord(scenarioId)
        val isNewBest = existing == null || breakdown.finalScore > existing.bestScore

        val updated = ScenarioRecordEntity(
            scenarioId = scenarioId,
            bestScore = maxOf(existing?.bestScore ?: 0, breakdown.finalScore),
            bestMedalOrdinal = maxOf(existing?.bestMedalOrdinal ?: MedalRank.NONE.rankOrder, breakdown.medal.rankOrder),
            bestOnTimePercent = maxOf(existing?.bestOnTimePercent ?: 0, breakdown.onTimePercent),
            completions = (existing?.completions ?: 0) + 1,
            lastPlayedMillis = now
        )
        dao.upsertScenarioRecord(updated)

        dao.insertSession(
            SessionRecordEntity(
                modeName = mode.name,
                mapName = mapName,
                score = breakdown.finalScore,
                trainsDelivered = trainsDelivered,
                onTimePercent = breakdown.onTimePercent,
                bestStreak = bestStreak,
                safetyIncidents = safetyIncidents,
                durationSeconds = durationSeconds,
                timestampMillis = now
            )
        )
        return isNewBest
    }

    suspend fun recordCasualSession(
        mode: GameMode,
        mapName: String,
        score: Int,
        trainsDelivered: Int,
        onTimePercent: Int,
        bestStreak: Int,
        safetyIncidents: Int,
        durationSeconds: Int
    ): Boolean {
        val existingBest = dao.getBestSessionForMode(mode.name)
        val isNewBest = existingBest == null || score > existingBest.score
        dao.insertSession(
            SessionRecordEntity(
                modeName = mode.name,
                mapName = mapName,
                score = score,
                trainsDelivered = trainsDelivered,
                onTimePercent = onTimePercent,
                bestStreak = bestStreak,
                safetyIncidents = safetyIncidents,
                durationSeconds = durationSeconds,
                timestampMillis = System.currentTimeMillis()
            )
        )
        return isNewBest
    }
}
