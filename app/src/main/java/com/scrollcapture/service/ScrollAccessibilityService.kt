package com.scrollcapture.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class ScrollAccessibilityService : AccessibilityService() {

    private var isScrolling = false
    private var scrollSpeed = ScrollSpeed.MEDIUM
    private val handler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null

    enum class ScrollSpeed(val intervalMs: Long, val gestureDurationMs: Long) {
        SLOW(1200L, 800L),
        MEDIUM(700L, 500L),
        FAST(350L, 300L)
    }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_START_SCROLL -> {
                    val speedName = intent.getStringExtra(EXTRA_SPEED) ?: "MEDIUM"
                    scrollSpeed = ScrollSpeed.valueOf(speedName)
                    startAutoScroll()
                }
                ACTION_STOP_SCROLL -> stopAutoScroll()
                ACTION_SET_SPEED -> {
                    val speedName = intent.getStringExtra(EXTRA_SPEED) ?: "MEDIUM"
                    scrollSpeed = ScrollSpeed.valueOf(speedName)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInstance = this

        val filter = IntentFilter().apply {
            addAction(ACTION_START_SCROLL)
            addAction(ACTION_STOP_SCROLL)
            addAction(ACTION_SET_SPEED)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this, commandReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used — we only need this service for gesture dispatch
    }

    override fun onInterrupt() {
        stopAutoScroll()
    }

    override fun onDestroy() {
        stopAutoScroll()
        try {
            unregisterReceiver(commandReceiver)
        } catch (_: Exception) {}
        serviceInstance = null
        super.onDestroy()
    }

    private fun startAutoScroll() {
        if (isScrolling) return
        isScrolling = true
        scheduleNextScroll()
    }

    private fun stopAutoScroll() {
        isScrolling = false
        scrollRunnable?.let { handler.removeCallbacks(it) }
        scrollRunnable = null
    }

    private fun scheduleNextScroll() {
        if (!isScrolling) return

        scrollRunnable = Runnable {
            performScrollDown()
            if (isScrolling) {
                scheduleNextScroll()
            }
        }
        handler.postDelayed(scrollRunnable!!, scrollSpeed.intervalMs)
    }

    private fun performScrollDown() {
        val displayMetrics = resources.displayMetrics
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels

        val xCenter = width / 2f
        val startY = height * 0.75f
        val endY = height * 0.25f

        val swipePath = Path().apply {
            moveTo(xCenter, startY)
            lineTo(xCenter, endY)
        }

        val stroke = GestureDescription.StrokeDescription(
            swipePath, 0, scrollSpeed.gestureDurationMs
        )
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                // Gesture completed successfully
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                // Gesture was cancelled
            }
        }, handler)
    }

    companion object {
        const val ACTION_START_SCROLL = "com.scrollcapture.ACTION_START_SCROLL"
        const val ACTION_STOP_SCROLL = "com.scrollcapture.ACTION_STOP_SCROLL"
        const val ACTION_SET_SPEED = "com.scrollcapture.ACTION_SET_SPEED"
        const val EXTRA_SPEED = "extra_speed"

        var serviceInstance: ScrollAccessibilityService? = null
            private set

        fun isRunning(): Boolean = serviceInstance != null

        fun sendCommand(context: Context, action: String, speed: ScrollSpeed? = null) {
            val intent = Intent(action).apply {
                setPackage(context.packageName)
                speed?.let { putExtra(EXTRA_SPEED, it.name) }
            }
            context.sendBroadcast(intent)
        }
    }
}
