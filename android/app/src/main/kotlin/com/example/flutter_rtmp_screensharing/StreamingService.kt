/*
package com.example.game_app

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pedro.rtplibrary.rtmp.RtmpDisplay
import net.ossrs.rtmp.ConnectCheckerRtmp

class StreamingService : Service(), ConnectCheckerRtmp {

    companion object {
        const val CHANNEL_ID = "screen_share_channel_service"
        const val NOTIFICATION_ID = 1
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA_INTENT = "dataIntent"
        const val EXTRA_RTMP_URL = "rtmpUrl"
    }

    private var rtmpDisplay: RtmpDisplay? = null
    private var mediaProjection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Streaming",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Streaming")
            .setContentText("Your screen is being streamed")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA_INTENT)
        val rtmpUrl = intent?.getStringExtra(EXTRA_RTMP_URL)

        if (resultCode == Activity.RESULT_OK && data != null && rtmpUrl != null) {
            startRtmpStream(resultCode, data, rtmpUrl)
        }

        return START_STICKY
    }

    private fun startRtmpStream(resultCode: Int, data: Intent, rtmpUrl: String) {
        rtmpDisplay = RtmpDisplay(this, true, this)
        rtmpDisplay?.setIntentResult(resultCode, data)
        rtmpDisplay?.prepareVideo(1280, 720, 30, 3_000_000, false, 0)
        rtmpDisplay?.prepareAudio()
        rtmpDisplay?.startStream(rtmpUrl)
    }

    fun stopRtmpStream() {
        rtmpDisplay?.stopStream()
        rtmpDisplay = null
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ========================
    // RTMP CALLBACKS
    // ========================
    override fun onConnectionSuccessRtmp() { println("RTMP connected") }
    override fun onConnectionFailedRtmp(reason: String) { println("RTMP failed: $reason"); stopRtmpStream() }
    override fun onNewBitrateRtmp(bitrate: Long) {}
    override fun onDisconnectRtmp() { println("RTMP disconnected") }
    override fun onAuthErrorRtmp() {}
    override fun onAuthSuccessRtmp() {}
}
*/