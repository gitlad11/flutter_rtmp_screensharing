package com.gitlad.rtmpstreamer

data class StreamEvent(
    val type: String,
    val message: String? = null,
)

fun interface StreamEventListener {
    fun onStreamEvent(event: StreamEvent)
}
