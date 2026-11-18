package com.galaxycrew.crewcam

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*

internal class SampleScreen(private val context: Context, onCheck: (Int) -> Unit, close: () -> Unit) {
    private val ink = Color.rgb(19, 39, 35)
    private val paper = Color.rgb(244, 247, 243)
    private val accent = Color.rgb(157, 229, 192)
    private val muted = Color.rgb(73, 94, 84)
    private val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ink) }
    private val picture = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setBackgroundColor(Color.rgb(220, 231, 238))
    }
    private val caption = label("Generated test image", 13, muted)
    private val result = label("Choose where to check this image.", 17, ink, true)
    private val buttons = mutableListOf<Button>()

    init {
        val bar = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(3), dp(12), dp(3)) }
        bar.addView(label("CrewCam  /  Sample checks", 19, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(button("Close", false) { close() })
        root.addView(bar)
        val body = LinearLayout(context).apply { orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(paper); setPadding(dp(16), dp(10), dp(16), dp(12))
        }
        body.addView(card, if (landscape) LinearLayout.LayoutParams(0, -1, 1.12f) else LinearLayout.LayoutParams(-1, 0, 1.1f))
        card.addView(picture, LinearLayout.LayoutParams(-1, 0, 1f))
        card.addView(caption); card.addView(result)
        val scroller = ScrollView(context)
        body.addView(scroller, if (landscape) LinearLayout.LayoutParams(0, -1, 1f) else LinearLayout.LayoutParams(-1, 0, 1f))
        val menu = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(6), dp(12), dp(14)) }
        scroller.addView(menu)
        fun control(parent: LinearLayout, index: Int, title: String, primary: Boolean = false) {
            val btn = button(title, primary) { onCheck(index) }
            buttons.add(btn); parent.addView(btn)
        }
        control(menu, 6, "Local-first sample", true)
        control(menu, 2, "On-device sample")
        control(menu, 3, "Cloud sample")
        control(menu, 4, "Stable-model fallback sample")
        val extra = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val more = button("Model setup and other checks", false) { extra.visibility = if (extra.visibility == View.GONE) View.VISIBLE else View.GONE }
        menu.addView(more); menu.addView(extra)
        listOf(0 to "Check model", 1 to "Prepare local model", 7 to "Check preview model availability", 5 to "Check App Check",
            8 to "Cloud sample · medium", 9 to "Cloud sample · close", 10 to "Cloud sample · missing subject").forEach {
            control(extra, it.first, it.second)
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
    }
    fun showImage(bitmap: Bitmap, description: String) {
        picture.setImageBitmap(bitmap); picture.contentDescription = description
        caption.text = description
    }
    fun status(value: String) { result.text = value }
    fun enabled(allow: Boolean) { buttons.forEach { it.isEnabled = allow; it.alpha = if (allow) 1f else .55f } }
    private fun button(title: String, primary: Boolean, action: () -> Unit) = Button(context).apply {
        text = title; isAllCaps = false; textSize = 14f; minHeight = dp(44); setTextColor(if (primary) ink else Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(if (primary) accent else Color.rgb(43, 66, 59))
        setOnClickListener { action() }
    }
    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); setPadding(0, dp(3), 0, dp(2))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun dp(i: Int) = (i * context.resources.displayMetrics.density + .5f).toInt()
}
