package com.galaxycrew.crewcam

import crewcam.core.CrewTopology
import org.json.JSONObject
import java.util.UUID

internal class Room(private val makeLink: (String,Int,CrewLinkCallbacks) -> CrewTransport, private val event: (JSONObject) -> Unit,
                    private val changed: () -> Unit, private val cancelCheck: () -> Unit,
                    private val clearSelection: () -> Unit,
                    private val transportLabel: String = "Nearby STAR",
                    private val authenticate: (String, String, (Boolean) -> Unit) -> Unit) {
    var camera = "A"; private set
    var size = 1; private set
    var connected = false; private set
    var run = uuid(); private set
    var round = ""; private set
    var subject = ""; private set
    var note = "One phone is ready to check a frame."; private set
    var needsFrame = false; private set
    var offer: CoverageAllocator.Choice? = null; private set
    var accepted = false; private set
    var done = false; private set
    var leader: TeamRound? = null; private set
    val originals = mutableSetOf<String>()
    val shots = mutableMapOf<String,CheckedShot>()
    var online = emptySet<String>(); private set
    var sharePhotos = false; private set
    var lastChoice: CoverageAllocator.Choice? = null; private set
    var lastVerdict: Boolean? = null; private set
    var selfAccepted = false; private set
    var selfReady = false; private set
    val freshRequest get() = receivedFresh
    private var singleCheck = false
    private val hiddenPhotos = mutableSetOf<String>()
    private val pastRuns = ArrayDeque<String>()
    private var link: CrewTransport? = null
    private var pendingAuth: String? = null
    private var receivedFresh = false
    private var cancelled = false

    fun configure(count: Int, name: String) {
        link?.close()
        link = null
        require(count == 1 && name == "A" || count in 2..CrewTopology.MAX_CAMERAS && name in CrewTopology.names(count))
        size = count; camera = name
        connected = false; online = emptySet(); sharePhotos = false; clearSelection()
        reset()
        note = if (size == 1) "Checking this phone." else "Waiting for nearby cameras."
        if (size > 1) {
            val transport = makeLink(camera, size, CrewLinkCallbacks(
                connection = { ready ->
                    connected = ready
                    if (!ready || singleCheck) { reset(); clearSelection() }
                    if (!ready) sharePhotos = false
                    changed()
                }, connectedCameras = { online = it; changed() }, message = ::receive, status = { note = it; changed() },
                authenticate = { endpoint, peer, digits ->
                    pendingAuth = endpoint
                    authenticate(peer, digits) { yes -> if (pendingAuth == endpoint) link?.confirm(endpoint, yes) }
                }, clearAuthentication = { endpoint -> if (endpoint == null || endpoint == pendingAuth) pendingAuth = null }))
            link = transport
            transport.start(name == "A")
        }
        changed()
    }

    private fun reset() {
        if (pastRuns.size>=24) pastRuns.removeFirst()
        pastRuns.addLast(run)
        cancelCheck(); leader = null; clearAssignment()
        shots.clear(); hiddenPhotos.clear(); lastChoice = null; lastVerdict = null
        selfAccepted=false; selfReady=false; singleCheck=false; subject=""
        run = uuid(); round = ""; needsFrame = false; originals.clear(); receivedFresh = false; cancelled = false
    }
    private fun clearAssignment() { offer = null; accepted = false; done = false }

    fun begin(target: String) {
        require(camera == "A")
        reset()
        subject = target.trim().also { require(it.isNotEmpty() && it.length <= 80) }
        singleCheck = size==1 || !connected
        if (singleCheck) {
            round = uuid(); needsFrame = true; note = "Checking your view."
        } else {
            leader = TeamRound(size, subject, run)
            askFrames(false)
        }
        changed()
    }

    fun fresh() {
        if (leader?.recheck() == true) { askFrames(true); changed() }
    }

    private fun askFrames(fresh: Boolean) {
        shots.clear(); hiddenPhotos.clear()
        round = leader!!.round; needsFrame = true; receivedFresh = fresh
        note = if (fresh) "Checking a new frame after the crew marked ready." else "Each camera must confirm the subject."
        event(base("check", "*").put("fresh", fresh).put("subject", subject))
        transmit(base("check", "*").put("fresh", fresh).put("subject", subject))
    }

    fun submitFrame(shot: CheckedShot) {
        if (!needsFrame || cancelled) return
        needsFrame = false
        val view=shot.view
        val data = base("view", "A").put("frame", view.frame).put("framing", view.framing)
            .put("checked_at",shot.checkedAt)
            .put("source", view.source).put("latency_ms", view.latencyMs)
            .put("tighten", view.tighten).put("widen", view.widen)
        view.issue?.let { data.put("issue", it.name) }
        event(JSONObject(data.toString()))
        shots[camera]=shot
        if (singleCheck) {
            note = if (view.framing == "UNCERTAIN") view.issue?.guidance ?: "Reframe the subject and check again."
                else "Your view is " + view.framing.lowercase() + "." + if (size>1) " Waiting for the other cameras." else ""
        } else if (camera == "A") readView(camera, data) else {
            if (sharePhotos && connected && shot.photo!=null)
                CrewPhoto.encode(shot.photo)?.let { data.put("photo",it) }
            note = "View sent. Waiting for the crew."; transmit(data)
        }
        changed()
    }

    private fun readView(sender: String, data: JSONObject) {
        val team = leader ?: return
        val view = CameraView(data.getLong("frame"), data.getString("framing"), data.getBoolean("tighten"),
            data.getBoolean("widen"), data.getString("source"), data.getLong("latency_ms"),
            data.optString("issue").takeIf { it.isNotEmpty() }?.let(FramingIssue::valueOf))
        if (!team.observe(round, sender, view)) return
        if (sender!=camera) {
            val photo=if (sender in hiddenPhotos) null else CrewPhoto.decode(data.optString("photo"))
            shots[sender]=CheckedShot(view,data.optLong("checked_at"),photo)
        }
        note = team.message
        when (team.phase) {
            Phase.ASSIGNED -> team.choices.forEach { choice ->
                if (choice.camera == "A") { offer = choice; lastChoice = choice }
                else transmit(base("offer", choice.camera).put("target", choice.framing).put("action", choice.adjustment).put("backup", choice.backup))
                event(base("assignment", choice.camera).put("target", choice.framing).put("action", choice.adjustment))
            }
            Phase.RETRY, Phase.VERIFIED -> verdict(team)
            else -> note = "Views checked " + team.views.size + " / " + size
        }
    }
    fun accept() {
        if (offer == null || accepted || cancelled) return
        accepted = true; selfAccepted=true; note = "Follow the framing cue. Mark ready when the shot is set."
        signal("accept"); changed()
    }
    fun ready() {
        if (!accepted || done || cancelled) return
        done = true; selfReady=true; note = "Ready for a fresh check."
        signal("ready"); changed()
    }
    fun failure(reason: String) {
        if (cancelled) return
        note = reason; needsFrame = false
        if (size > 1 && round.isNotEmpty()) {
            if (camera == "A") leader?.let { it.fail(reason); verdict(it) }
            else { signal("decline"); cancelled = true; clearAssignment() }
        }
        changed()
    }
    private fun signal(kind: String) {
        val data = base(kind, "A")
        event(data)
        if (camera == "A") handleSignal(kind, "A") else transmit(data)
    }
    private fun handleSignal(kind: String, sender: String) {
        val team = leader ?: return
        when (kind) {
            "accept" -> team.accept(round, sender)
            "ready" -> team.complete(round, sender)
            "decline" -> { team.fail("Camera " + sender + " declined or could not check its view."); verdict(team) }
        }
    }
    private fun verdict(team: TeamRound) {
        lastVerdict=team.phase==Phase.VERIFIED
        val value = base("verdict", "*").put("verified", team.phase == Phase.VERIFIED).put("message", team.message)
        event(value); transmit(value); cancelled = true; clearAssignment(); needsFrame = false; note = team.message
    }
    fun setPhotoSharing(enabled:Boolean) {
        sharePhotos=enabled
        if (!enabled && connected && camera!="A" && round.isNotEmpty())
            transmit(base("hide_photo","A"))
        changed()
    }
    fun saved(original: Original) {
        val data = base("original", "A").put("run", original.run).put("file", original.name)
            .put("duration_ms", original.durationMs).put("bytes", original.bytes)
        event(data)
        if (run == original.run) {
            originals.add(camera)
            if (size > 1 && camera != "A") transmit(data)
        }
        changed()
    }

    private fun receive(text: String) {
        runCatching {
            val data = JSONObject(text)
            val sender = data.getString("sender"); val kind = data.getString("kind")
            val incomingRun = UUID.fromString(data.getString("run")).toString()
            val incomingRound = data.getString("round")
            if (kind == "check" && camera != "A" && sender == "A") {
                val target = data.getString("subject").also { require(it.isNotBlank() && it.length <= 80) }
                UUID.fromString(incomingRound)
                val fresh = data.getBoolean("fresh")
                if (fresh && (run != incomingRun || !done || cancelled || receivedFresh)) return
                if (!fresh && (run == incomingRun || incomingRun in pastRuns) || round == incomingRound) return
                if (!fresh) { reset(); clearSelection() } else { cancelCheck(); shots.clear(); hiddenPhotos.clear() }
                receivedFresh = fresh; run = incomingRun; round = incomingRound; subject = target
                needsFrame = true; note = "Confirm you see " + target + ", then check this camera."
            } else {
                if (incomingRun != run || (kind != "original" && incomingRound != round)) return
                if (camera == "A") when (kind) {
                    "view" -> readView(sender, data)
                    "hide_photo" -> { hiddenPhotos.add(sender); shots[sender]?.let { shots[sender]=it.copy(photo=null) } }
                    "accept", "ready", "decline" -> handleSignal(kind, sender)
                    "original" -> if (data.getLong("duration_ms") > 0 && data.getLong("bytes") > 0) originals.add(sender)
                } else if (sender == "A") when (kind) {
                    "offer" -> if (!cancelled && offer == null && !needsFrame) {
                        val choice = CoverageAllocator.Choice(camera, data.getString("target"), data.getString("action"), data.getBoolean("backup"))
                        require(choice.framing in setOf("WIDE", "MEDIUM", "CLOSE") && choice.adjustment in setOf("HOLD", "WIDEN_CURRENT", "TIGHTEN_CURRENT"))
                        offer = choice; lastChoice=choice; note = "The crew has a framing suggestion."
                    }
                    "verdict" -> { cancelCheck(); lastVerdict=data.getBoolean("verified"); cancelled = true; needsFrame = false; clearAssignment(); note = data.getString("message").take(200) }
                }
            }
            data.remove("photo")
            event(data.put("direction", "received")); changed()
        }.onFailure { note = "A crew message could not be read."; changed() }
    }

    private fun base(kind: String, to: String) = JSONObject().put("event", kind).put("kind", kind)
        .put("version", CrewWire.VERSION).put("sender", camera).put("to", to)
        .put("run", run).put("round", round).put("transport", if (size > 1) transportLabel else "single phone")
    private fun transmit(data: JSONObject) { if (link?.send(data.toString()) != true) note = "Delivery failed. Reconnect the crew and start a new check." }
    fun close() { link?.close(); link = null }
    private fun uuid() = UUID.randomUUID().toString()
}
