package com.example.flutter_rtmp_screensharing

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.TextureView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.ScreenSource
import com.pedro.library.rtmp.RtmpStream
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val channel = "screen_share_channel"
    private val requestMediaProjection = 1001
    private val requestPermissionsCode = 1002
    private val mainHandler = Handler(Looper.getMainLooper())

    private var methodChannel: MethodChannel? = null
    private var rtmpStream: RtmpStream? = null
    private var cameraSource: Camera2Source? = null
    private var microphoneSource: MicrophoneSource? = null

    private var currentSource: String = "camera"
    private var isMuted = false
    private var videoPrepared = false
    private var audioPrepared = false
    private var streamWidth = 1280
    private var streamHeight = 720
    private var streamBitrate = 4_000_000
    private var streamFps = 30
    private var isPortrait = false
    private var settingsReceived = false

    private fun fallbackVideoProfiles(): List<VideoProfile> {
        return listOf(
            VideoProfile(width = 1280, height = 720, fps = 30, bitrate = 2_000_000),
            VideoProfile(width = 640, height = 480, fps = 30, bitrate = 1_200_000)
        )
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channel)
        flutterEngine.platformViewsController.registry.registerViewFactory(
            "native_camera_preview",
            NativeCameraPreviewFactory(::onCameraPreviewReady)
        )

        methodChannel!!.setMethodCallHandler { call, result ->
            when (call.method) {
                "startPreviewCamera" -> {
                    if (!ensureCameraPerms()) {
                        result.error("NO_PERMS", "Permissions required", null)
                    } else {
                        mainHandler.postDelayed({
                            result.success(startPreviewInternal())
                        }, 500)
                    }
                }

                "startStream" -> {
                    val url = (call.arguments as? Map<*, *>)?.get("url") as? String
                    if (url.isNullOrBlank()) {
                        result.error("BAD_ARGS", "URL required", null)
                    } else {
                        result.success(startStreamInternal(url))
                    }
                }

                "updateStreamSettings" -> {
                    val settings = call.arguments as? Map<*, *>
                    if (settings == null) {
                        result.error("BAD_ARGS", "Settings map required", null)
                    } else {
                        updateStreamSettings(settings)
                        result.success(true)
                    }
                }

                "stopStream" -> {
                    rtmpStream?.stopStream()
                    sendEvent("stopped", null)
                    result.success(true)
                }

                "switchSource" -> {
                    val to = (call.arguments as? String) ?: "camera"
                    result.success(switchSourceInternal(to))
                }

                "switchCamera" -> {
                    val stream = rtmpStream
                    if (stream == null || currentSource != "camera") {
                        result.success(false)
                    } else {
                        val switched = runCatching {
                            (stream.videoSource as? Camera2Source ?: cameraSource)?.switchCamera()
                            true
                        }.getOrDefault(false)
                        result.success(switched)
                    }
                }

                "toggleMute" -> {
                    if (microphoneSource == null) {
                        result.success(false)
                    } else {
                        isMuted = !isMuted
                        if (isMuted) microphoneSource?.mute() else microphoneSource?.unMute()
                        result.success(isMuted)
                    }
                }

                "release" -> {
                    releaseAll()
                    result.success(true)
                }

                else -> result.notImplemented()
            }
        }
    }

    private fun initStreamIfNeeded(): Boolean {
        if (rtmpStream != null) return true
        cameraSource = Camera2Source(applicationContext)
        microphoneSource = MicrophoneSource()
        rtmpStream = RtmpStream(applicationContext, connectChecker, cameraSource!!, microphoneSource!!)
        return true
    }

    private fun startPreviewInternal(): Boolean {
        if (!initStreamIfNeeded()) return false
        val stream = rtmpStream ?: return false
        if (!prepareStreamIfNeeded(stream)) return false

        val view = waitForCameraPreviewView() ?: return false

        return try {
            if (view.width <= 0 || view.height <= 0) {
                Log.e("RTMP", "Cannot start preview: View dimensions are zero (${view.width}x${view.height})")
                return false
            }

            if (!stream.isOnPreview) {
                stream.startPreview(view)
            }
            currentSource = "camera"
            sendEvent("preview_started", "camera")
            true
        } catch (e: Exception) {
            Log.e("RTMP", "Preview failed", e)
            false
        }
    }

    private fun waitForCameraPreviewView(timeoutMs: Long = 3_000L): TextureView? {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(NativePreviewHolder.lock) {
            while (System.currentTimeMillis() < deadline) {
                val view = NativePreviewHolder.cameraView
                if (
                    view != null &&
                    view.isAvailable &&
                    NativePreviewHolder.cameraAttached &&
                    NativePreviewHolder.cameraReady &&
                    NativePreviewHolder.cameraWidth > 0 &&
                    NativePreviewHolder.cameraHeight > 0
                ) {
                    return view
                }
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                NativePreviewHolder.lock.wait(remaining)
            }
        }
        return null
    }

    private fun onCameraPreviewReady() {
        runOnUiThread {
            if (settingsReceived) {
                startPreviewInternal()
            }
        }
    }

    private fun startStreamInternal(url: String): Boolean {
        if (!initStreamIfNeeded()) return false
        val stream = rtmpStream ?: return false

        return try {
            if (!stream.isStreaming) {
                if (!prepareStreamIfNeeded(stream)) return false
                stream.startStream(url)
                sendEvent("started", currentSource)
            }
            true
        } catch (e: Exception) {
            Log.e("RTMP", "Start stream failed", e)
            false
        }
    }

    private fun switchSourceInternal(to: String): Boolean {
        val stream = rtmpStream ?: return false
        if (currentSource == to) return true

        return if (to == "screen") {
            requestScreenCapturePermission()
            true
        } else {
            stopForegroundForScreen()
            val source = cameraSource ?: Camera2Source(applicationContext).also { cameraSource = it }
            stream.changeVideoSource(source)
            currentSource = "camera"
            sendEvent("source_changed", "camera")
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestMediaProjection && resultCode == Activity.RESULT_OK && data != null) {
            val stream = rtmpStream ?: return
            startForegroundForScreen()
            mainHandler.postDelayed({
                try {
                    val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val projection = pm.getMediaProjection(resultCode, data) ?: run {
                        sendEvent("failed", "Failed to obtain MediaProjection")
                        return@postDelayed
                    }
                    val screenSource = ScreenSource(applicationContext, projection)
                    stream.changeVideoSource(screenSource)
                    currentSource = "screen"
                    sendEvent("source_changed", "screen")
                } catch (e: SecurityException) {
                    Log.e("RTMP", "MediaProjection requires active foreground service", e)
                    sendEvent("failed", "Screen sharing requires active foreground service")
                } catch (e: Exception) {
                    Log.e("RTMP", "Failed to switch to screen source", e)
                    sendEvent("failed", "Failed to start screen sharing")
                }
            }, 350)
        }
    }

    private fun releaseAll() {
        try {
            rtmpStream?.release()
        } catch (_: Exception) {
        }
        rtmpStream = null
        cameraSource = null
        microphoneSource = null
        videoPrepared = false
        audioPrepared = false
        settingsReceived = false
        stopForegroundForScreen()
    }

    private fun updateStreamSettings(settings: Map<*, *>) {
        streamWidth = (settings["width"] as? Number)?.toInt()?.takeIf { it > 0 } ?: streamWidth
        streamHeight = (settings["height"] as? Number)?.toInt()?.takeIf { it > 0 } ?: streamHeight
        streamBitrate = (settings["bitrate"] as? Number)?.toInt()?.takeIf { it > 0 } ?: streamBitrate
        streamFps = (settings["fps"] as? Number)?.toInt()?.takeIf { it > 0 } ?: streamFps
        isPortrait = (settings["orientation"] as? String) == "portrait"
        settingsReceived = true

        NativePreviewHolder.targetPreviewWidth = streamWidth
        NativePreviewHolder.targetPreviewHeight = streamHeight
        (NativePreviewHolder.cameraView as? AspectRatioTextureView)
            ?.setAspectRatio(streamWidth, streamHeight)

        val stream = rtmpStream
        val isActive = stream?.isStreaming == true || stream?.isOnPreview == true
        if (!isActive) {
            videoPrepared = false
            audioPrepared = false
        }

        Log.d(
            "RTMP",
            "Updated stream settings: ${streamWidth}x${streamHeight} @ ${streamFps}fps ${streamBitrate}bps" +
                if (isActive) " (deferred until next prepare)" else ""
        )
    }

    private val connectChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            sendEvent("connecting", url)
        }

        override fun onConnectionSuccess() {
            sendEvent("connected", null)
        }

        override fun onConnectionFailed(reason: String) {
            sendEvent("failed", reason)
        }

        override fun onDisconnect() {
            sendEvent("disconnected", null)
        }

        override fun onNewBitrate(bitrate: Long) = Unit

        override fun onAuthError() {
            sendEvent("error", "Auth error")
        }

        override fun onAuthSuccess() = Unit
    }

    private fun sendEvent(type: String, message: String?) {
        runOnUiThread {
            methodChannel?.invokeMethod("onNativeEvent", hashMapOf("type" to type, "message" to message))
        }
    }

    private fun ensureCameraPerms(): Boolean {
        val cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (cam && mic) return true
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), requestPermissionsCode)
        return false
    }

    private fun requestScreenCapturePermission() {
        val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(pm.createScreenCaptureIntent(), requestMediaProjection)
    }

    private fun startForegroundForScreen() {
        val intent = Intent(this, ForegroundStreamService::class.java).apply { action = ForegroundStreamService.ACTION_START }
        startForegroundService(intent)
    }

    private fun stopForegroundForScreen() {
        val intent = Intent(this, ForegroundStreamService::class.java).apply { action = ForegroundStreamService.ACTION_STOP }
        startService(intent)
    }

    private fun prepareStreamIfNeeded(stream: RtmpStream): Boolean {
        if (!videoPrepared) {
            val rotation = if (isPortrait) 90 else 0
            // Камера всегда выдаёт landscape, для portrait используем rotation
            val encWidth = if (isPortrait) maxOf(streamWidth, streamHeight) else streamWidth
            val encHeight = if (isPortrait) minOf(streamWidth, streamHeight) else streamHeight

            val preferredProfile = VideoProfile(
                width = encWidth,
                height = encHeight,
                fps = streamFps,
                bitrate = streamBitrate,
            )
            val profiles = listOf(preferredProfile) + fallbackVideoProfiles()
            var prepared = false
            for (profile in profiles.distinct()) {
                if (stream.prepareVideo(profile.width, profile.height, profile.bitrate, profile.fps, 2, rotation)) {
                    prepared = true
                    break
                }
            }
            if (!prepared) return false
            videoPrepared = true
        }

        if (!audioPrepared) {
            audioPrepared = try {
                stream.prepareAudio(32000, true, 64_000)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "prepareAudio failed", e)
                false
            }
            if (!audioPrepared) return false
        }

        return true
    }

    private data class VideoProfile(val width: Int, val height: Int, val fps: Int, val bitrate: Int)
}
