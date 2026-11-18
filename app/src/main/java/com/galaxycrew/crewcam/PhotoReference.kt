package com.galaxycrew.crewcam

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class UnitBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(
            left in 0f..1f &&
                top in 0f..1f &&
                right in 0f..1f &&
                bottom in 0f..1f &&
                right > left &&
                bottom > top
        )
    }

    val width
        get() = right - left

    val height
        get() = bottom - top

    val x
        get() = (left + right) / 2

    val y
        get() = (top + bottom) / 2

    fun contains(px: Float, py: Float) = px in left..right && py in top..bottom

    companion object {
        fun drag(ax: Float, ay: Float, bx: Float, by: Float): UnitBox? {
            val l = min(ax, bx).coerceIn(0f, 1f)
            val r = max(ax, bx).coerceIn(0f, 1f)
            val t = min(ay, by).coerceIn(0f, 1f)
            val b = max(ay, by).coerceIn(0f, 1f)
            return if (r - l >= .035f && b - t >= .035f) UnitBox(l, t, r, b) else null
        }

        fun around(x: Float, y: Float): UnitBox {
            val l = (x - .09f).coerceIn(0f, .82f)
            val t = (y - .09f).coerceIn(0f, .82f)
            return UnitBox(l, t, l + .18f, t + .18f)
        }
    }
}

/** FIT_CENTER applies equally to the frozen photograph, touch input and overlay. */
internal class PhotoFit(viewW: Int, viewH: Int, photoW: Int, photoH: Int) {
    private val scale = min(viewW.toFloat() / photoW, viewH.toFloat() / photoH)
    val w = photoW * scale
    val h = photoH * scale
    val l = (viewW - w) / 2
    val t = (viewH - h) / 2

    fun point(x: Float, y: Float, clamp: Boolean = false): Pair<Float, Float>? {
        if (w <= 0 || h <= 0 || !clamp && (x !in l..l + w || y !in t..t + h)) return null
        return ((x - l) / w).coerceIn(0f, 1f) to ((y - t) / h).coerceIn(0f, 1f)
    }
}

internal data class MatchPoint(val region: UnitBox, val difference: Int)

/** Low-resolution RGB template from a patch of this camera's frozen photo. */
internal class AppearanceReference(
    val sensor: Long,
    val method: String,
    val region: UnitBox,
    val subject: String,
    frozen: IntArray,
) {
    init {
        require(frozen.size == SIDE * SIDE && sensor > 0)
    }

    private val reference = samples(frozen, region)
    private val variation =
        (0..2).sumOf { channel ->
            val values = reference.map { rgb(it, channel) }
            values.max() - values.min()
        }

    fun locate(current: IntArray): MatchPoint? {
        require(current.size == SIDE * SIDE)
        if (variation < 54) return null
        val scores = mutableListOf<MatchPoint>()
        // Try the exact previous position as well as translated/scaled positions.
        scores.add(MatchPoint(region, score(current, region)))
        for (scale in listOf(.55f, .75f, 1f, 1.3f, 1.65f, 2.05f)) {
            val w = region.width * scale
            val h = region.height * scale
            if (w >= .99f || h >= .99f) continue
            val step = max(1, (min(w, h) * SIDE * .16f).roundToInt()).coerceAtMost(4)
            val cols = ((1 - w) * SIDE).toInt()
            val rows = ((1 - h) * SIDE).toInt()
            for (iy in 0..rows step step) for (ix in 0..cols step step) {
                val l = ix.toFloat() / SIDE
                val t = iy.toFloat() / SIDE
                val box = UnitBox(l, t, (l + w).coerceAtMost(1f), (t + h).coerceAtMost(1f))
                val error = score(current, box)
                if (error <= 40) scores.add(MatchPoint(box, error))
            }
        }
        val best = scores.minBy { it.difference }
        if (best.difference > 34) return null
        val ambiguous = scores.any {
            val elsewhere =
                abs(it.region.x - best.region.x) > max(best.region.width * .8f, .13f) ||
                    abs(it.region.y - best.region.y) > max(best.region.height * .8f, .13f)
            elsewhere &&
                it.difference <= best.difference + 2 &&
                !best.region.contains(it.region.x, it.region.y)
        }
        return if (ambiguous) null else best
    }

    private fun score(pixels: IntArray, box: UnitBox): Int {
        var error = 0
        for (i in reference.indices) {
            val v = pixel(pixels, box, i)
            val ref = reference[i]
            for (c in 0..2) error += abs(rgb(v, c) - rgb(ref, c))
        }
        return error / (reference.size * 3)
    }

    private fun samples(pixels: IntArray, box: UnitBox) =
        IntArray(GRID * GRID) { pixel(pixels, box, it) }

    private fun pixel(pixels: IntArray, box: UnitBox, i: Int): Int {
        val x =
            ((box.left + box.width * ((i % GRID + .5f) / GRID)) * SIDE)
                .toInt()
                .coerceIn(0, SIDE - 1)
        val y =
            ((box.top + box.height * ((i / GRID + .5f) / GRID)) * SIDE)
                .toInt()
                .coerceIn(0, SIDE - 1)
        return pixels[y * SIDE + x]
    }

    private fun rgb(value: Int, index: Int) = (value ushr (8 * index)) and 255

    companion object {
        const val SIDE = 64
        private const val GRID = 7
    }
}
