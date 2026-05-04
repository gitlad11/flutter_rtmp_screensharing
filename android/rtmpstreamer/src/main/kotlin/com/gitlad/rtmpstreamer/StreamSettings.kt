package com.gitlad.rtmpstreamer

data class StreamSettings(
    val width: Int = 1280,
    val height: Int = 720,
    val bitrate: Int = 4_000_000,
    val fps: Int = 30,
    val orientation: StreamOrientation = StreamOrientation.LANDSCAPE,
) {
    val isPortrait: Boolean
        get() = orientation == StreamOrientation.PORTRAIT
}

enum class StreamOrientation {
    LANDSCAPE,
    PORTRAIT,
}

data class VideoProfile(
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int,
)

enum class StreamSource(val channelValue: String) {
    CAMERA("camera"),
    SCREEN("screen");

    companion object {
        fun fromChannelValue(value: String?): StreamSource {
            return entries.firstOrNull { it.channelValue == value } ?: CAMERA
        }
    }
}
