package com.scrollcapture.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "capture_sessions")
data class CaptureSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val title: String = "",
    val assembledText: String = "",
    val characterCount: Int = 0,
    val frameCount: Int = 0,
    val durationMs: Long = 0
)
