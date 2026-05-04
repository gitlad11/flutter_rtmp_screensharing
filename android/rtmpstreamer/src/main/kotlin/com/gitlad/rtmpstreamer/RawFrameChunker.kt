package com.gitlad.rtmpstreamer

import android.media.MediaFormat

data class RawFrameChunk(
    val index: Long,
    val frames: List<RawFrame>,
    val startedAtUs: Long,
    val endedAtUs: Long,
    val bytes: Int,
) {
    val durationUs: Long
        get() = endedAtUs - startedAtUs
}

interface RawFrameChunkListener {
    fun onVideoFormat(format: MediaFormat) = Unit
    fun onAudioFormat(format: MediaFormat) = Unit
    fun onChunk(chunk: RawFrameChunk)
}

class RawFrameChunker(
    private val maxDurationUs: Long = 2_000_000L,
    private val maxBytes: Int = 2 * 1024 * 1024,
    private val listener: RawFrameChunkListener,
) : RawFrameListener {
    private val frames = mutableListOf<RawFrame>()
    private var chunkIndex = 0L
    private var startedAtUs = -1L
    private var endedAtUs = -1L
    private var bytes = 0
    private var pendingKeyFrameSplit = false

    override fun onVideoFormat(format: MediaFormat) {
        listener.onVideoFormat(format)
    }

    override fun onAudioFormat(format: MediaFormat) {
        listener.onAudioFormat(format)
    }

    override fun onFrame(frame: RawFrame) {
        if (startedAtUs < 0) {
            startedAtUs = frame.presentationTimeUs
        }

        if (shouldFlushBefore(frame)) {
            flush()
            startedAtUs = frame.presentationTimeUs
        }

        frames += frame
        endedAtUs = frame.presentationTimeUs
        bytes += frame.size

        if (frame.type == RawFrameType.VIDEO && frame.isKeyFrame && shouldPreferKeyFrameSplit()) {
            pendingKeyFrameSplit = true
        }

        if (shouldFlushAfter()) {
            flush()
        }
    }

    fun flush() {
        if (frames.isEmpty()) return

        listener.onChunk(
            RawFrameChunk(
                index = chunkIndex++,
                frames = frames.toList(),
                startedAtUs = startedAtUs,
                endedAtUs = endedAtUs,
                bytes = bytes,
            ),
        )

        frames.clear()
        startedAtUs = -1L
        endedAtUs = -1L
        bytes = 0
        pendingKeyFrameSplit = false
    }

    private fun shouldFlushBefore(frame: RawFrame): Boolean {
        if (frames.isEmpty()) return false
        if (frame.type != RawFrameType.VIDEO || !frame.isKeyFrame) return false

        val duration = frame.presentationTimeUs - startedAtUs
        return duration >= maxDurationUs || bytes >= maxBytes || pendingKeyFrameSplit
    }

    private fun shouldFlushAfter(): Boolean {
        if (frames.isEmpty()) return false
        return bytes >= maxBytes && !containsVideoFrames()
    }

    private fun shouldPreferKeyFrameSplit(): Boolean {
        if (frames.isEmpty()) return false
        val duration = endedAtUs - startedAtUs
        return duration >= maxDurationUs
    }

    private fun containsVideoFrames(): Boolean {
        return frames.any { it.type == RawFrameType.VIDEO }
    }
}
