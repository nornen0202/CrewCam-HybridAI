package com.galaxycrew.crewcam

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** In-memory Nearby message simulation. The two participants are not real phones. */
class CrewRoomTest {
    private class PairOfRooms {
        val hooks = mutableMapOf<String, CrewLinkCallbacks>()
        val messages = mutableListOf<JSONObject>()
        val journal = mutableListOf<JSONObject>()
        val pending = ArrayDeque<Pair<String, String>>()

        fun room(name: String): Room {
            val room =
                Room(
                    { id, _, h ->
                        hooks[id] = h
                        object : CrewTransport {
                            override fun start(host: Boolean) {}

                            override fun close() {
                                h.connection(false)
                            }

                            override fun confirm(endpoint: String, accepted: Boolean) {}

                            override fun send(message: String): Boolean {
                                messages += JSONObject(message)
                                pending.addLast((if (id == "A") "B" else "A") to message)
                                return true
                            }
                        }
                    },
                    journal::add,
                    {},
                    {},
                    {},
                    authenticate = { _, _, _ -> },
                )
            room.configure(2, name)
            return room
        }

        fun connected() {
            hooks.forEach { (id, h) ->
                h.connectedCameras(setOf(if (id == "A") "B" else "A"))
                h.connection(true)
            }
        }

        fun deliver() {
            while (pending.isNotEmpty()) {
                val (dest, msg) = pending.removeFirst()
                hooks.getValue(dest).message(msg)
            }
        }
    }

    private fun shot(t: Long, f: String) =
        CheckedShot(CameraView(t, f, true, true, "ON_DEVICE", 5), 123456L, null)

    @Test
    fun soloLeadCanInspectAWhileBIsMissingAndConnectionClearsTheTake() {
        val pair = PairOfRooms()
        val a = pair.room("A")
        a.begin("bottle")
        a.submitFrame(shot(100, "WIDE"))
        assertEquals(setOf("A"), a.shots.keys)
        assertFalse(a.connected)
        assertNull(a.leader)
        assertTrue(pair.messages.isEmpty())
        pair.connected()
        assertTrue(a.shots.isEmpty())
    }

    @Test
    fun metadataOnlyPairMovesThroughAcceptedFreshAndOriginalStates() {
        val p = PairOfRooms()
        val a = p.room("A")
        val b = p.room("B")
        p.connected()
        a.begin("bottle")
        p.deliver()
        assertTrue(b.needsFrame)
        assertEquals("bottle", b.subject)
        a.submitFrame(shot(100, "WIDE"))
        b.submitFrame(shot(450, "WIDE"))
        p.deliver()
        assertEquals(setOf("A", "B"), a.shots.keys)
        assertTrue(p.messages.none { it.has("photo") })
        assertEquals("HOLD", a.lastChoice!!.adjustment)
        assertEquals("TIGHTEN_CURRENT", b.lastChoice!!.adjustment)
        a.accept()
        b.accept()
        a.ready()
        b.ready()
        p.deliver()
        assertEquals(setOf("A", "B"), a.leader!!.accepted)
        assertEquals(setOf("A", "B"), a.leader!!.done)
        a.fresh()
        p.deliver()
        assertTrue(a.shots.isEmpty())
        assertTrue(b.shots.isEmpty())
        a.submitFrame(shot(121, "WIDE"))
        b.submitFrame(shot(469, "MEDIUM"))
        p.deliver()
        assertEquals(true, a.lastVerdict)
        assertEquals(true, b.lastVerdict)
        val seal = p.messages.last { it.getString("kind") == "view" }
        val saved =
            JSONObject(seal.toString())
                .put("kind", "original")
                .put("duration_ms", 5000)
                .put("bytes", 90)
        p.hooks.getValue("A").message(saved.toString())
        assertEquals(setOf("B"), a.originals)
        p.hooks.getValue("A").connection(false)
        assertTrue(a.shots.isEmpty())
        assertTrue(a.originals.isEmpty())
        p.hooks.getValue("A").message(saved.toString())
        assertTrue(a.originals.isEmpty())
    }

    @Test
    fun retryAndDelayedShotsCannotRepublishAnOldPhoto() {
        val p = PairOfRooms()
        val a = p.room("A")
        val b = p.room("B")
        p.connected()
        a.begin("bottle")
        p.deliver()
        a.submitFrame(shot(1, "WIDE"))
        b.submitFrame(shot(17, "WIDE"))
        p.deliver()
        val old = p.messages.last { it.getString("kind") == "view" }
        val oldCheck = p.messages.first { it.getString("kind") == "check" }
        b.failure("Declined")
        p.deliver()
        assertEquals(false, a.lastVerdict)
        a.begin("cup")
        p.deliver()
        val newRun = b.run
        p.hooks.getValue("A").message(old.toString())
        p.hooks.getValue("B").message(oldCheck.toString())
        assertEquals(newRun, b.run)
        assertTrue(a.shots.isEmpty())
        assertTrue(p.journal.none { it.has("photo") })
    }
}
