package com.galaxycrew.crewcam

import android.content.Context
import android.os.Build
import android.os.SystemClock
import org.json.JSONObject
import java.io.File

internal class RunJournal(context: Context) {
    private val output = File(context.filesDir, "runs.jsonl")
    @Synchronized fun append(event: JSONObject) {
        event.put("device", Build.MODEL).put("android", Build.VERSION.RELEASE)
            .put("build", BuildConfig.VERSION_NAME).put("uptime_ms", SystemClock.elapsedRealtime())
            .put("utc_ms", System.currentTimeMillis())
        output.appendText(event.toString() + "\n")
    }
}
