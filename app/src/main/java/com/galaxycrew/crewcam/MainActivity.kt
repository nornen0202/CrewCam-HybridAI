package com.galaxycrew.crewcam

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import crewcam.core.CrewTopology
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class MainActivity : ComponentActivity() {
    private lateinit var ui: CaptureScreen
    private lateinit var camera: PhoneCamera
    private lateinit var room: Room
    private lateinit var inference: HybridInference
    private var work: Job? = null
    private var route = Route.PRIVATE
    private var receipt = ""
    private var lastOriginal: Uri? = null
    private var savedFile = ""
    private var pendingCrew: Pair<Int, String>? = null
    private var pairing: AlertDialog? = null
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val pending = pendingCrew
        pendingCrew = null
        if (pending != null && nearbyPermissions().all(::granted)) room.configure(pending.first, pending.second)
        else if (pending != null) ui.message.text = "Nearby access is needed to connect two cameras."
        else if (granted(Manifest.permission.CAMERA)) camera.bind()
        else ui.message.text = "Allow camera access to check a live frame. Sample checks also work."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG && intent.hasExtra("sample_check")) {
            startActivity(Intent(this, SampleActivity::class.java).putExtra("sample_check", intent.getIntExtra("sample_check", -1)))
            finish(); return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ui = CaptureScreen(this)
        setContentView(ui.root)
        val journal = RunJournal(this)
        inference = HybridInference(journal::append)
        room = Room(this, journal::append, ::render, { work?.cancel(); work = null; receipt = "" }) { peer, digits, reply ->
            pairing?.dismiss()
            pairing = AlertDialog.Builder(this).setTitle("Pair with camera " + peer + "?")
                .setMessage("Confirm that both phones show " + digits + ".")
                .setPositiveButton("Codes match") { _, _ -> reply(true) }
                .setNegativeButton("Reject") { _, _ -> reply(false) }
                .setOnCancelListener { reply(false) }.show()
        }
        camera = PhoneCamera(this, this, ui.preview, { ui.message.text = it; ui.record.text = if (camera.isRecording) "Stop recording" else "Record original" }) { original ->
            if (original != null) { lastOriginal = original.uri; savedFile = original.name; room.saved(original) }
            render()
        }
        ui.team.setOnClickListener { chooseCrew() }
        ui.route.setOnClickListener { chooseRoute() }
        ui.sample.setOnClickListener { startActivity(Intent(this, SampleActivity::class.java)) }
        ui.zoomIn.setOnClickListener { camera.zoom(1.35f) }
        ui.zoomOut.setOnClickListener { camera.zoom(1 / 1.35f) }
        ui.decline.setOnClickListener { room.failure("This camera stopped the framing check. Start a new check when the crew is ready.") }
        ui.check.setOnClickListener { action() }
        ui.record.setOnClickListener {
            runCatching { camera.record(room.run, room.camera) }.onFailure { ui.message.text = "Could not open a recording." }
            render()
        }
        ui.play.setOnClickListener { lastOriginal?.let { uri ->
            runCatching { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                .onFailure { ui.message.text = "Open the original in Movies/CrewCam." }
        } }
        render()
        if (granted(Manifest.permission.CAMERA)) camera.bind()
        else permissions.launch(arrayOf(Manifest.permission.CAMERA))
    }

    private fun action() {
        if (work?.isActive == true) return
        if (!granted(Manifest.permission.CAMERA)) { permissions.launch(arrayOf(Manifest.permission.CAMERA)); return }
        if (room.needsFrame) { observe(); return }
        if (room.offer != null && !room.accepted) { room.accept(); return }
        if (room.accepted && !room.done) { room.ready(); return }
        if (room.leader?.let { it.phase == Phase.ASSIGNED && it.done == it.cameras.toSet() } == true) {
            room.fresh(); observe(); return
        }
        if (room.camera == "A" && !camera.isRecording) {
            val subject = ui.subject.text.toString().trim()
            if (subject.isBlank()) { ui.subject.error = "Enter a visible subject."; return }
            room.begin(subject); observe()
        }
    }

    private fun observe() {
        val run = room.run; val round = room.round; val target = room.subject
        val cameraName = room.camera; val policy = route
        work = lifecycleScope.launch {
            try {
                ui.check.isEnabled = false; ui.route.isEnabled = false
                ui.message.text = "Checking a fresh camera frame…"
                val frame = camera.nextFrame()
                val reading = try {
                    withTimeout(110_000) { inference.observe(run, round, cameraName, target, frame, policy) }
                } finally { frame.bitmap.recycle() }
                if (room.run != run || room.round != round || !room.needsFrame) return@launch
                val data = reading.receipt
                receipt = reading.grounding.evidence + " · " + data.getString("source") + " · " + data.getLong("sdk_ms") + " ms"
                room.submitFrame(CameraView(frame.timestamp, reading.grounding.framing, reading.grounding.tightenSafe,
                    camera.canWiden, data.getString("source"), data.getLong("sdk_ms"), reading.grounding.issue))
            } catch (e: TimeoutCancellationException) {
                if (room.run == run && room.round == round) room.failure("The frame check timed out. Check the camera and model, then retry.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (room.run == run && room.round == round) room.failure(checkError(e))
            } finally { if (work === coroutineContext[Job]) { work = null; render() } }
        }
        render()
    }

    private fun render() {
        if (!::room.isInitialized || !::camera.isInitialized) return
        val busy = work?.isActive == true
        val host = room.camera == "A"
        val choice = room.offer
        val team = room.leader
        val canVerify = team?.let { it.phase == Phase.ASSIGNED && it.done == it.cameras.toSet() } == true
        ui.frameLabel.text = if (room.size == 1) "SOLO CAMERA" else "CAMERA " + room.camera + "  ·  NEARBY"
        ui.team.text = if (room.size == 1) "Crew setup" else "Crew · " + room.size
        ui.subject.isEnabled = host && !busy
        if (!host && room.subject.isNotBlank() && ui.subject.text.toString() != room.subject) ui.subject.setText(room.subject)
        ui.route.text = route.title
        ui.route.isEnabled = !busy; ui.sample.isEnabled = !busy && !camera.isRecording
        ui.team.isEnabled = !busy && !camera.isRecording
        ui.message.text = when {
            busy -> "Checking a fresh camera frame…"
            choice != null && !room.done -> "Camera " + room.camera + " · " + when (choice.adjustment) {
                "TIGHTEN_CURRENT" -> "Move closer or zoom in for a " + choice.framing.lowercase() + " view."
                "WIDEN_CURRENT" -> "Widen the view to include more of the scene."
                else -> "Hold the current " + choice.framing.lowercase() + " framing."
            } + if (choice.backup) " This camera is a backup." else ""
            else -> room.note
        }
        ui.receipt.text = receipt
        ui.totals.text = listOfNotNull(
            team?.takeIf { it.phase == Phase.ASSIGNED }?.let { "Ready " + it.done.size + "/" + room.size },
            room.originals.takeIf { it.isNotEmpty() }?.let { "Original saved · " + it.sorted().joinToString(", ") },
            savedFile.takeIf { it.isNotBlank() && room.size == 1 }?.let { "Last take · " + it }
        ).joinToString("    ")
        ui.check.text = when {
            busy -> "Checking…"
            room.needsFrame -> "I see the subject · Check frame"
            choice != null && !room.accepted -> "Accept framing"
            room.accepted && !room.done -> "My shot is ready"
            canVerify -> "Verify fresh views"
            !host -> "Waiting for lead camera"
            room.size > 1 && !room.connected -> "Waiting for crew"
            team?.phase in setOf(Phase.OBSERVE, Phase.RECHECK, Phase.ASSIGNED) -> "Waiting for crew"
            room.size == 1 -> "Check my frame"
            else -> "Check team framing"
        }
        ui.check.isEnabled = !busy && (room.size == 1 || room.connected) &&
            (room.needsFrame || choice != null && !room.done || canVerify ||
                host && team?.phase !in setOf(Phase.OBSERVE, Phase.RECHECK, Phase.ASSIGNED) && !camera.isRecording)
        val activeRound = team?.phase in setOf(Phase.OBSERVE, Phase.ASSIGNED, Phase.RECHECK)
        ui.decline.visibility = if (!busy && (choice != null && !room.done || activeRound)) View.VISIBLE else View.GONE
        ui.decline.text = if (choice != null && !room.accepted) "Decline" else "Cancel team check"
        ui.play.visibility = if (lastOriginal != null) View.VISIBLE else View.GONE
        ui.record.text = if (camera.isRecording) "Stop recording" else "Record original"
    }

    private fun chooseRoute() {
        AlertDialog.Builder(this).setTitle("Where can this frame be analyzed?")
            .setMessage("On this phone keeps the image local. With cloud fallback, Google may process this camera’s reduced frame and subject text on the free Gemini service. Use public or staged scenes for cloud checks.")
            .setPositiveButton("On this phone") { _, _ -> route = Route.PRIVATE; render() }
            .setNeutralButton("Allow cloud fallback") { _, _ -> route = Route.HYBRID; render() }
            .setNegativeButton("Cancel", null).show()
    }
    private fun chooseCrew() {
        AlertDialog.Builder(this).setTitle("Camera crew").setItems(arrayOf("One phone", "Lead a crew", "Join a crew")) { _, choice ->
            if (choice == 0) { room.configure(1, "A"); return@setItems }
            val sizes = (2..CrewTopology.MAX_CAMERAS).toList()
            AlertDialog.Builder(this).setTitle("How many phones?").setItems(sizes.map { "$it phones" }.toTypedArray()) { _, n ->
                val count = sizes[n]
                if (choice == 1) connect(count, "A") else {
                    val roles = CrewTopology.names(count).drop(1)
                    if (roles.size == 1) connect(count, roles.single())
                    else AlertDialog.Builder(this).setTitle("Choose a unique camera letter").setItems(roles.toTypedArray()) { _, i -> connect(count, roles[i]) }.show()
                }
            }.show()
        }.show()
    }
    private fun connect(count: Int, name: String) {
        val missing = nearbyPermissions().filterNot(::granted)
        if (missing.isEmpty()) room.configure(count, name)
        else { pendingCrew = count to name; permissions.launch(missing.toTypedArray()) }
    }
    private fun nearbyPermissions() = buildList {
        if (Build.VERSION.SDK_INT <= 32) { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
        if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT); add(Manifest.permission.BLUETOOTH_ADVERTISE) }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    private fun granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    override fun onConfigurationChanged(config: Configuration) {
        super.onConfigurationChanged(config)
        if (!::ui.isInitialized) return
        ui.arrange()
        if (granted(Manifest.permission.CAMERA)) ui.preview.post { camera.updateViewport() }
    }
    override fun onDestroy() { work?.cancel(); if (::room.isInitialized) room.close(); if (::camera.isInitialized) camera.close(); super.onDestroy() }
}
