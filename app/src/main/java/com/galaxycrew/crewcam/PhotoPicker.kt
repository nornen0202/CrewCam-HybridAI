package com.galaxycrew.crewcam

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlin.math.abs

internal fun referencePixels(bitmap: Bitmap): IntArray {
    val side = AppearanceReference.SIDE
    val small = Bitmap.createScaledBitmap(bitmap, side, side, true)
    val pixels = IntArray(side * side)
    small.getPixels(pixels, 0, side, 0, 0, side, side)
    if (small !== bitmap) small.recycle()
    return pixels
}

internal class PhotoPicker(
    private val activity: Activity,
    private val frame: Frame,
    private val name: String,
    private val chosen: (AppearanceReference) -> Unit,
) {
    val dialog = Dialog(activity)
    private val caption = TextView(activity)
    private val use = Button(activity)
    private val photo = PhotoSurface()
    private var selected: UnitBox? = null
    private var method = ""

    private fun dp(value: Int) = (activity.resources.displayMetrics.density * value + .5f).toInt()

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.rgb(17, 30, 32))
                setPadding(dp(12), dp(6), dp(12), dp(8))
            }
        root.addView(
            TextView(activity).apply {
                text = "Select " + name
                textSize = 20f
                setTextColor(Color.WHITE)
            }
        )
        caption.apply {
            text = "Tap an outlined object or drag around the subject."
            textSize = 14f
        }
        root.addView(caption)
        root.addView(photo, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = LinearLayout(activity).apply { gravity = Gravity.END }
        row.addView(
            Button(activity).apply {
                text = "Cancel"
                isAllCaps = false
                setOnClickListener { dialog.dismiss() }
            }
        )
        use.text = "Use selection"
        use.isAllCaps = false
        use.isEnabled = false
        use.setOnClickListener {
            val selectedRegion = selected ?: return@setOnClickListener
            chosen(
                AppearanceReference(
                    frame.timestamp,
                    method,
                    selectedRegion,
                    name,
                    referencePixels(frame.bitmap),
                )
            )
            dialog.dismiss()
        }
        row.addView(use)
        root.addView(row)
        dialog.setContentView(root)
        dialog.show()
        dialog.window?.setLayout(-1, -1)
        val options =
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .build()
        runCatching {
            val detector = ObjectDetection.getClient(options)
            detector
                .process(InputImage.fromBitmap(frame.bitmap, 0))
                .addOnSuccessListener { items ->
                    if (!dialog.isShowing) return@addOnSuccessListener
                    val w = frame.bitmap.width.toFloat()
                    val h = frame.bitmap.height.toFloat()
                    photo.candidates = items.mapNotNull { o ->
                        val r = o.boundingBox
                        UnitBox.drag(r.left / w, r.top / h, r.right / w, r.bottom / h)
                    }
                    photo.invalidate()
                    if (selected == null)
                        caption.text =
                            if (photo.candidates.isEmpty())
                                "Tap a detail to mark it, or drag around the subject."
                            else "Tap a numbered object, or drag your own box."
                }
                .addOnCompleteListener { detector.close() }
        }
    }

    private inner class PhotoSurface : View(activity) {
        var candidates = emptyList<UnitBox>()
        private val brush = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var start: Pair<Float, Float>? = null
        private var drag: UnitBox? = null
        private var downX = 0f
        private var downY = 0f

        init {
            contentDescription = "Frozen camera photo. Tap a candidate or drag a box."
            isClickable = true
            setBackgroundColor(Color.BLACK)
        }

        private fun fit() = PhotoFit(width, height, frame.bitmap.width, frame.bitmap.height)

        override fun onDraw(canvas: Canvas) {
            val fit = fit()
            brush.color = Color.WHITE
            brush.style = Paint.Style.FILL
            canvas.drawBitmap(
                frame.bitmap,
                null,
                RectF(fit.l, fit.t, fit.l + fit.w, fit.t + fit.h),
                brush,
            )
            candidates.forEachIndexed { i, r ->
                outline(canvas, fit, r, Color.rgb(130, 230, 190), (i + 1).toString())
            }
            (drag ?: selected)?.let {
                outline(canvas, fit, it, Color.rgb(255, 180, 72), "Selected")
            }
        }

        private fun outline(
            canvas: Canvas,
            fit: PhotoFit,
            box: UnitBox,
            color: Int,
            label: String,
        ) {
            val r =
                RectF(
                    fit.l + box.left * fit.w,
                    fit.t + box.top * fit.h,
                    fit.l + box.right * fit.w,
                    fit.t + box.bottom * fit.h,
                )
            brush.style = Paint.Style.STROKE
            brush.strokeWidth = dp(3).toFloat()
            brush.color = color
            canvas.drawRect(r, brush)
            brush.style = Paint.Style.FILL
            brush.textSize = dp(12).toFloat()
            val y = (r.top + dp(18)).coerceAtMost(fit.t + fit.h)
            canvas.drawRect(r.left, y - dp(18), r.left + brush.measureText(label) + dp(8), y, brush)
            brush.color = Color.BLACK
            canvas.drawText(label, r.left + dp(4), y - dp(4), brush)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val mapping = fit()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    start = mapping.point(e.x, e.y) ?: return false
                    downX = e.x
                    downY = e.y
                    drag = null
                    return true
                }
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP -> {
                    val a = start ?: return false
                    val b = mapping.point(e.x, e.y, true) ?: return false
                    val slop = ViewConfiguration.get(context).scaledTouchSlop
                    val moved = abs(e.x - downX) > slop || abs(e.y - downY) > slop
                    drag = if (moved) UnitBox.drag(a.first, a.second, b.first, b.second) else null
                    if (e.actionMasked == MotionEvent.ACTION_UP) {
                        val candidate =
                            candidates
                                .filter { it.contains(a.first, a.second) }
                                .minByOrNull { it.width * it.height }
                        if (!moved || drag != null) {
                            selected =
                                if (moved) drag else candidate ?: UnitBox.around(a.first, a.second)
                            method =
                                when {
                                    moved -> "drag"
                                    candidate != null -> "candidate"
                                    else -> "tap detail"
                                }
                            caption.text =
                                "Selected by " + method + ". Tap again or drag to change."
                            use.isEnabled = true
                        }
                        start = null
                        drag = null
                        performClick()
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    start = null
                    drag = null
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(e)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
