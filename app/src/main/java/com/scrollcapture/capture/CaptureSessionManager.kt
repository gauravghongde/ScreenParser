package com.scrollcapture.capture

import android.graphics.Bitmap
import com.scrollcapture.data.CaptureSession
import com.scrollcapture.data.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CaptureState(
    val isCapturing: Boolean = false,
    val isPaused: Boolean = false,
    val frameCount: Int = 0,
    val charCount: Int = 0,
    val startTime: Long = 0L,
    val currentSessionId: Long? = null
)

class CaptureSessionManager(
    private val ocrEngine: OcrEngine,
    private val repository: SessionRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dedup = DeduplicationEngine()

    private val _state = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    /**
     * Start a new capture session.
     */
    fun startSession() {
        dedup.reset()
        _state.value = CaptureState(
            isCapturing = true,
            isPaused = false,
            startTime = System.currentTimeMillis()
        )
    }

    /**
     * Pause the current session (stops processing frames but keeps state).
     */
    fun pauseSession() {
        _state.value = _state.value.copy(isPaused = true)
    }

    /**
     * Resume a paused session.
     */
    fun resumeSession() {
        _state.value = _state.value.copy(isPaused = false)
    }

    /**
     * Process a captured screen bitmap. Runs OCR and feeds into dedup engine.
     */
    fun processFrame(bitmap: Bitmap) {
        val currentState = _state.value
        if (!currentState.isCapturing || currentState.isPaused) {
            bitmap.recycle()
            return
        }

        scope.launch {
            try {
                val lines = ocrEngine.recognize(bitmap)
                if (lines.isNotEmpty()) {
                    dedup.processFrame(lines)
                    _state.value = _state.value.copy(
                        frameCount = dedup.getFrameCount(),
                        charCount = dedup.getCharCount()
                    )
                }
            } catch (e: Exception) {
                // Log but don't crash — OCR failures on individual frames are recoverable
                e.printStackTrace()
            } finally {
                bitmap.recycle()
            }
        }
    }

    /**
     * Stop the current session, save to database, and return the session ID.
     */
    suspend fun stopSession(): Long {
        val currentState = _state.value
        val assembledText = dedup.getAssembledText()
        val duration = System.currentTimeMillis() - currentState.startTime

        // Generate title from first meaningful line
        val title = assembledText.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.length > 5 }
            ?.take(60)
            ?: "Capture ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"

        val session = CaptureSession(
            timestamp = currentState.startTime,
            title = title,
            assembledText = assembledText,
            characterCount = assembledText.length,
            frameCount = currentState.frameCount,
            durationMs = duration
        )

        val id = repository.insertSession(session)

        _state.value = CaptureState(currentSessionId = id)
        dedup.reset()

        return id
    }

    /**
     * Get the current assembled text without stopping.
     */
    fun getCurrentText(): String = dedup.getAssembledText()
}
