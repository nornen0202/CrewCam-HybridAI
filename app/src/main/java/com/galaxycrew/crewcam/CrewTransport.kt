package com.galaxycrew.crewcam

internal data class CrewLinkCallbacks(
    val connection: (Boolean) -> Unit,
    val connectedCameras: (Set<String>) -> Unit,
    val message: (String) -> Unit,
    val status: (String) -> Unit,
    val authenticate: (String,String,String) -> Unit,
    val clearAuthentication: (String?) -> Unit,
)

internal interface CrewTransport {
    fun start(host: Boolean)
    fun confirm(endpoint: String, accepted: Boolean)
    fun send(message: String): Boolean
    fun close()
}
