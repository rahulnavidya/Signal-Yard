package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scenario_records")
data class ScenarioRecordEntity(
    @PrimaryKey val scenarioId: String,
    val bestScore: Int,
    val bestMedalOrdinal: Int,
    val bestOnTimePercent: Int,
    val completions: Int,
    val lastPlayedMillis: Long
)

@Entity(tableName = "session_records")
data class SessionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modeName: String,
    val mapName: String,
    val score: Int,
    val trainsDelivered: Int,
    val onTimePercent: Int,
    val bestStreak: Int,
    val safetyIncidents: Int,
    val durationSeconds: Int,
    val timestampMillis: Long
)
