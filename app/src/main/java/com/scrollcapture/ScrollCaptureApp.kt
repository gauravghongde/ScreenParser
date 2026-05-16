package com.scrollcapture

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class ScrollCaptureApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val captureChannel = NotificationChannel(
            CAPTURE_CHANNEL_ID,
            getString(R.string.capture_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.capture_channel_description)
            setShowBadge(false)
        }

        val overlayChannel = NotificationChannel(
            OVERLAY_CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.overlay_channel_description)
            setShowBadge(false)
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(captureChannel)
        nm.createNotificationChannel(overlayChannel)
    }

    companion object {
        const val CAPTURE_CHANNEL_ID = "capture_channel"
        const val OVERLAY_CHANNEL_ID = "overlay_channel"

        lateinit var instance: ScrollCaptureApp
            private set
    }
}
