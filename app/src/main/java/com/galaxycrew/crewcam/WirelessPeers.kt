package com.galaxycrew.crewcam

import crewcam.core.CrewTopology

import org.json.JSONObject

/** Camera identity is bound only after both people confirm the encrypted connection. */
internal class WirelessPeers(val camera: String, val size: Int) {
    private val expected = if (camera == "A") CrewTopology.names(size).drop(1).toSet() else setOf("A")
    private val pending = linkedMapOf<String, String>()
    private val approved = mutableSetOf<String>()
    private val connected = linkedMapOf<String, String>()
    init { require(size in 2..CrewTopology.MAX_CAMERAS && camera in CrewTopology.names(size)) }
    val info get() = "CrewCamHybrid:$camera:$size"
    val complete get() = connected.values.toSet() == expected
    val count get() = connected.size
    val online get() = connected.values.toSet()
    fun cameraFor(endpoint: String): String? = connected[endpoint] ?: pending[endpoint]
    fun reserve(endpoint: String, info: String): Boolean {
        val parts = info.split(':')
        if (parts.size != 3 || parts[0] != "CrewCamHybrid" || parts[2] != size.toString()) return false
        val remote = parts[1]
        if (remote !in expected || endpoint in connected || endpoint in pending ||
            remote in connected.values || remote in pending.values) return false
        pending[endpoint] = remote
        return true
    }
    fun approve(endpoint: String): Boolean = endpoint in pending && approved.add(endpoint)
    fun establish(endpoint: String): Boolean {
        if (!approved.remove(endpoint)) return false
        connected[endpoint] = pending.remove(endpoint) ?: return false
        return true
    }
    fun remove(endpoint: String) { pending.remove(endpoint); approved.remove(endpoint); connected.remove(endpoint) }
    fun clear() { pending.clear(); approved.clear(); connected.clear() }
    fun recipients(text: String): List<String> {
        val message = JSONObject(text)
        require(message.getInt("version") == CrewWire.VERSION && message.getString("sender") == camera)
        val to = message.getString("to")
        require(to == "*" || to in expected)
        return connected.filterValues { to == "*" || to == it }.keys.toList()
    }
    fun accepts(endpoint: String, text: String): Boolean = runCatching {
        val sender = connected[endpoint] ?: return false
        val message = JSONObject(text)
        message.getInt("version") == CrewWire.VERSION && message.getString("sender") == sender &&
            message.getString("to") in setOf(camera, "*") && !message.has("relay")
    }.getOrDefault(false)
}
