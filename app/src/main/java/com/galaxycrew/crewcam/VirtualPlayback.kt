package com.galaxycrew.crewcam

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Both windows are drawn from the same decoded video image and share a single playback clock. */
internal fun playVirtualPair(
    activity: Activity,
    source: RehearsalSource,
    crops: Map<String, VirtualCrop>,
    finished: (Int, Int, Int) -> Unit,
) {
    val group = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    val labels = LinearLayout(activity)
    val density = activity.resources.displayMetrics.density
    for (id in listOf("A", "B")) {
        val text =
            TextView(activity).apply {
                this.text =
                    "Virtual " +
                        id +
                        " · " +
                        String.format(Locale.US, "%.2f×", 1 / crops.getValue(id).fraction)
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = android.view.Gravity.CENTER
            }
        labels.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
    }
    group.addView(labels)
    val frame = FrameLayout(activity)
    val sourceSurface = TextureView(activity)
    var raster: Bitmap? = null
    val brush = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val pair =
        object : View(activity) {
            init {
                contentDescription = "Two moving virtual views from the same decoded frame"
                setBackgroundColor(Color.BLACK)
            }

            override fun onDraw(canvas: Canvas) {
                val image = raster ?: return
                val half = width / 2f
                listOf("A", "B").forEachIndexed { i, id ->
                    val src = crops.getValue(id).pixelRect(image.width, image.height)
                    val scale = minOf((half - 4) / src.width(), height.toFloat() / src.height())
                    if (scale <= 0) return@forEachIndexed
                    val w = src.width() * scale
                    val h = src.height() * scale
                    val x = i * half + (half - w) / 2
                    val y = (height - h) / 2
                    canvas.drawBitmap(image, src, RectF(x, y, x + w, y + h), brush)
                }
            }
        }
    frame.addView(sourceSurface, FrameLayout.LayoutParams(-1, -1))
    frame.addView(pair, FrameLayout.LayoutParams(-1, -1))
    group.addView(frame, LinearLayout.LayoutParams(-1, (160 * density).toInt()))
    val clock =
        TextView(activity).apply {
            text = "Starting shared source clock…"
            gravity = android.view.Gravity.CENTER
            textSize = 15f
        }
    group.addView(clock)
    val dialog =
        AlertDialog.Builder(activity)
            .setTitle("Virtual A/B playback")
            .setView(group)
            .setPositiveButton("Close", null)
            .create()
    var player: MediaPlayer? = null
    var output: Surface? = null
    var first = -1
    var last = -1
    var updates = 0
    fun release() {
        player?.release()
        player = null
        output?.release()
        output = null
    }
    sourceSurface.surfaceTextureListener =
        object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                texture: SurfaceTexture,
                width: Int,
                height: Int,
            ) {
                output = Surface(texture)
                player =
                    MediaPlayer().apply {
                        setDataSource(source.file.path)
                        setSurface(output)
                        setVolume(0f, 0f)
                        setOnPreparedListener { p ->
                            val w = maxOf(2, p.videoWidth)
                            val h = maxOf(2, p.videoHeight)
                            val scale = minOf(1f, 640f / maxOf(w, h))
                            raster =
                                Bitmap.createBitmap(
                                    maxOf(1, (w * scale).toInt()),
                                    maxOf(1, (h * scale).toInt()),
                                    Bitmap.Config.ARGB_8888,
                                )
                            p.start()
                        }
                        setOnCompletionListener {
                            clock.text =
                                "End of source · " +
                                    String.format(Locale.US, "%.2f s", it.duration / 1000.0)
                        }
                        setOnErrorListener { _, _, _ ->
                            clock.text = "The clip could not play."
                            true
                        }
                        prepareAsync()
                    }
            }

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                val image = raster ?: return
                val p = player ?: return
                val position = p.currentPosition
                if (position == last || !sourceSurface.isAvailable) return
                sourceSurface.getBitmap(image)
                if (first < 0) first = position
                last = position
                updates++
                pair.invalidate()
                clock.text =
                    String.format(
                        Locale.US,
                        "Same source · %.2f / %.2f s",
                        position / 1000.0,
                        p.duration / 1000.0,
                    )
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, w: Int, h: Int) {}

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                release()
                return true
            }
        }
    dialog.setOnDismissListener {
        release()
        finished(first, last, updates)
    }
    dialog.show()
}
