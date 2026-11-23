package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ScenarioRecordEntity::class, SessionRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SignalYardDatabase : RoomDatabase() {
    abstract fun signalYardDao(): SignalYardDao

    companion object {
        @Volatile
        private var INSTANCE: SignalYardDatabase? = null

        fun getInstance(context: Context): SignalYardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SignalYardDatabase::class.java,
                    "signal_yard_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
