package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.GameRecordsRepository
import com.example.data.SignalYardDatabase
import com.example.scenarios.GameMode
import com.example.sim.MedalRank
import com.example.sim.ScoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches Signal Yard`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Signal Yard", appName)
    }

    @Test
    fun `room persistence stores and retrieves scenario medals and rush hour records`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SignalYardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = GameRecordsRepository(db.signalYardDao())
            val breakdown = ScoreManager.calculateSessionResult(
                deliveredCount = 3,
                onTimeDeliveredCount = 3,
                bestStreak = 3,
                safetyIncidents = 0,
                totalDelaySeconds = 0f,
                goldTargetScore = 1100,
                silverTargetScore = 850,
                bronzeTargetScore = 500
            )
            val isNewBest = repo.recordScenarioCompletion(
                scenarioId = "qd_crossing",
                mode = GameMode.QUICK_DISPATCH,
                mapName = "Valley Junction",
                breakdown = breakdown,
                trainsDelivered = 3,
                bestStreak = 3,
                safetyIncidents = 0,
                durationSeconds = 95
            )
            assertTrue(isNewBest)

            val records = repo.scenarioRecords.first()
            assertEquals(1, records.size)
            assertEquals(MedalRank.GOLD.rankOrder, records.first().bestMedalOrdinal)
            assertEquals(1270, records.first().bestScore)

            val total = repo.totalTrainsDelivered.first()
            assertEquals(3, total)
        } finally {
            db.close()
        }
    }
}
