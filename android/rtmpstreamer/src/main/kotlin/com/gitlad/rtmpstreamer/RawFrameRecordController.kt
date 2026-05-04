package com.gitlad.rtmpstreamer

import android.media.MediaCodec
import android.media.MediaFormat
import com.pedro.library.base.recording.BaseRecordController
import com.pedro.library.base.recording.RecordController
import java.io.FileDescriptor
import java.nio.ByteBuffer

internal class RawFrameRecordController(
    private val listenerProvider: () -> RawFrameListener?,
) : BaseRecordController() {

    override fun startRecord(
        path: String,
        listener: RecordController.Listener?,
        tracks: RecordController.RecordTracks,
    ) = Unit

    override fun startRecord(
        fd: FileDescriptor,
        listener: RecordController.Listener?,
        tracks: RecordController.RecordTracks,
    ) = Unit

    override fun stopRecord() = Unit

    override fun recordVideo(byteBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        dispatchFrame(RawFrameType.VIDEO, byteBuffer, info)
    }

    override fun recordAudio(byteBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        dispatchFrame(RawFrameType.AUDIO, byteBuffer, info)
    }

    override fun setVideoFormat(mediaFormat: MediaFormat) {
        listenerProvider()?.onVideoFormat(mediaFormat)
    }

    override fun setAudioFormat(mediaFormat: MediaFormat) {
        listenerProvider()?.onAudioFormat(mediaFormat)
    }

    override fun resetFormats() = Unit

    private fun dispatchFrame(
        type: RawFrameType,
        byteBuffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ) {
        val listener = listenerProvider() ?: return
        if (info.size <= 0) return

        val duplicate = byteBuffer.duplicate()
        duplicate.position(info.offset)
        duplicate.limit(info.offset + info.size)

        val data = ByteArray(info.size)
        duplicate.get(data)

        listener.onFrame(
            RawFrame(
                type = type,
                data = data,
                offset = 0,
                size = data.size,
                presentationTimeUs = info.presentationTimeUs,
                flags = info.flags,
            ),
        )
    }
}
