package com.example.flutter_rtmp_screensharing

import android.view.TextureView

object NativePreviewHolder {
    @Volatile var cameraView: TextureView? = null
    @Volatile var screenView: TextureView? = null
    @Volatile var targetPreviewWidth: Int = 1280
    @Volatile var targetPreviewHeight: Int = 720
    @Volatile var cameraReady: Boolean = false
    @Volatile var screenReady: Boolean = false
    @Volatile var cameraWidth: Int = 0
    @Volatile var cameraHeight: Int = 0
    @Volatile var screenWidth: Int = 0
    @Volatile var screenHeight: Int = 0
    @Volatile var cameraAttached: Boolean = false
    @Volatile var previewStartRequested: Boolean = false
    val lock = Object()
}
