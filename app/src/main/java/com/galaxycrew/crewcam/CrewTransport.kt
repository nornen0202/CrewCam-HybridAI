package com.galaxycrew.crewcam

interface CrewTransport {
    fun start(host: Boolean)
    fun send(message: String): Boolean
    fun close()
}
