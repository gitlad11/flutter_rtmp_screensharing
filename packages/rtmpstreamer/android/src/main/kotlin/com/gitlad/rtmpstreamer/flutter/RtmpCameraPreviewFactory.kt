package com.gitlad.rtmpstreamer.flutter

import android.content.Context
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class RtmpCameraPreviewFactory(
    private val onCameraPreviewReady: () -> Unit,
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        return RtmpCameraPreviewView(
            context = context,
            kind = "camera",
            onCameraPreviewReady = onCameraPreviewReady,
        )
    }
}
