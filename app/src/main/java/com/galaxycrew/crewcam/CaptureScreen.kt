package com.galaxycrew.crewcam

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.camera.view.PreviewView

/** The capture stays visible while the controls scroll, including landscape and large text. */
internal class CaptureScreen(val context: Context) {
    private val ink = Color.rgb(18, 30, 34)
    private val panel = Color.rgb(30, 45, 48)
    private val accent = Color.rgb(151, 226, 190)
    val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ink) }
    private val body = LinearLayout(context)
    val preview = PreviewView(context)
    val team = button("Crew setup")
    val rehearsal = button("Try the rehearsal")
    val enableCamera = button("Enable camera", true)
    val route = button("On this phone")
    val sample = button("Sample checks")
    val zoomOut = button("− Zoom"); val zoomIn = button("+ Zoom")
    val check = button("Check my frame", true)
    val pick = button("Select in photo")
    val clear = button("Clear selection")
    val share = button("Crew photos")
    val details = button("Check details")
    val stepBack = CheckBox(context).apply { text="I have space to step back"; textSize=15f }
    val board = CameraBoard(context)
    val reference = label("", 14, accent)
    val decline = button("Decline")
    val record = button("Record original")
    val play = button("Play last original")
    val subject = EditText(context).apply {
        setSingleLine(); hint = "e.g. birthday cake"; setText("birthday cake")
        textSize = 17f; setTextColor(Color.WHITE); setSelectAllOnFocus(true); maxLines = 1
        filters = arrayOf(android.text.InputFilter.LengthFilter(80))
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
    }
    val title = label("CrewCam", 25, Color.WHITE, true)
    val frameLabel = label("ONE CAMERA", 12, Color.WHITE, true)
    val message = label("Describe the subject, then check what your camera sees.", 16, Color.WHITE)
    val receipt = label("", 14, accent)
    val totals = label("", 13, Color.LTGRAY)
    private val stage = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
    private val controls = ScrollView(context).apply { isFillViewport = false }

    init {
        root.isFocusableInTouchMode = true
        subject.setOnEditorActionListener { field, action, event ->
            val enter = event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_UP
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || enter) {
                context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(field.windowToken, 0)
                root.requestFocus()
                true
            } else false
        }
        val bar = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(3), dp(12), dp(3)) }
        bar.addView(title, LinearLayout.LayoutParams(0, -2, 1f)); bar.addView(rehearsal); bar.addView(team)
        root.addView(bar); root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(stage); body.addView(controls)
        stage.addView(preview, FrameLayout.LayoutParams(-1, -1))
        stage.addView(enableCamera, FrameLayout.LayoutParams(-2,-2,Gravity.CENTER))
        frameLabel.setPadding(dp(9), dp(4), dp(9), dp(4)); frameLabel.background = shape(0xb51a292a.toInt())
        stage.addView(frameLabel, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(10), dp(10), 0, 0) })
        val zoom = row(zoomOut, zoomIn)
        stage.addView(zoom, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(8), dp(5)) })
        val options = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(9), dp(15), dp(16)) }
        controls.addView(options)
        options.addView(label("Subject in view", 13, accent, true)); options.addView(subject)
        options.addView(row(pick,clear)); options.addView(reference)
        options.addView(message); options.addView(receipt); options.addView(totals)
        options.addView(check); options.addView(decline); options.addView(row(record, play)); options.addView(row(route, sample))
        options.addView(row(share,details)); options.addView(stepBack); options.addView(board)
        play.visibility = View.GONE; decline.visibility = View.GONE
        arrange()
        if (android.os.Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            val keyboard = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
            view.setPadding(bars.left, bars.top, bars.right, maxOf(keyboard, bars.bottom)); insets
        }
    }
    fun arrange() {
        val land = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        body.orientation = if (land) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        stage.layoutParams = if (land) LinearLayout.LayoutParams(0, -1, 1.05f) else LinearLayout.LayoutParams(-1, 0, .95f)
        controls.layoutParams = if (land) LinearLayout.LayoutParams(0, -1, 1f) else LinearLayout.LayoutParams(-1, 0, 1.10f)
    }
    private fun row(vararg views: View) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { addView(it, LinearLayout.LayoutParams(-2, -2, 1f).apply { setMargins(dp(2), dp(1), dp(2), dp(1)) }) }
    }
    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); setPadding(0, dp(3), 0, dp(3))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun button(value: String, primary: Boolean = false) = Button(context).apply {
        text = value; isAllCaps = false; textSize = 14f; setTextColor(if (primary) ink else Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(if (primary) accent else panel)
        minimumHeight = dp(46); minWidth = 0
    }
    private fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(7).toFloat() }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
}
