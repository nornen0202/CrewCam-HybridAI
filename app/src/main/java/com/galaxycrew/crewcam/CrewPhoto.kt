package com.galaxycrew.crewcam

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

internal data class CheckedShot(val view: CameraView, val checkedAt: Long, val photo: Bitmap?)

/**
 * Each optional image is part of the observation bearing that exact run, round and sensor frame.
 */
internal object CrewPhoto {
    const val LONG_EDGE = 192
    const val MAX_BYTES = 12_000

    fun thumbnail(source: Bitmap): Bitmap {
        val scale = minOf(1f, LONG_EDGE.toFloat() / maxOf(source.width, source.height))
        val thumb =
            Bitmap.createScaledBitmap(
                source,
                maxOf(1, (source.width * scale).roundToInt()),
                maxOf(1, (source.height * scale).roundToInt()),
                true,
            )
        return if (thumb === source) source.copy(Bitmap.Config.ARGB_8888, false) else thumb
    }

    fun encode(photo: Bitmap): String? {
        for (quality in listOf(60, 38)) {
            val bytes = ByteArrayOutputStream()
            if (
                photo.compress(Bitmap.CompressFormat.JPEG, quality, bytes) &&
                    bytes.size() in 1..MAX_BYTES
            )
                return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        }
        return null
    }

    fun decode(payload: String): Bitmap? {
        if (payload.length !in 1..((MAX_BYTES + 2) / 3 * 4)) return null
        val bytes =
            runCatching { Base64.decode(payload, Base64.NO_WRAP) }.getOrNull() ?: return null
        if (bytes.size !in 1..MAX_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (
            bounds.outMimeType != "image/jpeg" ||
                bounds.outWidth !in 1..LONG_EDGE ||
                bounds.outHeight !in 1..LONG_EDGE
        )
            return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
