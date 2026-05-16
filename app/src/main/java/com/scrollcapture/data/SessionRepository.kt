package com.scrollcapture.data

import kotlinx.coroutines.flow.Flow

class SessionRepository(private val dao: CaptureSessionDao) {

    fun getAllSessions(): Flow<List<CaptureSession>> = dao.getAllSessions()

    fun getSessionById(id: Long): Flow<CaptureSession?> = dao.getSessionById(id)

    suspend fun getSessionByIdOnce(id: Long): CaptureSession? = dao.getSessionByIdOnce(id)

    suspend fun insertSession(session: CaptureSession): Long = dao.insert(session)

    suspend fun updateSession(session: CaptureSession) = dao.update(session)

    suspend fun deleteSession(session: CaptureSession) = dao.delete(session)

    suspend fun deleteSessionById(id: Long) = dao.deleteById(id)
}
