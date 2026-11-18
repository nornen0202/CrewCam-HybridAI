package com.galaxycrew.crewcam

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

/** Product 1:N transport. All state and callbacks are confined to the main thread. */
internal class NearbyStarTransport(
    context: Context,
    private val camera: String,
    private val crewSize: Int,
    private val connection: (Boolean) -> Unit,
    private val connectedCameras: (Set<String>) -> Unit,
    private val message: (String) -> Unit,
    private val status: (String) -> Unit,
    private val authenticate: (String, String, String) -> Unit,
    private val clearAuthentication: (String?) -> Unit,
) : CrewTransport {
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private var current: Run? = null
    override fun start(host: Boolean) {
        require(host == (camera == "A"))
        close()
        val run = Run()
        current = run
        status(if (host) "Opening the crew. Join on the other phones." else "Looking for lead camera A.")
        main.postDelayed({ if (current === run) run.start() }, 200)
    }
    override fun close() {
        val previous = current
        current = null
        previous?.peers?.clear()
        client.stopAdvertising(); client.stopDiscovery(); client.stopAllEndpoints()
        clearAuthentication(null)
        connectedCameras(emptySet())
        connection(false)
    }
    override fun send(message: String): Boolean = current?.send(message) == true
    override fun confirm(endpoint: String, accepted: Boolean) { current?.confirm(endpoint, accepted) }

    private inner class Run {
        val peers = WirelessPeers(camera, crewSize)
        private var outgoing: String? = null
        private var ready = false
        private fun alive() = current === this
        private fun failure(operation: String, error: Exception) {
            if (!alive()) return
            status("$operation failed (${(error as? ApiException)?.statusCode ?: -1}). Check nearby permissions, Wi-Fi and Bluetooth.")
        }
        fun start() {
            if (!alive()) return
            if (camera == "A") {
                client.startAdvertising(peers.info, SERVICE, lifecycle,
                    AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR)
                        .setConnectionType(ConnectionType.NON_DISRUPTIVE).build())
                    .addOnSuccessListener { if (alive()) status("Lead camera · waiting for 0 / ${crewSize - 1} peers") }
                    .addOnFailureListener { failure("Open crew", it) }
            } else discover()
        }
        private fun discover() {
            if (!alive() || camera == "A") return
            client.startDiscovery(SERVICE, discovery,
                    DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build())
                    .addOnSuccessListener { if (alive()) status("Searching for lead camera A") }
                    .addOnFailureListener { failure("Find crew", it) }
        }
        fun confirm(endpoint: String, accepted: Boolean) {
            if (!alive() || peers.cameraFor(endpoint) == null) return
            clearAuthentication(endpoint)
            if (!accepted) {
                client.rejectConnection(endpoint); peers.remove(endpoint); outgoing = null
                status("Pairing declined. You can reconnect.")
            } else if (peers.approve(endpoint)) {
                client.acceptConnection(endpoint, payload)
                    .addOnFailureListener { if (alive()) { peers.remove(endpoint); outgoing = null; failure("Pairing", it) } }
            }
        }
        fun send(text: String): Boolean {
            if (!alive()) return false
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.size !in 1..24_576) return false
            val recipients = runCatching { peers.recipients(text) }.getOrNull() ?: return false
            if (recipients.isEmpty()) return false
            client.sendPayload(recipients, Payload.fromBytes(bytes))
                .addOnFailureListener { failure("Message delivery", it) }
            return true
        }
        private fun changed() {
            connectedCameras(peers.online)
            val complete = peers.complete
            if (complete != ready) { ready = complete; connection(complete) }
            status("Nearby · ${peers.count} / ${if (camera == "A") crewSize - 1 else 1} peers connected")
        }
        private val discovery = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                if (!alive() || outgoing != null || peers.complete || info.endpointName != "CrewCamHybrid:A:$crewSize") return
                outgoing = endpointId
                client.requestConnection(peers.info, endpointId, lifecycle,
                    ConnectionOptions.Builder().setConnectionType(ConnectionType.NON_DISRUPTIVE).build())
                    .addOnFailureListener { if (alive()) { outgoing = null; failure("Connect to lead camera", it) } }
            }
            override fun onEndpointLost(endpointId: String) {}
        }
        private val lifecycle = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
                if (!alive()) return
                if ((camera != "A" && endpointId != outgoing) || !peers.reserve(endpointId, info.endpointName)) {
                    client.rejectConnection(endpointId); return
                }
                authenticate(endpointId, peers.cameraFor(endpointId)!!, info.authenticationDigits)
            }
            override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
                if (!alive()) return
                clearAuthentication(endpointId)
                if (resolution.status.isSuccess && peers.establish(endpointId)) {
                    if (camera != "A") client.stopDiscovery()
                    changed()
                } else {
                    client.disconnectFromEndpoint(endpointId)
                    peers.remove(endpointId); outgoing = null
                    changed()
                    status("Pairing incomplete (${resolution.status.statusCode}). Reconnect to the crew.")
                }
            }
            override fun onDisconnected(endpointId: String) {
                if (!alive()) return
                clearAuthentication(endpointId)
                peers.remove(endpointId); outgoing = null
                changed()
                status("A camera disconnected. Reconnect to check a new take.")
                // A restarting its team must not leave a previously connected member idle.
                // Discovery was stopped on successful connection; authentication is still required.
                if (camera != "A") {
                    client.stopDiscovery()
                    main.postDelayed({ if (alive() && !peers.complete && outgoing == null) discover() }, 300)
                }
            }
            override fun onBandwidthChanged(endpointId: String, info: BandwidthInfo) {
                if (alive()) status("Nearby · link quality ${info.quality} · camera ${peers.cameraFor(endpointId) ?: "pending"}")
            }
        }
        private val payload = object : PayloadCallback() {
            override fun onPayloadReceived(endpointId: String, value: Payload) {
                if (!alive() || value.type != Payload.Type.BYTES) return
                val bytes = value.asBytes() ?: return
                if (bytes.size !in 1..24_576) return
                val text = bytes.toString(Charsets.UTF_8)
                if (peers.accepts(endpointId, text)) message(text)
                else status("Ignored a message from an unpaired camera.")
            }
            override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
                if (alive() && update.status in setOf(PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED))
                    status("Delivery incomplete. Reconnect and confirm the pairing code.")
            }
        }
    }
    private companion object { const val SERVICE = "com.galaxycrew.crewcam.hybrid.star.v2" }
}
