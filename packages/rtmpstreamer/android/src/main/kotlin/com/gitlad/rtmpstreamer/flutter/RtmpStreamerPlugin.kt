package com.gitlad.rtmpstreamer.flutter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import com.gitlad.rtmpstreamer.RtmpStreamingClient
import com.gitlad.rtmpstreamer.StreamEvent
import com.gitlad.rtmpstreamer.StreamEventListener
import com.gitlad.rtmpstreamer.StreamOrientation
import com.gitlad.rtmpstreamer.StreamSettings
import com.gitlad.rtmpstreamer.StreamSource
import com.gitlad.rtmpstreamer.preview.PreviewSurfaceHolder
import com.gitlad.rtmpstreamer.screen.ScreenShareForegroundService
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry

class RtmpStreamerPlugin :
    FlutterPlugin,
    ActivityAware,
    MethodChannel.MethodCallHandler,
    StreamEventListener,
    PluginRegistry.ActivityResultListener,
    PluginRegistry.RequestPermissionsResultListener {

    private val channelName = "rtmpstreamer"
    private val cameraPreviewViewType = "rtmpstreamer_camera_preview"
    private val requestMediaProjection = 9101
    private val requestPermissionsCode = 9102
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var context: Context
    private var activity: Activity? = null
    private var channel: MethodChannel? = null
    private lateinit var streamingClient: RtmpStreamingClient
    private var pendingPreviewResult: MethodChannel.Result? = null
    private var settingsReceived = false

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        streamingClient = RtmpStreamingClient(context, this)
        channel = MethodChannel(binding.binaryMessenger, channelName).also {
            it.setMethodCallHandler(this)
        }

        binding.platformViewRegistry.registerViewFactory(
            cameraPreviewViewType,
            RtmpCameraPreviewFactory(::onCameraPreviewReady),
        )
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        releaseAll()
        channel?.setMethodCallHandler(null)
        channel = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startPreviewCamera" -> {
                if (!ensureCameraPermissions()) {
                    pendingPreviewResult = result
                    return
                }
                mainHandler.postDelayed({
                    result.success(startPreviewCamera())
                }, 500)
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

            "setCameraOrientation" -> {
                val args = call.arguments as? Map<*, *>
                if (args == null) {
                    result.error("BAD_ARGS", "Orientation map required", null)
                } else {
                    val orientation = StreamOrientation.fromChannelValue(args["orientation"] as? String)
                    val rotation = (args["rotationDegrees"] as? Number)
                        ?.toInt()
                        ?: orientation.defaultRotationDegrees
                    streamingClient.setCameraOrientation(orientation, rotation)
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

    override fun onStreamEvent(event: StreamEvent) {
        mainHandler.post {
            channel?.invokeMethod(
                "onNativeEvent",
                hashMapOf("type" to event.type, "message" to event.message),
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != requestMediaProjection || resultCode != Activity.RESULT_OK || data == null) {
            return false
        }

        startForegroundForScreen()
        mainHandler.postDelayed({
            val projection = runCatching {
                val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
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
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ): Boolean {
        if (requestCode != requestPermissionsCode) return false
        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        val result = pendingPreviewResult ?: return true
        pendingPreviewResult = null
        result.success(if (granted) startPreviewCamera() else false)
        return true
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
        mainHandler.post {
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
        if (::streamingClient.isInitialized) {
            streamingClient.release()
        }
        settingsReceived = false
        stopForegroundForScreen()
    }

    private fun ensureCameraPermissions(): Boolean {
        val currentActivity = activity ?: return false
        val cameraGranted = currentActivity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val micGranted = currentActivity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted && micGranted) return true

        currentActivity.requestPermissions(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            requestPermissionsCode,
        )
        return false
    }

    private fun requestScreenCapturePermission() {
        val currentActivity = activity ?: return
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        currentActivity.startActivityForResult(manager.createScreenCaptureIntent(), requestMediaProjection)
    }

    private fun startForegroundForScreen() {
        val intent = Intent(context, ScreenShareForegroundService::class.java).apply {
            action = ScreenShareForegroundService.ACTION_START
            activity?.javaClass?.name?.let {
                putExtra(ScreenShareForegroundService.EXTRA_OPEN_ACTIVITY_CLASS, it)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopForegroundForScreen() {
        if (!::context.isInitialized) return
        val intent = Intent(context, ScreenShareForegroundService::class.java).apply {
            action = ScreenShareForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun Map<*, *>.toStreamSettings(): StreamSettings {
        val orientation = StreamOrientation.fromChannelValue(this["orientation"] as? String)
        return StreamSettings(
            width = (this["width"] as? Number)?.toInt()?.takeIf { it > 0 } ?: 1280,
            height = (this["height"] as? Number)?.toInt()?.takeIf { it > 0 } ?: 720,
            bitrate = (this["bitrate"] as? Number)?.toInt()?.takeIf { it > 0 } ?: 4_000_000,
            fps = (this["fps"] as? Number)?.toInt()?.takeIf { it > 0 } ?: 30,
            orientation = orientation,
            rotationDegrees = (this["rotationDegrees"] as? Number)
                ?.toInt()
                ?: orientation.defaultRotationDegrees,
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
