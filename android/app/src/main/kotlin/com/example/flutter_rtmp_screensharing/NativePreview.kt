package com.example.flutter_rtmp_screensharing

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import android.view.View
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
            synchronized(NativePreviewHolder.lock) {
                if (kind == "camera") {
                    NativePreviewHolder.cameraReady = false
                    NativePreviewHolder.cameraWidth = 0
                    NativePreviewHolder.cameraHeight = 0
                    NativePreviewHolder.cameraAttached = false
                } else {
                    NativePreviewHolder.screenReady = false
                    NativePreviewHolder.screenWidth = 0
                    NativePreviewHolder.screenHeight = 0
                }
                NativePreviewHolder.lock.notifyAll()
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
                synchronized(NativePreviewHolder.lock) {
                    if (kind == "camera") {
                        NativePreviewHolder.cameraAttached = true
                    }
                    NativePreviewHolder.lock.notifyAll()
                }
                maybeDispatchCameraReady()
            }

            override fun onViewDetachedFromWindow(v: View) {
                synchronized(NativePreviewHolder.lock) {
                    if (kind == "camera") {
                        NativePreviewHolder.cameraAttached = false
                        NativePreviewHolder.cameraReady = false
                    }
                    NativePreviewHolder.lock.notifyAll()
                }
            }
        })

        synchronized(NativePreviewHolder.lock) {
            if (kind == "camera") {
                NativePreviewHolder.cameraView = textureView
                NativePreviewHolder.cameraReady = false
                NativePreviewHolder.cameraWidth = textureView.width
                NativePreviewHolder.cameraHeight = textureView.height
                NativePreviewHolder.cameraAttached = textureView.isAttachedToWindow
            } else {
                NativePreviewHolder.screenView = textureView
                NativePreviewHolder.screenReady = false
                NativePreviewHolder.screenWidth = textureView.width
                NativePreviewHolder.screenHeight = textureView.height
            }
        }
    }

    override fun getView(): View = textureView

    override fun dispose() {
        textureView.surfaceTextureListener = null
        synchronized(NativePreviewHolder.lock) {
            if (kind == "camera") {
                NativePreviewHolder.cameraReady = false
                NativePreviewHolder.cameraWidth = 0
                NativePreviewHolder.cameraHeight = 0
                NativePreviewHolder.cameraAttached = false
                if (NativePreviewHolder.cameraView === textureView) NativePreviewHolder.cameraView = null
            } else {
                NativePreviewHolder.screenReady = false
                NativePreviewHolder.screenWidth = 0
                NativePreviewHolder.screenHeight = 0
                if (NativePreviewHolder.screenView === textureView) NativePreviewHolder.screenView = null
            }
            NativePreviewHolder.lock.notifyAll()
        }
    }

    private fun updateState(width: Int, height: Int) {
        synchronized(NativePreviewHolder.lock) {
            val ready = textureView.isAvailable && width > 0 && height > 0
            if (kind == "camera") {
                NativePreviewHolder.cameraReady = ready
                NativePreviewHolder.cameraWidth = width
                NativePreviewHolder.cameraHeight = height
                NativePreviewHolder.cameraAttached = textureView.isAttachedToWindow
            } else {
                NativePreviewHolder.screenReady = ready
                NativePreviewHolder.screenWidth = width
                NativePreviewHolder.screenHeight = height
            }
            NativePreviewHolder.lock.notifyAll()
        }
    }

    private fun updateAspectRatio(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (kind == "camera") {
            val targetWidth = NativePreviewHolder.targetPreviewWidth
            val targetHeight = NativePreviewHolder.targetPreviewHeight
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
            val shouldDispatch = synchronized(NativePreviewHolder.lock) {
                val isStable = textureView.isAvailable &&
                    textureView.isAttachedToWindow &&
                    textureView.width > 0 &&
                    textureView.height > 0

                if (isStable && !NativePreviewHolder.previewStartRequested) {
                    NativePreviewHolder.previewStartRequested = true
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
