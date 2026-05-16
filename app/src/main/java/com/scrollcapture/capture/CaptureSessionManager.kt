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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /**
     * Serialises all DeduplicationEngine access.
     *
     * processFrame launches one coroutine per frame on Dispatchers.Default (a thread pool).
     * Without this mutex multiple OCR results arrive concurrently and simultaneously mutate
     * assembledLines / previousFrameLines, corrupting the assembled text.
     */
    private val dedupMutex = Mutex()

    private val _state = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    fun startSession() {
        dedup.reset()
        _state.value = CaptureState(
            isCapturing = true,
            isPaused = false,
            startTime = System.currentTimeMillis()
        )
    }

    fun pauseSession() {
        _state.value = _state.value.copy(isPaused = true)
    }

    fun resumeSession() {
        _state.value = _state.value.copy(isPaused = false)
    }

    /**
     * Process a captured screen bitmap. Runs OCR then feeds into the dedup engine.
     *
     * OCR runs concurrently across frames (intentional — keeps throughput high), but
     * DeduplicationEngine writes are serialised through dedupMutex so order-sensitive
     * state (assembledLines, previousFrameLines) is never touched by two coroutines at once.
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
                    dedupMutex.withLock {
                        dedup.processFrame(lines)
                        _state.value = _state.value.copy(
                            frameCount = dedup.getFrameCount(),
                            charCount = dedup.getCharCount()
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                bitmap.recycle()
            }
        }
    }

    suspend fun stopSession(): Long {
        val currentState = _state.value

        // Acquire mutex before reading so any in-flight OCR results are flushed first
        val assembledText = dedupMutex.withLock { dedup.getAssembledText() }
        val duration = System.currentTimeMillis() - currentState.startTime

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
        dedupMutex.withLock { dedup.reset() }

        return id
    }

    fun getCurrentText(): String = dedup.getAssembledText()
}