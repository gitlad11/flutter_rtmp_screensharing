package com.example.flutter_rtmp_screensharing

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
import androidx.core.app.NotificationCompat

class ForegroundStreamService : Service() {

    companion object {
        const val CHANNEL_ID = "screen_share_channel"
        const val CHANNEL_NAME = "Screen sharing"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_STOP_AND_OPEN_APP = "ACTION_STOP_AND_OPEN_APP"

        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
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
                stopEverything()
                openFlutterApp()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())

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
        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenShare",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            null,
            null,
            null
        )
    }

    private fun stopEverything() {
        stopScreenShare()
        stopForeground(true)
        stopSelf()
    }

    private fun stopScreenShare() {
        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun openFlutterApp() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
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
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Screen sharing in progress"
                setSound(null, null)
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopAndOpenIntent = Intent(this, ForegroundStreamService::class.java).apply {
            action = ACTION_STOP_AND_OPEN_APP
        }

        val stopAndOpenPending = PendingIntent.getService(
            this,
            0,
            stopAndOpenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen sharing")
            .setContentText("Tap to stop and return to app")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop & Open App",
                stopAndOpenPending
            )
            .build()
    }
}
