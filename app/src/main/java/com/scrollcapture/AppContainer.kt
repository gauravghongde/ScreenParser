package com.scrollcapture

import android.content.Context
import com.scrollcapture.capture.CaptureSessionManager
import com.scrollcapture.capture.OcrEngine
import com.scrollcapture.data.AppDatabase
import com.scrollcapture.data.SessionRepository

/**
 * Manual dependency injection container.
 * Provides singleton instances of core dependencies.
 */
class AppContainer(context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val captureSessionDao = database.captureSessionDao()

    val sessionRepository = SessionRepository(captureSessionDao)
    val ocrEngine = OcrEngine()
    val captureSessionManager = CaptureSessionManager(ocrEngine, sessionRepository)
}
