package com.galaxycrew.crewcam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock

/** Locally drawn and visibly labelled. This image contains no camera or personal data. */
internal object SampleCheck {
    const val subject = "the orange ball"
    enum class View(val radius: Float, val caption: String) {
        WIDE(72f, "Generated image · orange ball"),
        MEDIUM(145f, "Generated image · larger orange ball"),
        CLOSE(215f, "Generated image · close orange ball"),
        EMPTY(0f, "Generated image · empty table")
    }
    fun frame(view: View = View.WIDE): Frame {
        val image = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.rgb(220, 231, 238))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(106, 137, 147)
        canvas.drawRect(0f, 328f, 640f, 480f, paint)
        val radius = view.radius
        if (radius == 0f) return Frame(image, SystemClock.elapsedRealtimeNanos(), "generated sample")
        paint.color = Color.rgb(213, 103, 42)
        canvas.drawCircle(320f, 240f, radius, paint)
        paint.color = Color.rgb(255, 192, 106)
        canvas.drawCircle(320f - radius * .30f, 240f - radius * .35f, radius * .20f, paint)
        return Frame(image, SystemClock.elapsedRealtimeNanos(), "generated sample")
    }
}
