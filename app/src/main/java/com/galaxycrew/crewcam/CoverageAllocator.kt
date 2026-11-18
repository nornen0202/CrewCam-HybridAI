package com.galaxycrew.crewcam

import crewcam.core.CrewTopology

internal class CoverageUnavailable : IllegalStateException(
    "Both views are too tight. Give one camera more room, then check again.")

/** Deterministic application policy: maximize distinct observed/feasible coverage, then minimize adjustment.
 * Never upgrades a match verdict; identical wording cannot count as different coverage. */
internal object CoverageAllocator {
    data class Choice(val camera: String, val framing: String, val adjustment: String, val backup: Boolean = false)
    fun assign(
        observations: Map<String, CoverageObservation>,
        previousTargets: Map<String, String> = emptyMap(),
        widenCameras: Set<String> = emptySet(),
    ): List<Choice> {
        val cameras = observations.keys.sorted()
        require(cameras == CrewTopology.names(cameras.size))
        observations.values.forEach { require(it.framing in setOf("WIDE", "MEDIUM", "CLOSE")) }
        if (previousTargets.keys == cameras.toSet() &&
            cameras.all { observations.getValue(it).framing == previousTargets.getValue(it) } &&
            observations.values.map { it.framing }.distinct().size >= 2) {
            val seen = mutableSetOf<String>()
            return cameras.map { camera ->
                val framing = observations.getValue(camera).framing
                Choice(camera, framing, "VERIFIED_HOLD", backup = !seen.add(framing))
            }
        }
        val choices = cameras.map { camera ->
            val observation = observations.getValue(camera)
            buildList {
                add(Choice(camera, observation.framing, "HOLD"))
                if (observation.tightenSafe && observation.framing != "CLOSE")
                    add(Choice(camera, if (observation.framing == "WIDE") "MEDIUM" else "CLOSE", "TIGHTEN_CURRENT"))
                if (camera in widenCameras && observation.framing != "WIDE")
                    add(Choice(camera, if (observation.framing == "CLOSE") "MEDIUM" else "WIDE", "WIDEN_CURRENT"))
            }
        }
        var best = emptyList<Choice>(); var bestScore = Int.MIN_VALUE
        fun visit(index: Int, current: List<Choice>) {
            if (index < choices.size) { choices[index].forEach { visit(index + 1, current + it) }; return }
            // A is the context anchor on ties. Stable iteration makes repeated observations stable.
            val penalty = current.sumOf {
                when (it.adjustment) {
                    "HOLD" -> 0
                    "TIGHTEN_CURRENT" -> 10
                    else -> 15
                } as Int
            }
            val score = current.map { it.framing }.distinct().size * 100 -
                penalty - if (current.first().adjustment == "HOLD") 0 else 1
            if (score > bestScore) { bestScore = score; best = current }
        }
        visit(0, emptyList())
        if (best.map { it.framing }.distinct().size < 2) throw CoverageUnavailable()
        val seen = mutableSetOf<String>()
        return best.map { it.copy(backup = !seen.add(it.framing)) }
    }
}
