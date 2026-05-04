package com.example.flutter_rtmp_screensharing

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gitlad.rtmpstreamer.RtmpStreamingClient
import com.gitlad.rtmpstreamer.StreamEvent
import com.gitlad.rtmpstreamer.StreamEventListener
import com.gitlad.rtmpstreamer.StreamOrientation
import com.gitlad.rtmpstreamer.StreamSettings
import com.gitlad.rtmpstreamer.StreamSource
import com.gitlad.rtmpstreamer.preview.PreviewSurfaceHolder
import com.gitlad.rtmpstreamer.screen.ScreenShareForegroundService
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity(), StreamEventListener {

    private val channelName = "screen_share_channel"
    private val requestMediaProjection = 1001
    private val requestPermissionsCode = 1002
    private val mainHandler = Handler(Looper.getMainLooper())

    private var methodChannel: MethodChannel? = null
    private lateinit var streamingClient: RtmpStreamingClient
    private var settingsReceived = false

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        streamingClient = RtmpStreamingClient(applicationContext, this)
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)

        flutterEngine.platformViewsController.registry.registerViewFactory(
            "native_camera_preview",
            NativeCameraPreviewFactory(::onCameraPreviewReady),
        )

        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "startPreviewCamera" -> {
                    if (!ensureCameraPerms()) {
                        result.error("NO_PERMS", "Permissions required", null)
                    } else {
                        mainHandler.postDelayed({
                            result.success(startPreviewCamera())
                        }, 500)
                    }
                }

                "startStream" -> {
                    val url = (call.arguments as? Map<*, *>)?.get("url") as? String
                    if (url.isNullOrBlank()) {
                        result.error("BAD_ARGS", "URL required", null)
                    } else {
                        result.success(streamingClient.startStream(url))
                    }
                }

                "startPreviewScreen" -> {
                    requestScreenCapturePermission()
                    result.success(true)
                }

                "updateStreamSettings" -> {
                    val settings = (call.arguments as? Map<*, *>)?.toStreamSettings()
                    if (settings == null) {
                        result.error("BAD_ARGS", "Settings map required", null)
                    } else {
                        applyStreamSettings(settings)
                        result.success(true)
                    }
                }

                "stopStream" -> {
                    streamingClient.stopStream()
                    result.success(true)
                }

                "switchSource" -> {
                    when (StreamSource.fromChannelValue(call.arguments as? String)) {
                        StreamSource.CAMERA -> result.success(switchToCamera())
                        StreamSource.SCREEN -> {
                            requestScreenCapturePermission()
                            result.success(true)
                        }
                    }
                }

                "switchCamera" -> result.success(streamingClient.switchCamera())

                "toggleMute" -> result.success(streamingClient.toggleMute())

                "getState" -> result.success(currentState())

                "release" -> {
                    releaseAll()
                    result.success(true)
                }

                else -> result.notImplemented()
            }
        }
    }

    override fun onStreamEvent(event: StreamEvent) {
        runOnUiThread {
            methodChannel?.invokeMethod(
                "onNativeEvent",
                hashMapOf("type" to event.type, "message" to event.message),
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != requestMediaProjection || resultCode != Activity.RESULT_OK || data == null) return

        startForegroundForScreen()
        mainHandler.postDelayed({
            val projection = runCatching {
                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                manager.getMediaProjection(resultCode, data)
            }.getOrNull()

            if (projection == null) {
                onStreamEvent(StreamEvent("failed", "Failed to obtain MediaProjection"))
                return@postDelayed
            }

            if (!streamingClient.switchToScreen(projection)) {
                onStreamEvent(StreamEvent("failed", "Screen sharing requires active foreground service"))
            }
        }, 350)
    }

    private fun startPreviewCamera(): Boolean {
        val view = waitForCameraPreviewView() ?: return false
        return streamingClient.startPreview(view)
    }

    private fun waitForCameraPreviewView(timeoutMs: Long = 3_000L): TextureView? {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(PreviewSurfaceHolder.lock) {
            while (System.currentTimeMillis() < deadline) {
                val view = PreviewSurfaceHolder.cameraView
                if (
                    view != null &&
                    view.isAvailable &&
                    PreviewSurfaceHolder.cameraAttached &&
                    PreviewSurfaceHolder.cameraReady &&
                    PreviewSurfaceHolder.cameraWidth > 0 &&
                    PreviewSurfaceHolder.cameraHeight > 0
                ) {
                    return view
                }
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                PreviewSurfaceHolder.lock.wait(remaining)
            }
        }
        return null
    }

    private fun onCameraPreviewReady() {
        runOnUiThread {
            if (settingsReceived) {
                startPreviewCamera()
            }
        }
    }

    private fun applyStreamSettings(settings: StreamSettings) {
        settingsReceived = true
        PreviewSurfaceHolder.updateTargetPreviewSize(settings.width, settings.height)
        streamingClient.updateSettings(settings)
    }

    private fun switchToCamera(): Boolean {
        stopForegroundForScreen()
        return streamingClient.switchToCamera()
    }

    private fun releaseAll() {
        streamingClient.release()
        settingsReceived = false
        stopForegroundForScreen()
    }

    private fun ensureCameraPerms(): Boolean {
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val microphoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted && microphoneGranted) return true

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            requestPermissionsCode,
        )
        return false
    }

    private fun requestScreenCapturePermission() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), requestMediaProjection)
    }

    private fun startForegroundForScreen() {
        val intent = Intent(this, ScreenShareForegroundService::class.java).apply {
            action = ScreenShareForegroundService.ACTION_START
            putExtra(ScreenShareForegroundService.EXTRA_OPEN_ACTIVITY_CLASS, MainActivity::class.java.name)
        }
        startForegroundService(intent)
    }

    private fun stopForegroundForScreen() {
        val intent = Intent(this, ScreenShareForegroundService::class.java).apply {
            action = ScreenShareForegroundService.ACTION_STOP
        }
        startService(intent)
    }

    private fun Map<*, *>.toStreamSettings(): StreamSettings {
        val orientation = if (this["orientation"] as? String == "portrait") {
            StreamOrientation.PORTRAIT
        } else {
            StreamOrientation.LANDSCAPE
        }

        return StreamSettings(
            width = (this["width"] as? Number)?.toInt()?.takeIf { it > 0 } ?: 1280,
            height = (this["height"] as? Number)?.toInt()?.takeIf { it > 0 } ?: 720,
            bitrate = (this["bitrate"] as? Number)?.toInt()?.takeIf { it > 0 } ?: 4_000_000,
            fps = (this["fps"] as? Number)?.toInt()?.takeIf { it > 0 } ?: 30,
            orientation = orientation,
        )
    }

    private fun currentState(): Map<String, Any> {
        return hashMapOf(
            "source" to streamingClient.source.channelValue,
            "isStreaming" to streamingClient.isStreaming(),
            "isOnPreview" to streamingClient.isOnPreview(),
            "isMuted" to streamingClient.isMuted,
        )
    }
}
