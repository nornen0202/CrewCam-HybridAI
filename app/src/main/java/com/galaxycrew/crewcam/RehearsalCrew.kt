package com.galaxycrew.crewcam

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Two crop windows on one source file. All messages stay in this process. */
internal class RehearsalCrew(private val root: File, private val source: RehearsalSource) {
    private val callbacks = mutableMapOf<String, CrewLinkCallbacks>()
    private val messages = ArrayDeque<Pair<String, String>>()
    private val events = JSONArray()
    private val inputs = JSONArray()
    private val initial = mutableMapOf<String, GroundedFraming>()
    val crops = mutableMapOf("A" to VirtualCrop(), "B" to VirtualCrop())
    val lead = make("A")
    private val partner = make("B")
    private val ai = HybridInference { event ->
        events.put(event)
        save()
    }
    var branch = "undecided"
        private set

    var message = ""
        private set

    var outcome = "started"
        private set

    private var started = false
    private var useReference: AppearanceReference? = null
    val run
        get() = lead.run

    init {
        callbacks.forEach { (id, h) ->
            h.connectedCameras(setOf(if (id == "A") "B" else "A"))
            h.connection(true)
        }
        partner.setPhotoSharing(true)
    }

    private fun make(name: String): Room {
        val r =
            Room(
                { id, _, hooks ->
                    callbacks[id] = hooks
                    object : CrewTransport {
                        override fun start(host: Boolean) {}

                        override fun confirm(endpoint: String, accepted: Boolean) {}

                        override fun close() {}

                        override fun send(message: String): Boolean {
                            messages.addLast((if (id == "A") "B" else "A") to message)
                            return true
                        }
                    }
                },
                { events.put(it) },
                {},
                {},
                {},
                transportLabel = "virtual views on one device",
                authenticate = { _, _, _ -> },
            )
        r.configure(2, name)
        return r
    }

    private fun deliver() {
        while (messages.isNotEmpty()) {
            val (to, msg) = messages.removeFirst()
            callbacks.getValue(to).message(msg)
        }
    }

    fun begin(subject: String, reference: AppearanceReference?) {
        lead.begin(subject)
        deliver()
        started = true
        useReference = reference
        save()
    }

