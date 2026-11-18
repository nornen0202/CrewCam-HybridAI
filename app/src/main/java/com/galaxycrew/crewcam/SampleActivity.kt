@file:OptIn(com.google.firebase.ai.type.PublicPreviewAPI::class)

package com.galaxycrew.crewcam

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

class SampleActivity : ComponentActivity() {
    private lateinit var inference: HybridInference
    private lateinit var journal: RunJournal
    private lateinit var screen: SampleScreen
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        journal = RunJournal(this)
        inference = HybridInference(journal::append)
        screen = SampleScreen(this, ::runCheck) { finish() }
        setContentView(screen.root)
        screen.showImage(SampleCheck.frame().bitmap, SampleCheck.View.WIDE.caption)
        if (BuildConfig.DEBUG && intent.hasExtra("sample_check")) {
            val index = intent.getIntExtra("sample_check", -1)
            if (index in 0..10) runCheck(index)
        }
    }

    private fun runCheck(index: Int) {
        if (running) return
        running = true
        screen.enabled(false)
        screen.status("Checking…")
        lifecycleScope.launch {
            var display = "Check interrupted."
            try {
                display = withTimeout(100_000) {
                    when (index) {
                        0 -> inference.readiness()
                        1 -> { inference.download(screen::status); inference.readiness() }
                        5 -> if (FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token.isNotEmpty())
                            "App Check verified for this installation." else "App Check unavailable."
                        7 -> inference.otherCategories()
                        else -> sample(index)
                    }
                }
            } catch (e: TimeoutCancellationException) { display = "The check timed out. Try again." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { display = checkError(e) }
            finally {
                screen.status(display); screen.enabled(true); running = false
                journal.append(JSONObject().put("event", "sample_status").put("index", index).put("display", display.take(220)))
            }
        }
    }

    private suspend fun sample(index: Int): String {
        val view = when (index) { 8 -> SampleCheck.View.MEDIUM; 9 -> SampleCheck.View.CLOSE; 10 -> SampleCheck.View.EMPTY; else -> SampleCheck.View.WIDE }
        val frame = SampleCheck.frame(view)
        val route = when (index) { 2 -> Route.PRIVATE; 4, 6 -> Route.HYBRID; else -> Route.CLOUD }
        screen.showImage(frame.bitmap, if (index == 4) "Generated image · stable local model requested" else view.caption)
        val result = inference.observe(UUID.randomUUID().toString(), "sample", "A", SampleCheck.subject, frame, route, stableProbe = index == 4)
        val data = result.receipt
        val seconds = String.format(Locale.US, "%.1f s", data.getLong("sdk_ms") / 1000.0)
        val source = if (data.getString("source") == "ON_DEVICE") "ON DEVICE" else "IN CLOUD"
        val model = if (source == "ON DEVICE") data.optString("local_model") else data.optString("response_model")
        val retry = if (data.has("retry_of")) " · format retry" else ""
        return result.grounding.framing + "  ·  " + source + "  ·  " + seconds + "\n" +
            result.grounding.evidence + "\n" + model + retry
    }
}
