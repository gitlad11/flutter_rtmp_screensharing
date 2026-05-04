package com.gitlad.rtmpstreamer.screen

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import com.gitlad.rtmpstreamer.R

class ScreenShareForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "rtmpstreamer_screen_share"
        const val CHANNEL_NAME = "Screen sharing"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.gitlad.rtmpstreamer.action.START_SCREEN_FOREGROUND"
        const val ACTION_STOP = "com.gitlad.rtmpstreamer.action.STOP_SCREEN_FOREGROUND"
        const val ACTION_STOP_AND_OPEN_APP = "com.gitlad.rtmpstreamer.action.STOP_AND_OPEN_APP"

        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val EXTRA_OPEN_ACTIVITY_CLASS = "openActivityClass"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }

            ACTION_STOP_AND_OPEN_APP -> {
                val activityClassName = intent.getStringExtra(EXTRA_OPEN_ACTIVITY_CLASS)
                stopEverything()
                openApp(activityClassName)
                return START_NOT_STICKY
            }

            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification(intent.getStringExtra(EXTRA_OPEN_ACTIVITY_CLASS)))

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)

                if (resultCode == Activity.RESULT_OK && data != null) {
                    startProjection(resultCode, data)
                }
            }
        }

        return START_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenShare",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            null,
            null,
            null,
        )
    }

    private fun stopEverything() {
        stopScreenShare()
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    private fun stopScreenShare() {
        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun openApp(activityClassName: String?) {
        if (activityClassName.isNullOrBlank()) return

        val openIntent = Intent().apply {
            setClassName(this@ScreenShareForegroundService, activityClassName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(openIntent)
    }

    override fun onDestroy() {
        stopScreenShare()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Screen sharing in progress"
                setSound(null, null)
            }

            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(activityClassName: String?): Notification {
        val stopAndOpenIntent = Intent(this, ScreenShareForegroundService::class.java).apply {
            action = ACTION_STOP_AND_OPEN_APP
            putExtra(EXTRA_OPEN_ACTIVITY_CLASS, activityClassName)
        }

        val stopAndOpenPending = PendingIntent.getService(
            this,
            0,
            stopAndOpenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Screen sharing")
            .setContentText("Tap to stop and return to app")
            .setSmallIcon(R.drawable.rtmpstreamer_notification)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop & Open App",
                stopAndOpenPending,
            )
            .build()
    }
}
