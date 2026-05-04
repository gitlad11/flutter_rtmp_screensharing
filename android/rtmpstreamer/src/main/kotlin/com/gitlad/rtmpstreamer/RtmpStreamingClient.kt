package com.gitlad.rtmpstreamer

import android.content.Context
import android.media.projection.MediaProjection
import android.util.Log
import android.view.TextureView
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.ScreenSource
import com.pedro.library.rtmp.RtmpStream

class RtmpStreamingClient(
    private val context: Context,
    private val listener: StreamEventListener,
) {
    private var stream: RtmpStream? = null
    private var cameraSource: Camera2Source? = null
    private var microphoneSource: MicrophoneSource? = null
    private var rawFrameListener: RawFrameListener? = null
    private val rawFrameRecordController = RawFrameRecordController { rawFrameListener }

    var source: StreamSource = StreamSource.CAMERA
        private set

    var isMuted: Boolean = false
        private set

    private var settings = StreamSettings()
    private var videoPrepared = false
    private var audioPrepared = false

    fun updateSettings(next: StreamSettings) {
        settings = next
        val active = stream?.isStreaming == true || stream?.isOnPreview == true
        if (!active) {
            videoPrepared = false
            audioPrepared = false
        }

        Log.d(
            TAG,
            "Updated stream settings: ${next.width}x${next.height} @ ${next.fps}fps ${next.bitrate}bps" +
                if (active) " (deferred until next prepare)" else "",
        )
    }

    fun startPreview(view: TextureView): Boolean {
        val stream = initStream()
        if (!prepareStreamIfNeeded(stream)) return false

        return runCatching {
            if (view.width <= 0 || view.height <= 0) {
                Log.e(TAG, "Cannot start preview: View dimensions are zero (${view.width}x${view.height})")
                return false
            }

            if (!stream.isOnPreview) {
                stream.startPreview(view)
            }
            source = StreamSource.CAMERA
            emit("preview_started", source.channelValue)
            true
        }.onFailure {
            Log.e(TAG, "Preview failed", it)
        }.getOrDefault(false)
    }

    fun startStream(url: String): Boolean {
        val stream = initStream()
        return runCatching {
            if (!stream.isStreaming) {
                if (!prepareStreamIfNeeded(stream)) return false
                stream.startStream(url)
                emit("started", source.channelValue)
            }
            true
        }.onFailure {
            Log.e(TAG, "Start stream failed", it)
        }.getOrDefault(false)
    }

    fun stopStream() {
        stream?.stopStream()
        emit("stopped")
    }

    fun switchToCamera(): Boolean {
        val stream = stream ?: return false
        val camera = cameraSource ?: Camera2Source(context).also { cameraSource = it }
        return runCatching {
            stream.changeVideoSource(camera)
            source = StreamSource.CAMERA
            emit("source_changed", source.channelValue)
            true
        }.onFailure {
            Log.e(TAG, "Switch to camera failed", it)
            emit("failed", "Failed to switch to camera")
        }.getOrDefault(false)
    }

    fun switchToScreen(projection: MediaProjection): Boolean {
        val stream = stream ?: return false
        return runCatching {
            val screenSource = ScreenSource(context, projection)
            stream.changeVideoSource(screenSource)
            source = StreamSource.SCREEN
            emit("source_changed", source.channelValue)
            true
        }.onFailure {
            Log.e(TAG, "Switch to screen failed", it)
            emit("failed", "Failed to start screen sharing")
        }.getOrDefault(false)
    }

    fun switchCamera(): Boolean {
        val stream = stream ?: return false
        if (source != StreamSource.CAMERA) return false

        return runCatching {
            (stream.videoSource as? Camera2Source ?: cameraSource)?.switchCamera()
            true
        }.getOrDefault(false)
    }

    fun toggleMute(): Boolean {
        val microphone = microphoneSource ?: return isMuted
        isMuted = !isMuted
        if (isMuted) microphone.mute() else microphone.unMute()
        return isMuted
    }

    fun setRawFrameListener(listener: RawFrameListener?) {
        rawFrameListener = listener
        stream?.setRecordController(rawFrameRecordController)
    }

    fun isStreaming(): Boolean = stream?.isStreaming == true

    fun isOnPreview(): Boolean = stream?.isOnPreview == true

    fun release() {
        runCatching { stream?.release() }
        stream = null
        cameraSource = null
        microphoneSource = null
        videoPrepared = false
        audioPrepared = false
        source = StreamSource.CAMERA
        isMuted = false
    }

    private fun initStream(): RtmpStream {
        stream?.let { return it }
        val camera = Camera2Source(context).also { cameraSource = it }
        val microphone = MicrophoneSource().also { microphoneSource = it }
        return RtmpStream(context, connectChecker, camera, microphone).also {
            it.setRecordController(rawFrameRecordController)
            stream = it
        }
    }

    private fun prepareStreamIfNeeded(stream: RtmpStream): Boolean {
        if (!videoPrepared) {
            val rotation = if (settings.isPortrait) 90 else 0
            val encWidth = if (settings.isPortrait) maxOf(settings.width, settings.height) else settings.width
            val encHeight = if (settings.isPortrait) minOf(settings.width, settings.height) else settings.height
            val preferred = VideoProfile(encWidth, encHeight, settings.fps, settings.bitrate)

            val prepared = (listOf(preferred) + fallbackVideoProfiles()).distinct().any { profile ->
                stream.prepareVideo(profile.width, profile.height, profile.bitrate, profile.fps, 2, rotation)
            }
            if (!prepared) return false
            videoPrepared = true
        }

        if (!audioPrepared) {
            audioPrepared = runCatching {
                stream.prepareAudio(32000, true, 64_000)
            }.onFailure {
                Log.e(TAG, "prepareAudio failed", it)
            }.getOrDefault(false)
            if (!audioPrepared) return false
        }

        return true
    }

    private fun fallbackVideoProfiles(): List<VideoProfile> {
        return listOf(
            VideoProfile(width = 1280, height = 720, fps = 30, bitrate = 2_000_000),
            VideoProfile(width = 640, height = 480, fps = 30, bitrate = 1_200_000),
        )
    }

    private val connectChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) = emit("connecting", url)
        override fun onConnectionSuccess() = emit("connected")
        override fun onConnectionFailed(reason: String) = emit("failed", reason)
        override fun onDisconnect() = emit("disconnected")
        override fun onNewBitrate(bitrate: Long) = emit("bitrate", bitrate.toString())
        override fun onAuthError() = emit("error", "Auth error")
        override fun onAuthSuccess() = emit("auth_success")
    }

    private fun emit(type: String, message: String? = null) {
        listener.onStreamEvent(StreamEvent(type, message))
    }

    private companion object {
        const val TAG = "RTMP"
    }
}
