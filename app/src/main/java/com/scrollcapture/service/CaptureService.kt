package com.scrollcapture.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.scrollcapture.MainActivity
import com.scrollcapture.R
import com.scrollcapture.ScrollCaptureApp
import kotlinx.coroutines.*

class CaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { stopCapture() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                @Suppress("DEPRECATION")
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, createNotification())
                }
                if (resultCode != -1 && resultData != null) startCapture(resultCode, resultData)
            }
            ACTION_PAUSE -> {
                ScrollCaptureApp.instance.container.captureSessionManager.pauseSession()
            }
            ACTION_RESUME -> {
                ScrollCaptureApp.instance.container.captureSessionManager.resumeSession()
            }
            ACTION_STOP -> {
                scope.launch {
                    ScrollCaptureApp.instance.container.captureSessionManager.stopSession()
                    stopCapture()
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = pm.getMediaProjection(resultCode, resultData)
        mediaProjection?.registerCallback(projectionCallback, null)

        // Bug 5 fix: increase buffer from 2 → 3 to reduce dropped frames between polls
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 3)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScrollCapture", screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader!!.surface, null, null
        )
        ScrollCaptureApp.instance.container.captureSessionManager.startSession()

        // Bug 5 fix: poll at ~2.5 fps instead of 1 fps so fast scrolling isn't missed
        captureJob = scope.launch {
            while (isActive) { delay(400L); captureFrame() }
        }
    }

    private fun captureFrame() {
        val image: Image = imageReader?.acquireLatestImage() ?: return
        try {
            val bitmap = imageToBitmap(image)
            if (bitmap != null) {
                ScrollCaptureApp.instance.container.captureSessionManager.processFrame(bitmap)
            }
        } finally { image.close() }
    }

    /**
     * Bug 2 fix: correctly handle ImageReader row-stride padding.
     *
     * Android image planes use rowStride which is often wider than width × pixelStride.
     * Copying the raw buffer directly into a width-sized bitmap silently corrupts the image
     * because the padding bytes shift every row. Instead we:
     *   1. Create the bitmap at the padded width (rowStride / pixelStride).
     *   2. Copy the buffer directly — no manual ByteArray needed.
     *   3. Crop to the real screen width to strip the right-side padding.
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        // Padded width that aligns with the buffer layout
        val paddedWidth = rowStride / pixelStride

        if (paddedWidth <= 0 || image.height <= 0) return null

        val bitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)

        // Crop to actual screen width if padding was added
        return if (paddedWidth != image.width) {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            bitmap.recycle()
            cropped
        } else {
            bitmap
        }
    }

    private fun stopCapture() {
        captureJob?.cancel(); captureJob = null
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.close(); imageReader = null
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop(); mediaProjection = null
        isRunning = false
    }

    private fun createNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, ScrollCaptureApp.CAPTURE_CHANNEL_ID)
            .setContentTitle("ScrollCapture")
            .setContentText("Capturing screen…")
            .setSmallIcon(R.drawable.ic_capture)
            .setContentIntent(pi).setOngoing(true).setSilent(true).build()
    }

    override fun onDestroy() { stopCapture(); scope.cancel(); super.onDestroy() }

    companion object {
        const val ACTION_START = "com.scrollcapture.ACTION_START_CAPTURE"
        const val ACTION_PAUSE = "com.scrollcapture.ACTION_PAUSE_CAPTURE"
        const val ACTION_RESUME = "com.scrollcapture.ACTION_RESUME_CAPTURE"
        const val ACTION_STOP = "com.scrollcapture.ACTION_STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val NOTIFICATION_ID = 1001
        var isRunning = false; private set

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val i = Intent(context, CaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            context.startForegroundService(i)
        }

        fun sendAction(context: Context, action: String) {
            context.startService(Intent(context, CaptureService::class.java).apply { this.action = action })
        }
    }
}