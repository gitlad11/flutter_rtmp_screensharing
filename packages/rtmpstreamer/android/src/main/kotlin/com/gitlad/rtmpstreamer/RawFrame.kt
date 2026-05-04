package com.gitlad.rtmpstreamer

import android.media.MediaCodec
import android.media.MediaFormat

data class RawFrame(
    val type: RawFrameType,
    val data: ByteArray,
    val offset: Int,
    val size: Int,
    val presentationTimeUs: Long,
    val flags: Int,
) {
    val isKeyFrame: Boolean
        get() = flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0

    val isCodecConfig: Boolean
        get() = flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
}

enum class RawFrameType {
    VIDEO,
    AUDIO,
}

interface RawFrameListener {
    fun onVideoFormat(format: MediaFormat) = Unit
    fun onAudioFormat(format: MediaFormat) = Unit
    fun onFrame(frame: RawFrame)
}
