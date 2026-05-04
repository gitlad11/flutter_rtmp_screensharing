package com.gitlad.rtmpstreamer

data class StreamSettings(
    val width: Int = 1280,
    val height: Int = 720,
    val bitrate: Int = 4_000_000,
    val fps: Int = 30,
    val orientation: StreamOrientation = StreamOrientation.LANDSCAPE,
    val rotationDegrees: Int = orientation.defaultRotationDegrees,
) {
    val isPortrait: Boolean
        get() = orientation == StreamOrientation.PORTRAIT
}

enum class StreamOrientation(val channelValue: String, val defaultRotationDegrees: Int) {
    LANDSCAPE("landscape", 0),
    PORTRAIT("portrait", 90);

    companion object {
        fun fromChannelValue(value: String?): StreamOrientation {
            return entries.firstOrNull { it.channelValue == value } ?: LANDSCAPE
        }
    }
}

fun normalizeRotationDegrees(value: Int): Int {
    return when (((value % 360) + 360) % 360) {
        0 -> 0
        90 -> 90
        180 -> 180
        270 -> 270
        else -> 0
    }
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
