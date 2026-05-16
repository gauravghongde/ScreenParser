package com.scrollcapture.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.scrollcapture.MainActivity
import com.scrollcapture.R
import com.scrollcapture.ScrollCaptureApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var expandedView: View? = null
    private var isExpanded = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Drag tracking
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    // Tag constants for views that need runtime updates
    private companion object {
        const val TAG_FRAMES = "tv_frames"
        const val TAG_CHARS = "tv_chars"
        const val TAG_PAUSE_RESUME = "btn_pause_resume"
        const val TAG_AUTOSCROLL = "tv_autoscroll"
        const val NOTIFICATION_ID = 1002
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        createBubbleView()
        observeState()
    }

    private fun createBubbleView() {
        val size = (56 * resources.displayMetrics.density).toInt()
        val bubble = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bubble_bg)
            val icon = ImageView(this@OverlayService).apply {
                setImageResource(R.drawable.ic_capture)
                val pad = (12 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                setColorFilter(0xFFA78BFA.toInt())
            }
            addView(icon, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        bubble.setOnTouchListener(createDragTouchListener(params) {
            if (!isDragging) toggleExpanded()
        })

        windowManager?.addView(bubble, params)
        bubbleView = bubble
    }

    private fun toggleExpanded() {
        if (isExpanded) collapseOverlay() else expandOverlay()
    }

    private fun expandOverlay() {
        if (expandedView != null) return
        isExpanded = true
        bubbleView?.visibility = View.GONE

        val dp = resources.displayMetrics.density
        val w = (220 * dp).toInt()
        val h = (300 * dp).toInt()

        val panel = createExpandedPanel()

        val params = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (20 * dp).toInt()
            y = 300
        }

        windowManager?.addView(panel, params)
        expandedView = panel
    }

    private fun createExpandedPanel(): LinearLayout {
        val dp = resources.displayMetrics.density
        val ctx = this

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.panel_bg)
            val pad = (16 * dp).toInt()
            setPadding(pad, pad, pad, pad)

            addView(TextView(ctx).apply {
                text = "ScrollCapture"
                setTextColor(0xFFE8E6F0.toInt())
                textSize = 16f
                setPadding(0, 0, 0, (8 * dp).toInt())
            })

            addView(TextView(ctx).apply {
                tag = TAG_FRAMES
                text = "Frames: 0"
                setTextColor(0xFF9A97A8.toInt())
                textSize = 13f
            })

            addView(TextView(ctx).apply {
                tag = TAG_CHARS
                text = "Characters: 0"
                setTextColor(0xFF9A97A8.toInt())
                textSize = 13f
                setPadding(0, 0, 0, (12 * dp).toInt())
            })

            // Combined Pause / Resume toggle button
            // Label and action update in observeState() based on live CaptureState
            addView(createButton(ctx, "⏸  Pause", 0xFF14B8A6.toInt()) {
                val state = ScrollCaptureApp.instance.container.captureSessionManager.state.value
                if (state.isPaused) {
                    CaptureService.sendAction(ctx, CaptureService.ACTION_RESUME)
                } else {
                    CaptureService.sendAction(ctx, CaptureService.ACTION_PAUSE)
                }
            }.also { it.tag = TAG_PAUSE_RESUME })

            addView(createButton(ctx, "⏹  Stop", 0xFFEF4444.toInt()) {
                scope.launch {
                    ScrollCaptureApp.instance.container.captureSessionManager.stopSession()
                    ctx.stopService(Intent(ctx, CaptureService::class.java))
                    collapseOverlay()
                    stopSelf()
                }
            })

            addView(TextView(ctx).apply {
                tag = TAG_AUTOSCROLL
                text = "⚡ Auto-scroll: OFF"
                setTextColor(0xFFA78BFA.toInt())
                textSize = 13f
                val topPad = (12 * dp).toInt()
                setPadding(0, topPad, 0, 0)
                setOnClickListener { toggleAutoScroll(this) }
            })

            addView(TextView(ctx).apply {
                text = "━  Minimize"
                setTextColor(0xFF9A97A8.toInt())
                textSize = 12f
                val topPad = (8 * dp).toInt()
                setPadding(0, topPad, 0, 0)
                setOnClickListener { collapseOverlay() }
            })
        }
    }

    private var autoScrollOn = false

    private fun toggleAutoScroll(tv: TextView) {
        autoScrollOn = !autoScrollOn
        if (autoScrollOn) {
            tv.text = "⚡ Auto-scroll: ON"
            ScrollAccessibilityService.sendCommand(
                this, ScrollAccessibilityService.ACTION_START_SCROLL,
                ScrollAccessibilityService.ScrollSpeed.MEDIUM
            )
        } else {
            tv.text = "⚡ Auto-scroll: OFF"
            ScrollAccessibilityService.sendCommand(this, ScrollAccessibilityService.ACTION_STOP_SCROLL)
        }
    }

    private fun createButton(ctx: Context, label: String, color: Int, onClick: () -> Unit): TextView {
        val dp = resources.displayMetrics.density
        return TextView(ctx).apply {
            text = label
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setBackgroundColor(color)
            val hPad = (12 * dp).toInt()
            val vPad = (8 * dp).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (4 * dp).toInt() }
            setOnClickListener { onClick() }
        }
    }

    private fun collapseOverlay() {
        isExpanded = false
        expandedView?.let { windowManager?.removeView(it) }
        expandedView = null
        bubbleView?.visibility = View.VISIBLE
    }

    /**
     * Observes CaptureState and updates:
     *  - frame / char counters
     *  - Pause/Resume button label — this is what makes the Resume button work correctly.
     *    Previously the button was always labelled "Resume" regardless of pause state, and
     *    it called resumeSession() directly on the manager instead of going through
     *    CaptureService, so the service's own state was bypassed.
     */
    private fun observeState() {
        scope.launch {
            ScrollCaptureApp.instance.container.captureSessionManager.state.collectLatest { state ->
                expandedView?.let { panel ->
                    panel.findViewWithTag<TextView>(TAG_FRAMES)?.text =
                        "Frames: ${state.frameCount}"
                    panel.findViewWithTag<TextView>(TAG_CHARS)?.text =
                        "Characters: ${state.charCount}"
                    panel.findViewWithTag<TextView>(TAG_PAUSE_RESUME)?.text =
                        if (state.isPaused) "▶  Resume" else "⏸  Pause"
                }
            }
        }
    }

    private fun createDragTouchListener(
        params: WindowManager.LayoutParams,
        onClick: () -> Unit
    ): View.OnTouchListener {
        return View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > 10 || abs(dy) > 10) isDragging = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) onClick()
                    val screenH = resources.displayMetrics.heightPixels
                    if (params.y > screenH - 200) stopSelf()
                    true
                }
                else -> false
            }
        }
    }

    private fun createNotification() =
        NotificationCompat.Builder(this, ScrollCaptureApp.OVERLAY_CHANNEL_ID)
            .setContentTitle("ScrollCapture Overlay")
            .setContentText("Tap bubble to control capture")
            .setSmallIcon(R.drawable.ic_capture)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .setSilent(true)
            .build()

    override fun onDestroy() {
        bubbleView?.let { windowManager?.removeView(it) }
        expandedView?.let { windowManager?.removeView(it) }
        scope.cancel()
        isRunning = false
        super.onDestroy()
    }

    companion object {
        var isRunning = false; private set
    }
}