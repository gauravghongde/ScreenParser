package com.scrollcapture.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CaptureSession::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun captureSessionDao(): CaptureSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scrollcapture_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
