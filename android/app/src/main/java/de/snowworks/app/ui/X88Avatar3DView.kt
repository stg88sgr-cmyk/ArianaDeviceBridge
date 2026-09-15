package de.snowworks.app.ui

import android.content.Context
import android.os.SystemClock
import android.view.Choreographer
import android.view.SurfaceView
import com.google.android.filament.View
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.io.File
import java.nio.ByteBuffer

/**
 * ARIANA X-88 V11 glTF surface.
 *
 * This view is intentionally optional. If no GLB is bundled (normal source builds)
 * or the model cannot be parsed, the host keeps the existing procedural avatar.
 * A rigged GLB can later replace the interim static GLB without changing this API.
 */
class X88Avatar3DView(context: Context) : SurfaceView(context), Choreographer.FrameCallback {
    companion object {
        const val ASSET_MODEL_PATH = "ariana/ariana_x88.glb"
        const val INTERNAL_MODEL_PATH = "ariana/ariana_x88.glb"

        init {
            Utils.init()
        }
    }

    var onModelReady: (() -> Unit)? = null
    var onModelError: ((Throwable) -> Unit)? = null

    var mode: X88AvatarView.Mode = X88AvatarView.Mode.IDLE
        set(value) {
            field = value
            applyModeAnimation()
        }

    private val modelViewer = ModelViewer(this)
    private val choreographer: Choreographer = Choreographer.getInstance()
    private var loadStarted = false
    private var modelLoaded = false
    private var framePosted = false

    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        setOnTouchListener(modelViewer)
        configureForMobile()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loadModelIfAvailable()
        postFrameIfNeeded()
    }

    override fun onDetachedFromWindow() {
        framePosted = false
        choreographer.removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        framePosted = false
        if (!isAttachedToWindow) return
        if (modelLoaded) {
            modelViewer.render(frameTimeNanos)
        }
        postFrameIfNeeded()
    }

    private fun postFrameIfNeeded() {
        if (!framePosted && isAttachedToWindow) {
            framePosted = true
            choreographer.postFrameCallback(this)
        }
    }

    private fun configureForMobile() {
        val filamentView = modelViewer.view
        filamentView.renderQuality = filamentView.renderQuality.apply {
            hdrColorBuffer = View.QualityLevel.MEDIUM
        }
        filamentView.dynamicResolutionOptions = filamentView.dynamicResolutionOptions.apply {
            enabled = true
            quality = View.QualityLevel.MEDIUM
        }
        filamentView.multiSampleAntiAliasingOptions = filamentView.multiSampleAntiAliasingOptions.apply {
            enabled = true
        }
        filamentView.antiAliasing = View.AntiAliasing.FXAA
        filamentView.ambientOcclusionOptions = filamentView.ambientOcclusionOptions.apply {
            enabled = true
        }
        filamentView.bloomOptions = filamentView.bloomOptions.apply {
            enabled = true
        }
    }

    private fun loadModelIfAvailable() {
        if (loadStarted) return
        loadStarted = true

        Thread({
            runCatching { readModelBytes() }
                .onSuccess { bytes ->
                    post {
                        if (!isAttachedToWindow) return@post
                        runCatching {
                            modelViewer.loadModelGlb(ByteBuffer.wrap(bytes))
                            checkNotNull(modelViewer.asset) { "GLB konnte nicht als FilamentAsset geladen werden." }
                            modelViewer.transformToUnitCube()
                            modelLoaded = true
                            applyModeAnimation()
                        }.onSuccess {
                            onModelReady?.invoke()
                        }.onFailure { error ->
                            modelLoaded = false
                            onModelError?.invoke(error)
                        }
                    }
                }
                .onFailure { error ->
                    post {
                        modelLoaded = false
                        onModelError?.invoke(error)
                    }
                }
        }, "ArianaX88ModelLoader").apply {
            isDaemon = true
            start()
        }
    }

    private fun readModelBytes(): ByteArray {
        val internalModel = File(context.filesDir, INTERNAL_MODEL_PATH)
        if (internalModel.isFile && internalModel.length() > 0L) {
            return internalModel.readBytes()
        }
        return context.assets.open(ASSET_MODEL_PATH).use { it.readBytes() }
    }

    private fun applyModeAnimation() {
        if (!modelLoaded) return
        val animator = modelViewer.animator ?: return
        val names = List(animator.animationCount) { index -> animator.getAnimationName(index) ?: "" }
        val index = X88AvatarAnimationPolicy.pickAnimationIndex(mode, names)

        if (index == null) {
            modelViewer.autoPlayAnimations = false
            if (mode == X88AvatarView.Mode.STOPPED) {
                animator.resetBoneMatrices()
                animator.updateBoneMatrices()
            }
            return
        }

        modelViewer.activeAnimationIndex = index
        modelViewer.autoPlayAnimations = true
    }
}