    suspend fun observeBoth(fresh: Boolean, progress: (String) -> Unit) {
        if (fresh) {
            lead.fresh()
            deliver()
            check(lead.freshRequest)
        }
        val movie =
            withContext(Dispatchers.IO) {
                source.frame(if (fresh) RehearsalSource.FRESH_US else RehearsalSource.INITIAL_US)
            }
        try {
            val aim = useReference?.let { ref ->
                val result =
                    withContext(Dispatchers.Default) { ref.locate(referencePixels(movie.image)) }
                        ?: throw ReferenceLost()
                LocatedReference(ref, result)
            }
            for ((id, room) in listOf("A" to lead, "B" to partner)) {
                progress("Checking virtual " + id + " on this phone…")
                val crop = crops.getValue(id)
                val view = movie.viewport(crop)
                try {
                    val rect = crop.pixelRect(movie.image.width, movie.image.height)
                    val rawHash =
                        withContext(Dispatchers.Default) { RehearsalSource.pixelHash(view) }
                    val record =
                        JSONObject()
                            .put("stage", if (fresh) "fresh" else "initial")
                            .put("camera", id)
                            .put("source_pts_us", movie.ptsUs)
                            .put("source_frame_index", movie.index)
                            .put("frame_id", movie.id)
                            .put(
                                "crop_xyxy_px",
                                JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)),
                            )
                            .put("input_width", view.width)
                            .put("input_height", view.height)
                            .put("input_argb_sha256", rawHash)
                            .put("round", room.round)
                    inputs.put(record)
                    save()
                    val marker = aim?.let {
                        val area = crop.project(it.match.region) ?: throw ReferenceLost()
                        LocatedReference(it.reference, it.match.copy(region = area))
                    }
                    val result =
                        kotlinx.coroutines.withTimeout(100_000) {
                            ai.observe(
                                room.run,
                                room.round,
                                id,
                                room.subject,
                                Frame(view, movie.id, "video rehearsal"),
                                Route.PRIVATE,
                                projectedReference = marker,
                            )
                        }
                    val reading = result.grounding
                    record
                        .put("framing", reading.framing)
                        .put("source", result.receipt.getString("source"))
                        .put("model", result.receipt.optString("local_model"))
                        .put("sdk_ms", result.receipt.getLong("sdk_ms"))
                        .put("request", result.receipt.getString("request"))
                        .put("issue", reading.issue?.name ?: JSONObject.NULL)
                    if (!fresh) initial[id] = reading
                    val proposed =
                        proposedCrop(
                            crop,
                            reading,
                            if (reading.framing == "WIDE") "MEDIUM" else "CLOSE",
                        )
                    val canTighten = proposed != null && proposed.fraction < crop.fraction
                    room.submitFrame(
                        CheckedShot(
                            CameraView(
                                movie.id,
                                reading.framing,
                                canTighten,
                                crop.fraction < .99f,
                                result.receipt.getString("source"),
                                result.receipt.getLong("sdk_ms"),
                                reading.issue,
                            ),
                            System.currentTimeMillis(),
                            CrewPhoto.thumbnail(view),
                        )
                    )
                    deliver()
                    save()
                } finally {
                    if (view !== movie.image) view.recycle()
                }
            }
            outcome =
                when (lead.lastVerdict) {
                    true -> "verified"
                    false -> "retry"
                    null -> "assigned"
                }
            message = lead.note
        } finally {
            movie.image.recycle()
            save()
        }
    }

    fun choose(adjust: Boolean) {
        check(lead.leader?.phase == Phase.ASSIGNED)
        branch = if (adjust) "apply suggested framing" else "keep original framing"
        if (adjust)
            for (choice in lead.leader!!.choices) {
                if (choice.adjustment == "HOLD") continue
                val current = crops.getValue(choice.camera)
                crops[choice.camera] =
                    proposedCrop(current, initial.getValue(choice.camera), choice.framing)
                        ?: current
            }
        lead.accept()
        partner.accept()
        deliver()
        lead.ready()
        partner.ready()
        deliver()
        save()
    }

    private fun proposedCrop(
        current: VirtualCrop,
        reading: GroundedFraming,
        goal: String,
    ): VirtualCrop? {
        if (reading.framing == "UNCERTAIN") return null
        val raw = reading.observation.getJSONObject("raw_observation")
        val coords = raw.getJSONArray("box_2d")
        val box =
            UnitBox.drag(
                coords.getInt(1) / 1000f,
                coords.getInt(0) / 1000f,
                coords.getInt(3) / 1000f,
                coords.getInt(2) / 1000f,
            ) ?: return null
        val target =
            when (goal) {
                "MEDIUM" -> .61f
                "CLOSE" -> .90f
                else -> .31f
            }
        val zoom = (target / maxOf(box.width, box.height)).coerceIn(.5f, 6f)
        val next = current.scaledAround(box.x, box.y, zoom)
        val projected =
            next.project(
                UnitBox(
                    current.x + box.left * current.fraction,
                    current.y + box.top * current.fraction,
                    (current.x + box.right * current.fraction).coerceAtMost(1f),
                    (current.y + box.bottom * current.fraction).coerceAtMost(1f),
                )
            ) ?: return null
        if (
            goal != "CLOSE" &&
                (projected.left < .01f ||
                    projected.right > .99f ||
                    projected.top < .01f ||
                    projected.bottom > .99f)
        )
            return null
        return next
    }

    fun fail(note: String) {
        message = note
        outcome = "error"
        save()
    }

    fun playback(firstMs: Int, lastMs: Int, frames: Int) {
        events.put(
            JSONObject()
                .put("event", "paired_virtual_playback")
                .put("source", "one decoder")
                .put("first_position_ms", firstMs)
                .put("last_position_ms", lastMs)
                .put("displayed_updates", frames)
        )
        save()
    }

    fun summary(): String =
        (0 until inputs.length()).joinToString("\n") { i ->
            val x = inputs.getJSONObject(i)
            x.getString("camera") +
                " · " +
                x.getString("stage") +
                " · " +
                x.optString("framing", "incomplete") +
                " · " +
                x.optString("source", "pending") +
                " · " +
                x.optString("model", "") +
                " · " +
                x.optLong("sdk_ms") +
                " ms\n" +
                x.getLong("source_pts_us") / 1_000_000.0 +
                " s · " +
                x.getJSONArray("crop_xyxy_px") +
                " · " +
                x.getString("input_argb_sha256").take(12)
        }

    fun save() {
        if (!started) return
        root.mkdirs()
        val record =
            JSONObject()
                .put("run", run)
                .put("build", BuildConfig.VERSION_NAME)
                .put("device", android.os.Build.MODEL)
                .put("physical_phones", 1)
                .put("virtual_views", 2)
                .put(
                    "input_origin",
                    if (source.generated) "generated Flow video" else "imported local video",
                )
                .put("source_sha256", source.digest)
                .put("subject", lead.subject)
                .put("transport", "in process")
                .put("branch", branch)
                .put("result", outcome)
                .put("message", message)
                .put("inputs", inputs)
                .put("events", events)
        val target = File(root, run + ".json")
        val tmp = File(root, run + ".tmp")
        tmp.writeText(record.toString(2))
        java.nio.file.Files.move(
            tmp.toPath(),
            target.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
    }
}
