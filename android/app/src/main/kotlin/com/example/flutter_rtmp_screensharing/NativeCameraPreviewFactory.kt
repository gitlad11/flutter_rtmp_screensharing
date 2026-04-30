package com.example.flutter_rtmp_screensharing

import android.content.Context
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class NativeCameraPreviewFactory(
    private val onCameraPreviewReady: () -> Unit,
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        return NativePreview(
            context = context,
            kind = "camera",
            onCameraPreviewReady = onCameraPreviewReady,
        )
    }
}
