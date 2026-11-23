package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalYardDao {
    @Query("SELECT * FROM scenario_records ORDER BY lastPlayedMillis DESC")
    fun observeScenarioRecords(): Flow<List<ScenarioRecordEntity>>

    @Query("SELECT * FROM scenario_records WHERE scenarioId = :scenarioId LIMIT 1")
    suspend fun getScenarioRecord(scenarioId: String): ScenarioRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScenarioRecord(record: ScenarioRecordEntity)

    @Query("SELECT * FROM session_records ORDER BY timestampMillis DESC LIMIT 50")
    fun observeRecentSessions(): Flow<List<SessionRecordEntity>>

    @Query("SELECT * FROM session_records WHERE modeName = :modeName ORDER BY score DESC LIMIT 1")
    suspend fun getBestSessionForMode(modeName: String): SessionRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionRecordEntity)

    @Query("SELECT COALESCE(SUM(trainsDelivered), 0) FROM session_records")
    fun observeTotalTrainsDelivered(): Flow<Int>

    @Query("SELECT COALESCE(MAX(bestStreak), 0) FROM session_records")
    fun observeBestEverStreak(): Flow<Int>

    @Query("SELECT COALESCE(MAX(score), 0) FROM session_records WHERE modeName = :modeName")
    fun observeHighScoreForMode(modeName: String): Flow<Int>
}
