package com.galaxycrew.crewcam

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import java.io.File
import org.json.JSONObject

/**
 * Synthetic participants in one process. No physical pairing or camera capture is asserted here.
 */
class CrewUiChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        val outcome = runCatching {
            runOnMainSync {
                runCatching { exerciseRooms() }
                    .onSuccess { result.putString("checks", it) }
                    .onFailure { result.putString("error", it.toString()) }
            }
        }
        outcome.exceptionOrNull()?.let { result.putString("error", it.toString()) }
        finish(
            if (result.containsKey("error")) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            result,
        )
    }

    private fun exerciseRooms(): String {
        val handlers = mutableMapOf<String, CrewLinkCallbacks>()
        val messages = ArrayDeque<Pair<String, String>>()
        val sent = mutableListOf<JSONObject>()
        val journal = mutableListOf<JSONObject>()
        fun make(role: String): Room {
            val room =
                Room(
                    { name, _, hooks ->
                        handlers[name] = hooks
                        object : CrewTransport {
                            override fun start(host: Boolean) {}

                            override fun close() {}

                            override fun confirm(endpoint: String, accepted: Boolean) {}

                            override fun send(message: String): Boolean {
                                sent += JSONObject(message)
                                messages.addLast((if (name == "A") "B" else "A") to message)
                                return true
                            }
                        }
                    },
                    journal::add,
                    {},
                    {},
                    {},
                    authenticate = { _, _, _ -> },
                )
            room.configure(2, role)
            return room
        }
        fun deliver() {
            while (messages.isNotEmpty()) {
                val (to, msg) = messages.removeFirst()
                handlers.getValue(to).message(msg)
            }
        }
        fun drawing(color: Int): Bitmap =
            Bitmap.createBitmap(120, 90, Bitmap.Config.ARGB_8888).apply {
                eraseColor(color)
                val paint = android.graphics.Paint().apply { this.color = Color.WHITE }
                Canvas(this).drawCircle(60f, 45f, 13f, paint)
            }
        fun sample(id: Long, angle: String, color: Int) =
            CheckedShot(
                CameraView(id, angle, true, true, "ON_DEVICE", 17),
                1_794_888_000_000L,
                drawing(color),
            )
        val a = make("A")
        val b = make("B")
        handlers.forEach { (id, h) ->
            h.connectedCameras(setOf(if (id == "A") "B" else "A"))
            h.connection(true)
        }
        a.begin("generated ball")
        deliver()
        b.submitFrame(sample(10, "WIDE", Color.BLUE))
        a.submitFrame(sample(22, "WIDE", Color.RED))
        deliver()
        check(sent.filter { it.optString("kind") == "view" }.none { it.has("photo") })
        check(a.shots.getValue("B").photo == null && a.shots.getValue("A").photo != null)
        a.begin("generated ball")
        deliver()
        b.setPhotoSharing(true)
        a.submitFrame(sample(35, "WIDE", Color.RED))
        b.submitFrame(sample(58, "WIDE", Color.BLUE))
        deliver()
        val photo = a.shots.getValue("B").photo ?: error("Consented photo missing")
        check(photo.width == 120 && photo.height == 90 && Color.blue(photo.getPixel(5, 5)) > 220)
        check(a.shots.getValue("B").view.frame == 58L)
        check(sent.last { it.optString("kind") == "view" }.toString().toByteArray().size < 24_576)
        check(journal.none { it.has("photo") })
        a.accept()
        b.accept()
        a.ready()
        b.ready()
        deliver()
        check(a.leader!!.done == setOf("A", "B"))
        check(b.lastChoice!!.adjustment == "TIGHTEN_CURRENT")
        val board =
            CameraBoard(
                ContextThemeWrapper(targetContext, android.R.style.Theme_Material_NoActionBar)
            )
        board.show(a)
        board.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        board.layout(0, 0, board.measuredWidth, board.measuredHeight)
        val bitmap = Bitmap.createBitmap(board.width, board.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(18, 30, 34))
        board.draw(canvas)
        val output = File(targetContext.getExternalFilesDir(null), "simulated-roles.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        b.setPhotoSharing(false)
        deliver()
        check(a.shots.getValue("B").photo == null && a.shots.getValue("B").view.frame == 58L)
        a.fresh()
        deliver()
        check(a.shots.isEmpty() && b.shots.isEmpty())
        b.setPhotoSharing(true)
        a.submitFrame(sample(41, "WIDE", Color.RED))
        b.submitFrame(sample(68, "MEDIUM", Color.CYAN))
        deliver()
        check(a.lastVerdict == true && b.lastVerdict == true)
        val previous = sent.last { it.optString("kind") == "view" }
        handlers.getValue("A").connection(false)
        check(a.shots.isEmpty())
        handlers.getValue("A").message(previous.toString())
        check(a.shots.isEmpty())
        check(CrewPhoto.decode("A".repeat(20_000)) == null && CrewPhoto.decode("YWJj") == null)
        return "PASS: opt-out metadata, opt-in JPEG, sender/frame binding, no journal pixels, revoke, clear at fresh round, accepted roles, verified round, disconnect, stale packet, bounded decoding"
    }
}
