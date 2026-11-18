package com.galaxycrew.crewcam

import crewcam.core.CrewTopology
import java.util.UUID

internal data class CameraView(val frame: Long, val framing: String, val tighten: Boolean,
                               val widen: Boolean, val source: String, val latencyMs: Long,
                               val issue: FramingIssue? = null) {
    init {
        require(frame > 0 && framing in setOf("WIDE", "MEDIUM", "CLOSE", "UNCERTAIN"))
        require(source in setOf("ON_DEVICE", "IN_CLOUD") && latencyMs >= 0)
    }
}

internal enum class Phase { OBSERVE, ASSIGNED, RECHECK, VERIFIED, RETRY }

/** Sensor clocks are compared only with frames from the same camera in this run. */
internal class TeamRound(size: Int, val subject: String, val run: String = UUID.randomUUID().toString()) {
    val cameras = CrewTopology.names(size)
    var phase = Phase.OBSERVE
        private set
    var round = UUID.randomUUID().toString()
        private set
    private val initial = linkedMapOf<String, CameraView>()
    fun initialView(camera: String): CameraView? = initial[camera]
    val views = linkedMapOf<String, CameraView>()
    var choices = emptyList<CoverageAllocator.Choice>()
        private set
    val accepted = mutableSetOf<String>()
    val done = mutableSetOf<String>()
    var message = "Waiting for camera checks."
        private set

    init { require(subject.isNotBlank() && subject.length <= 80) }
    fun observe(id: String, camera: String, view: CameraView): Boolean {
        if (id != round || camera !in cameras || phase !in setOf(Phase.OBSERVE, Phase.RECHECK) || camera in views) return false
        if (view.frame <= (initial[camera]?.frame ?: 0L)) return false
        views[camera] = view
        if (view.framing == "UNCERTAIN") {
            fail("Camera " + camera + " · " + (view.issue?.guidance ?: "Reframe the subject and check again.")); return true
        }
        if (views.size < cameras.size) return true
        if (phase == Phase.RECHECK) {
            val matched = choices.all { views.getValue(it.camera).framing == it.framing }
            if (matched && views.values.map { it.framing }.distinct().size >= 2) {
                phase = Phase.VERIFIED
                message = "Fresh views match the assigned framing."
            } else fail("The fresh views still miss a requested framing. Check the scene again.")
            return true
        }
        choices = try {
            CoverageAllocator.assign(views.mapValues { CoverageObservation(it.value.framing, it.value.tighten) },
                widenCameras = views.filterValues { it.widen }.keys)
        } catch (_: CoverageUnavailable) { fail("Both views are tight. Widen one camera and check again."); return true }
        initial.putAll(views)
        phase = Phase.ASSIGNED
        message = "Accept the framing on each phone, then mark it ready."
        return true
    }
    fun accept(id: String, camera: String) =
        phase == Phase.ASSIGNED && round == id && camera in cameras && accepted.add(camera)
    fun complete(id: String, camera: String) =
        phase == Phase.ASSIGNED && round == id && camera in accepted && done.add(camera)
    fun recheck(): Boolean {
        if (phase != Phase.ASSIGNED || done != cameras.toSet()) return false
        phase = Phase.RECHECK
        round = UUID.randomUUID().toString()
        views.clear()
        message = "Checking fresh frames from each camera."
        return true
    }
    fun fail(reason: String) { phase = Phase.RETRY; message = reason }
}
