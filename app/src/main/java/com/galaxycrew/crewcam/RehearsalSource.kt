package com.galaxycrew.crewcam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class VirtualCrop(val x: Float = 0f, val y: Float = 0f, val fraction: Float = 1f) {
    init {
        require(
            fraction in .12f..1f &&
                x >= 0f &&
                y >= 0f &&
                x + fraction <= 1.001f &&
                y + fraction <= 1.001f
        )
    }

    fun pixelRect(w: Int, h: Int): Rect {
        val l = (x * w).roundToInt().coerceIn(0, w - 1)
        val t = (y * h).roundToInt().coerceIn(0, h - 1)
        return Rect(
            l,
            t,
            ((x + fraction) * w).roundToInt().coerceIn(l + 1, w),
            ((y + fraction) * h).roundToInt().coerceIn(t + 1, h),
        )
    }

    fun project(box: UnitBox): UnitBox? =
        UnitBox.drag(
            (box.left - x) / fraction,
            (box.top - y) / fraction,
            (box.right - x) / fraction,
            (box.bottom - y) / fraction,
        )

    fun scaledAround(cx: Float, cy: Float, zoom: Float): VirtualCrop {
        val size = (fraction / zoom).coerceIn(.12f, 1f)
        return VirtualCrop(
            (x + cx * fraction - size / 2).coerceIn(0f, 1 - size),
            (y + cy * fraction - size / 2).coerceIn(0f, 1 - size),
            size,
        )
    }
}

internal data class MovieFrame(val image: Bitmap, val ptsUs: Long, val index: Int) {
    val id
        get() = ptsUs * 1000 + 1

    fun viewport(crop: VirtualCrop): Bitmap {
        val box = crop.pixelRect(image.width, image.height)
        val part = Bitmap.createBitmap(image, box.left, box.top, box.width(), box.height())
        val scale = minOf(1f, 640f / maxOf(part.width, part.height))
        val out =
            Bitmap.createScaledBitmap(
                part,
                maxOf(1, (part.width * scale).roundToInt()),
                maxOf(1, (part.height * scale).roundToInt()),
                true,
            )
        if (part !== image && out !== part) part.recycle()
        return out
    }
}

internal class RehearsalSource
private constructor(
    val file: File,
    val digest: String,
    private val times: List<Long>,
    private val media: MediaMetadataRetriever,
) {
    val generated
        get() = digest == FLOW_SHA

    @Synchronized
    fun frame(requestedUs: Long): MovieFrame {
        val position = times.indices.minBy { abs(times[it] - requestedUs) }
        val pts = times[position]
        val image = requireNotNull(media.getFrameAtTime(pts, MediaMetadataRetriever.OPTION_CLOSEST))
        return MovieFrame(image, pts, position)
    }

    @Synchronized fun close() = media.release()

    companion object {
        const val FLOW_SHA = "60d091cc8a30f0664fa750264d7e402c80ce98a8c04e51fb5c6b058a90cc93a9"
        const val INITIAL_US = 500_000L
        const val FRESH_US = 1_500_000L

        fun open(context: Context, uri: Uri? = null): RehearsalSource? {
            val input =
                if (uri == null)
                    try {
                        context.assets.open("rehearsal/first-birthday.mp4")
                    } catch (_: java.io.FileNotFoundException) {
                        return null
                    }
                else requireNotNull(context.contentResolver.openInputStream(uri))
            val dest = File.createTempFile("crew-rehearsal-", ".mp4", context.cacheDir)
            val hash = MessageDigest.getInstance("SHA-256")
            var success = false
            val retriever = MediaMetadataRetriever()
            try {
                input.use { from ->
                    dest.outputStream().use { to ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            val count = from.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 128L * 1024 * 1024) { "Choose a shorter clip." }
                            hash.update(buffer, 0, count)
                            to.write(buffer, 0, count)
                        }
                    }
                }
                val extractor = MediaExtractor()
                val times = mutableListOf<Long>()
                try {
                    extractor.setDataSource(dest.path)
                    val index =
                        (0 until extractor.trackCount).first {
                            extractor
                                .getTrackFormat(it)
                                .getString(MediaFormat.KEY_MIME)
                                ?.startsWith("video/") == true
                        }
                    extractor.selectTrack(index)
                    while (extractor.sampleTime >= 0 && times.size < 1000) {
                        val stamp = extractor.sampleTime
                        times.add(stamp)
                        if (stamp > FRESH_US + 1_000_000 || !extractor.advance()) break
                    }
                } finally {
                    extractor.release()
                }
                require(times.isNotEmpty() && times.max() >= FRESH_US) {
                    "The clip must span at least 1.5 seconds."
                }
                retriever.setDataSource(dest.path)
                val digest = hex(hash.digest())
                if (uri == null) require(digest == FLOW_SHA)
                success = true
                return RehearsalSource(dest, digest, times.distinct().sorted(), retriever)
            } finally {
                if (!success) {
                    retriever.release()
                    dest.delete()
                }
            }
        }

        fun pixelHash(bitmap: Bitmap): String {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val bytes = ByteBuffer.allocate(pixels.size * 4).order(ByteOrder.BIG_ENDIAN)
            bytes.asIntBuffer().put(pixels)
            return hex(MessageDigest.getInstance("SHA-256").digest(bytes.array()))
        }

        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}
