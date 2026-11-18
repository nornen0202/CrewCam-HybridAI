package crewcam.core

/** Bounded protocol capacity, not a claim that eight simultaneous phones have been validated. */
object CrewTopology {
    const val MAX_CAMERAS = 8
    fun cameras(size: Int): List<CameraId> {
        require(size in 2..MAX_CAMERAS)
        return CameraId.entries.take(size)
    }
    fun names(size: Int) = cameras(size).map { it.name }
    val allowedNames get() = CameraId.entries.map { it.name }.toSet()
}
