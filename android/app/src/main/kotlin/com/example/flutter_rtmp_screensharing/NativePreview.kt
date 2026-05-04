package com.example.flutter_rtmp_screensharing

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import android.view.View
import com.gitlad.rtmpstreamer.preview.AspectRatioTextureView
import com.gitlad.rtmpstreamer.preview.PreviewSurfaceHolder
import io.flutter.plugin.platform.PlatformView

class NativePreview(
    context: Context,
    private val kind: String,
    private val onCameraPreviewReady: (() -> Unit)? = null,
) : PlatformView {

    private val textureView = AspectRatioTextureView(context)

    private val listener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            updateAspectRatio(width, height)
            updateState(width, height)
            if (width > 0 && height > 0) {
                maybeDispatchCameraReady()
            }
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            updateAspectRatio(width, height)
            updateState(width, height)
            if (width > 0 && height > 0) {
                maybeDispatchCameraReady()
            }
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            synchronized(PreviewSurfaceHolder.lock) {
                if (kind == "camera") {
                    PreviewSurfaceHolder.cameraReady = false
                    PreviewSurfaceHolder.cameraWidth = 0
                    PreviewSurfaceHolder.cameraHeight = 0
                    PreviewSurfaceHolder.cameraAttached = false
                } else {
                    PreviewSurfaceHolder.screenReady = false
                    PreviewSurfaceHolder.screenWidth = 0
                    PreviewSurfaceHolder.screenHeight = 0
                }
                PreviewSurfaceHolder.lock.notifyAll()
            }
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }

    init {
        textureView.surfaceTextureListener = listener
        textureView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val w = textureView.width
            val h = textureView.height
            updateAspectRatio(w, h)
            updateState(w, h)
            if (w > 0 && h > 0) {
                maybeDispatchCameraReady()
            }
        }

        textureView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                synchronized(PreviewSurfaceHolder.lock) {
                    if (kind == "camera") {
                        PreviewSurfaceHolder.cameraAttached = true
                    }
                    PreviewSurfaceHolder.lock.notifyAll()
                }
                maybeDispatchCameraReady()
            }

            override fun onViewDetachedFromWindow(v: View) {
                synchronized(PreviewSurfaceHolder.lock) {
                    if (kind == "camera") {
                        PreviewSurfaceHolder.cameraAttached = false
                        PreviewSurfaceHolder.cameraReady = false
                    }
                    PreviewSurfaceHolder.lock.notifyAll()
                }
            }
        })

        synchronized(PreviewSurfaceHolder.lock) {
            if (kind == "camera") {
                PreviewSurfaceHolder.cameraView = textureView
                PreviewSurfaceHolder.cameraReady = false
                PreviewSurfaceHolder.cameraWidth = textureView.width
                PreviewSurfaceHolder.cameraHeight = textureView.height
                PreviewSurfaceHolder.cameraAttached = textureView.isAttachedToWindow
            } else {
                PreviewSurfaceHolder.screenView = textureView
                PreviewSurfaceHolder.screenReady = false
                PreviewSurfaceHolder.screenWidth = textureView.width
                PreviewSurfaceHolder.screenHeight = textureView.height
            }
        }
    }

    override fun getView(): View = textureView

    override fun dispose() {
        textureView.surfaceTextureListener = null
        synchronized(PreviewSurfaceHolder.lock) {
            if (kind == "camera") {
                PreviewSurfaceHolder.cameraReady = false
                PreviewSurfaceHolder.cameraWidth = 0
                PreviewSurfaceHolder.cameraHeight = 0
                PreviewSurfaceHolder.cameraAttached = false
                if (PreviewSurfaceHolder.cameraView === textureView) PreviewSurfaceHolder.cameraView = null
            } else {
                PreviewSurfaceHolder.screenReady = false
                PreviewSurfaceHolder.screenWidth = 0
                PreviewSurfaceHolder.screenHeight = 0
                if (PreviewSurfaceHolder.screenView === textureView) PreviewSurfaceHolder.screenView = null
            }
            PreviewSurfaceHolder.lock.notifyAll()
        }
    }

    private fun updateState(width: Int, height: Int) {
        synchronized(PreviewSurfaceHolder.lock) {
            val ready = textureView.isAvailable && width > 0 && height > 0
            if (kind == "camera") {
                PreviewSurfaceHolder.cameraReady = ready
                PreviewSurfaceHolder.cameraWidth = width
                PreviewSurfaceHolder.cameraHeight = height
                PreviewSurfaceHolder.cameraAttached = textureView.isAttachedToWindow
            } else {
                PreviewSurfaceHolder.screenReady = ready
                PreviewSurfaceHolder.screenWidth = width
                PreviewSurfaceHolder.screenHeight = height
            }
            PreviewSurfaceHolder.lock.notifyAll()
        }
    }

    private fun updateAspectRatio(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (kind == "camera") {
            val targetWidth = PreviewSurfaceHolder.targetPreviewWidth
            val targetHeight = PreviewSurfaceHolder.targetPreviewHeight
            if (targetWidth > 0 && targetHeight > 0) {
                textureView.setAspectRatio(targetWidth, targetHeight)
            } else if (height >= width) {
                textureView.setAspectRatio(9, 16)
            } else {
                textureView.setAspectRatio(16, 9)
            }
        } else {
            textureView.setAspectRatio(width, height)
        }
    }

    private fun maybeDispatchCameraReady() {
        if (kind != "camera" || onCameraPreviewReady == null) return

        textureView.postDelayed({
            val shouldDispatch = synchronized(PreviewSurfaceHolder.lock) {
                val isStable = textureView.isAvailable &&
                    textureView.isAttachedToWindow &&
                    textureView.width > 0 &&
                    textureView.height > 0

                if (isStable && !PreviewSurfaceHolder.previewStartRequested) {
                    PreviewSurfaceHolder.previewStartRequested = true
                    true
                } else {
                    false
                }
            }

            if (shouldDispatch) {
                Log.d("RTMP", "Signaling camera ready: ${textureView.width}x${textureView.height}")
                onCameraPreviewReady.invoke()
            }
        }, 100)
    }
}
