package com.galaxycrew.crewcam

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RehearsalActivity : ComponentActivity() {
    private enum class Step {
        EMPTY,
        PREPARING,
        START,
        CHECKING,
        PLAN,
        READY,
        RESULT,
    }

    private var step = Step.EMPTY
    private var source: RehearsalSource? = null
    private var crew: RehearsalCrew? = null
    private var reference: AppearanceReference? = null
    private var job: Job? = null
    private val ink = Color.rgb(16, 36, 33)
    private val mint = Color.rgb(161, 228, 195)
    private val root by lazy {
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ink)
        }
    }
    private val scroll by lazy { ScrollView(this) }
    private val contents by lazy {
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(9), dp(14), dp(16))
        }
    }
    private val origin by lazy { label("", 14) }
    private val stageLabel by lazy { label("", 17, true) }
    private val status by lazy { label("", 17, true).apply { setTextColor(mint) } }
    private val subject by lazy {
        EditText(this).apply {
            setSingleLine()
            setText("light blue ball on the table in front of the baby")
            textSize = 16f
            selectAllOnFocusCompat()
        }
    }

    private fun EditText.selectAllOnFocusCompat() {
        setSelectAllOnFocus(true)
        filters = arrayOf(android.text.InputFilter.LengthFilter(80))
    }

    private val pics = mutableMapOf<String, ImageView>()
    private val captions = mutableMapOf<String, TextView>()
    private val board by lazy { CameraBoard(this) }
    private val pick by lazy { button("Select subject in still") { selectSubject() } }
    private val check by lazy { button("Check starting views") { checkViews(false) } }
    private val keep by lazy { button("Keep current views") { choose(false) } }
    private val adjust by lazy { button("Apply suggested framing") { choose(true) } }
    private val fresh by lazy { button("Check new frames") { checkViews(true) } }
    private val compare by lazy {
        button("Play both virtual views") {
            val clip = source
            val take = crew
            if (clip != null && take != null)
                playVirtualPair(this, clip, take.crops.toMap(), take::playback)
        }
    }
    private val restart by lazy { button("Reset for another take") { reset() } }
    private val details by lazy {
        button("Run details") {
            crew?.let {
                AlertDialog.Builder(this)
                    .setTitle("Rehearsal evidence")
                    .setMessage("Run " + it.run + "\n" + it.summary())
                    .setPositiveButton("Close", null)
                    .show()
            }
        }
    }
    private val play by lazy { button("Play source clip") { playSource() } }
    private val importClip by lazy {
        button("Open local video") { chooseFile.launch(arrayOf("video/*")) }
    }
    private val chooseFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) load(uri)
        }
    private var picker: PhotoPicker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root.isFocusableInTouchMode = true
        val top =
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(4), dp(10), 0)
            }
        top.addView(label("Rehearsal", 23, true), LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(button("Close") { finish() })
        root.addView(top)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        scroll.addView(contents)
        contents.addView(origin)
        contents.addView(stageLabel)
        val strip = LinearLayout(this)
        for (id in listOf("A", "B")) {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val im =
                ImageView(this).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(Color.BLACK)
                    contentDescription = "Virtual " + id + " model input"
                }
            val cap = label("Virtual " + id, 13, true)
            pics[id] = im
            captions[id] = cap
            col.addView(im, LinearLayout.LayoutParams(-1, dp(103)))
            col.addView(cap)
            strip.addView(
                col,
                LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), 0, dp(2), 0) },
            )
        }
        contents.addView(strip)
        contents.addView(status)
        contents.addView(subject)
        contents.addView(pick)
        contents.addView(check)
        contents.addView(row(keep, adjust))
        contents.addView(fresh)
        contents.addView(compare)
        contents.addView(board)
        contents.addView(row(details, restart))
        contents.addView(row(play, importClip))
        if (android.os.Build.VERSION.SDK_INT >= 30)
            root.setOnApplyWindowInsetsListener { v, ins ->
                val b =
                    ins.getInsets(
                        android.view.WindowInsets.Type.systemBars() or
                            android.view.WindowInsets.Type.displayCutout()
                    )
                v.setPadding(
                    b.left,
                    b.top,
                    b.right,
                    maxOf(b.bottom, ins.getInsets(android.view.WindowInsets.Type.ime()).bottom),
                )
                ins
            }
        setContentView(root)
        load(null)
    }

    private fun load(uri: Uri?) {
        if (job?.isActive == true) return
        step = Step.PREPARING
        status.text = "Opening the local clip…"
        render()
        job = lifecycleScope.launch {
            try {
                val next =
                    withContext(Dispatchers.IO) {
                        RehearsalSource.open(this@RehearsalActivity, uri)
                    }
                source?.let {
                    it.close()
                    it.file.delete()
                }
                source = next
                if (next == null) {
                    step = Step.EMPTY
                    status.text = "Open a local video at least 1.5 seconds long."
                } else reset()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                step = Step.EMPTY
                status.text = "This clip could not be opened. Choose another local video."
            } finally {
                render()
            }
        }
    }

    private fun reset() {
        if (source == null) return
        crew = null
        reference = null
        step = Step.PREPARING
        status.text = "Preparing the starting views…"
        subject.isEnabled = true
        preparePreview(false, "Both virtual cameras start with the same full frame.")
    }

    private fun preparePreview(freshFrame: Boolean, explanation: String) {
        job = lifecycleScope.launch {
            try {
                previews(freshFrame)
                step = if (freshFrame) Step.READY else Step.START
                status.text = explanation
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                step = Step.EMPTY
                status.text = "This frame could not be decoded. Open another local video."
                crew?.fail("The preview frame could not be decoded.")
            } finally {
                render()
                scroll.smoothScrollTo(0, 0)
            }
        }
        render()
    }

    private suspend fun previews(after: Boolean) {
        val src = source ?: return
        val movie =
            withContext(Dispatchers.IO) {
                src.frame(if (after) RehearsalSource.FRESH_US else RehearsalSource.INITIAL_US)
            }
        val duration = movie.ptsUs / 1_000_000.0
        stageLabel.text =
            "Paused · " +
                String.format(java.util.Locale.US, "%.2f s", duration) +
                " · source frame " +
                movie.index
        for (id in listOf("A", "B")) {
            val crop = crew?.crops?.get(id) ?: VirtualCrop()
            val im = movie.viewport(crop)
            val scaled =
                Bitmap.createScaledBitmap(im, 320, maxOf(1, 320 * im.height / im.width), true)
            val display =
                if (scaled === movie.image) scaled.copy(Bitmap.Config.ARGB_8888, false) else scaled
            pics[id]?.setImageBitmap(display)
            if (im !== movie.image && display !== im) im.recycle()
            captions[id]?.text =
                "Virtual " +
                    id +
                    " · " +
                    String.format(java.util.Locale.US, "%.2f×", 1f / crop.fraction)
        }
        movie.image.recycle()
    }

    private fun selectSubject() {
        val src = source ?: return
        val name =
            subject.text.toString().trim().ifBlank {
                return
            }
        lifecycleScope.launch {
            val movie =
                try {
                    withContext(Dispatchers.IO) { src.frame(0) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    status.text = "The opening still could not be read."
                    return@launch
                }
            val bitmap = movie.viewport(VirtualCrop())
            if (bitmap !== movie.image) movie.image.recycle()
            val p =
                PhotoPicker(
                    this@RehearsalActivity,
                    Frame(bitmap, movie.id, "video rehearsal"),
                    name,
                ) { ref ->
                    reference = ref
                    status.text =
                        "Selected " +
                            name +
                            " · " +
                            ref.method +
                            ". Starting views use the later source frame."
                }
            picker = p
            p.dialog.setOnDismissListener { if (picker === p) picker = null }
            p.show()
        }
    }

    private fun checkViews(isFresh: Boolean) {
        val src = source ?: return
        val name = subject.text.toString().trim()
        if (name.isBlank()) {
            subject.error = "Name a visible subject."
            return
        }
        if (isFresh && step != Step.READY || !isFresh && step != Step.START) return
        val active =
            if (isFresh) requireNotNull(crew)
            else {
                val dir = File(getExternalFilesDir(null) ?: filesDir, "rehearsals")
                RehearsalCrew(dir, src).also {
                    crew = it
                    it.begin(name, reference?.takeIf { r -> r.subject == name })
                }
            }
        step = Step.CHECKING
        render()
        job = lifecycleScope.launch {
            try {
                active.observeBoth(isFresh) { text ->
                    status.text = text
                    render()
                }
                step = if (active.outcome == "assigned" && !isFresh) Step.PLAN else Step.RESULT
                status.text =
                    when (active.outcome) {
                        "verified" -> "Verified · fresh views match both assigned framings."
                        "assigned" -> "Choose whether to keep or adjust the virtual views."
                        else -> "Check again · " + active.message
                    }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                active.fail("The model timed out.")
                step = Step.RESULT
                status.text = active.message
            } catch (e: CancellationException) {
                active.fail("Interrupted before completion.")
                throw e
            } catch (e: Exception) {
                active.fail(checkError(e))
                step = Step.RESULT
                status.text = active.message
            } finally {
                render()
                scroll.smoothScrollTo(0, 0)
            }
        }
    }

    private fun choose(adjust: Boolean) {
        if (step != Step.PLAN) return
        crew?.choose(adjust)
        step = Step.PREPARING
        status.text = "Preparing the later source frame…"
        preparePreview(
            true,
            if (adjust) "Suggested crop applied. Check these later pixels."
            else "Original crops kept. Check the same later source time.",
        )
    }

    private fun playSource() {
        val clip = source ?: return
        val player =
            VideoView(this).apply {
                setVideoPath(clip.file.path)
                setOnPreparedListener { start() }
            }
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(if (clip.generated) "Generated source clip" else "Imported local clip")
                .setView(player)
                .setPositiveButton("Close", null)
                .create()
        dialog.setOnDismissListener { player.stopPlayback() }
        dialog.show()
    }

    private fun render() {
        origin.text =
            "1 phone · 2 virtual views · " +
                (if (source?.generated == true) "generated Flow video" else "local video") +
                " · on-device AI"
        fun visible(v: View, yes: Boolean) {
            v.visibility = if (yes) View.VISIBLE else View.GONE
        }
        visible(subject, step == Step.START)
        visible(pick, step == Step.START)
        visible(check, step == Step.START)
        visible(keep, step == Step.PLAN)
        visible(adjust, step == Step.PLAN)
        visible(fresh, step == Step.READY)
        visible(compare, step == Step.RESULT)
        visible(details, step in listOf(Step.PLAN, Step.READY, Step.RESULT))
        visible(restart, step in listOf(Step.PLAN, Step.READY, Step.RESULT))
        visible(play, source != null && step !in listOf(Step.CHECKING, Step.PREPARING, Step.EMPTY))
        visible(importClip, step == Step.EMPTY)
        visible(board, crew != null)
        crew?.let { board.show(it.lead, virtual = true) }
    }

    private fun label(s: String, size: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size.toFloat()
            setTextColor(Color.WHITE)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun button(s: String, action: () -> Unit) =
        Button(this).apply {
            text = s
            isAllCaps = false
            minHeight = dp(44)
            minWidth = 0
            textSize = 14f
            setOnClickListener { action() }
        }

    private fun row(vararg views: View) =
        LinearLayout(this).apply {
            views.forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        }

    private fun dp(v: Int) = (resources.displayMetrics.density * v + .5f).toInt()

    override fun onDestroy() {
        job?.cancel()
        picker?.dialog?.dismiss()
        source?.let {
            it.close()
            it.file.delete()
        }
        super.onDestroy()
    }
}
