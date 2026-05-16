package com.scrollcapture.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureSessionDao {

    @Query("SELECT * FROM capture_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<CaptureSession>>

    @Query("SELECT * FROM capture_sessions WHERE id = :id")
    fun getSessionById(id: Long): Flow<CaptureSession?>

    @Query("SELECT * FROM capture_sessions WHERE id = :id")
    suspend fun getSessionByIdOnce(id: Long): CaptureSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: CaptureSession): Long

    @Update
    suspend fun update(session: CaptureSession)

    @Delete
    suspend fun delete(session: CaptureSession)

    @Query("DELETE FROM capture_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
