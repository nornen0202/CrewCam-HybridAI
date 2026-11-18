package com.galaxycrew.crewcam

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.io.File

internal data class Original(val run: String, val name: String, val uri: Uri,
                             val durationMs: Long, val bytes: Long)

internal class PhoneCamera(private val context: Context, private val owner: LifecycleOwner,
                          val preview: PreviewView, private val note: (String) -> Unit,
                          private val finished: (Original?) -> Unit) {
    private val main = ContextCompat.getMainExecutor(context)
    private var camera: Camera? = null
    private var bound = emptyList<UseCase>()
    private var refreshAfterRecording = false
    private var bindingGeneration = 0
    private var video: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var pending: CompletableDeferred<Frame>? = null
    private var lastSensor = 0L
    private var barrier = 0L
    private var skip = 0
    val canWiden get() = camera?.cameraInfo?.zoomState?.value?.let { it.zoomRatio > it.minZoomRatio + .05f } == true
    val isRecording get() = recording != null

    init {
        preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        preview.scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    private fun onImage(image: ImageProxy) {
        try {
            val timestamp = image.imageInfo.timestamp
            lastSensor = maxOf(lastSensor, timestamp)
            val waiter = pending ?: return
            if (timestamp <= barrier || ++skip < 3) return
            val raw = image.toBitmap()
            val rect = image.cropRect
            val scale = minOf(1f, 640f / maxOf(rect.width(), rect.height()))
            val transform = Matrix().apply {
                setScale(scale, scale)
                postRotate(image.imageInfo.rotationDegrees.toFloat())
            }
            val framed = Bitmap.createBitmap(raw, rect.left, rect.top, rect.width(), rect.height(), transform, true)
            if (raw !== framed) raw.recycle()
            pending = null
            waiter.complete(Frame(framed, timestamp, "live camera"))
        } catch (e: Exception) {
            pending?.completeExceptionally(e)
            pending = null
        } finally {
            image.close()
        }
    }

    fun bind() {
        val generation = ++bindingGeneration
        preview.doOnLayout {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    val provider = future.get()
                    if (owner.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.DESTROYED || generation != bindingGeneration) return@addListener
                    val display = preview.display ?: return@addListener
                    val viewport = preview.viewPort ?: return@addListener
                    val zoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio
                    if (bound.isNotEmpty()) { provider.unbind(*bound.toTypedArray()); camera = null; video = null; bound = emptyList() }
                    val live = Preview.Builder().setTargetRotation(display.rotation).build()
                    live.setSurfaceProvider(preview.surfaceProvider)
                    val recorder = Recorder.Builder().setQualitySelector(QualitySelector.fromOrderedList(
                        listOf(Quality.HD, Quality.FHD), FallbackStrategy.lowerQualityOrHigherThan(Quality.HD))).build()
                    val capture = VideoCapture.withOutput(recorder)
                    capture.targetRotation = display.rotation
                    val analyzer = ImageAnalysis.Builder().setTargetRotation(display.rotation)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analyzer.setAnalyzer(main, ::onImage)
                    val group = UseCaseGroup.Builder().setViewPort(viewport).addUseCase(live).addUseCase(capture).addUseCase(analyzer)
                    camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group.build())
                    if (zoom != null) camera?.cameraControl?.setZoomRatio(zoom)
                    bound = listOf(live, capture, analyzer)
                    video = capture
                } catch (e: Exception) { note("Camera could not start · " + e.javaClass.simpleName) }
            }, main)
        }
    }

    fun updateViewport() {
        if (isRecording) { refreshAfterRecording = true; return }
        bind()
    }

    suspend fun nextFrame(): Frame {
        check(camera != null && pending == null) { "Camera is not ready" }
        val waiter = CompletableDeferred<Frame>()
        pending = waiter
        barrier = lastSensor
        skip = 0
        try { return withTimeout(5500) { waiter.await() } }
        finally { if (pending === waiter) pending = null }
    }

    fun zoom(factor: Float) {
        val state = camera?.cameraInfo?.zoomState?.value ?: return
        camera?.cameraControl?.setZoomRatio((state.zoomRatio * factor).coerceIn(state.minZoomRatio, state.maxZoomRatio))
    }

    fun record(run: String, role: String) {
        recording?.let { it.stop(); return }
        val capture = video ?: return note("Wait for the camera preview.")
        val name = "CrewCam-" + run.take(8) + "-" + role + "-" + System.currentTimeMillis() + ".mp4"
        var legacy: File? = null
        val prepare = if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CrewCam")
            }
            capture.output.prepareRecording(context, MediaStoreOutputOptions.Builder(context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build())
        } else {
            val dir = File(context.getExternalFilesDir(null), "originals").apply { mkdirs() }
            val file = File(dir, name)
            legacy = file
            capture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
        }
        recording = prepare.start(main) { event ->
            when (event) {
                is VideoRecordEvent.Start -> note("Recording the original on this phone.")
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    val uri = legacy?.let { FileProvider.getUriForFile(context, context.packageName + ".files", it) }
                        ?: event.outputResults.outputUri
                    val bytes = if (!event.hasError() && uri != Uri.EMPTY) runCatching {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                    }.getOrDefault(0L) else 0L
                    val duration = event.recordingStats.recordedDurationNanos / 1_000_000
                    if (bytes > 0 && duration > 0) finished(Original(run, name, uri, duration, bytes))
                    else { note("Recording did not finalize. Start a new take."); finished(null) }
                    if (refreshAfterRecording) { refreshAfterRecording = false; bind() }
                }
            }
        }
    }
    fun close() { bindingGeneration++; refreshAfterRecording = false; recording?.stop(); pending?.cancel(); pending = null }
}
