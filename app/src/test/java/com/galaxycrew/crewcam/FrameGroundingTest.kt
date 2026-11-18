package com.galaxycrew.crewcam

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FrameGroundingTest {
    companion object {
        fun reply(framing: String, safe: Boolean = true): String {
            val box = when (framing) {
                "CLOSE" -> listOf(50, 100, 950, 900)
                "WIDE" -> listOf(350, 350, 650, 650)
                else -> if (safe) listOf(275, 275, 725, 725) else listOf(200, 200, 800, 800)
            }
            return JSONObject().put("evidence", "target visible extent")
                .put("target_visible", true).put("extent_clear", framing != "UNCERTAIN")
                .put("box_2d", JSONArray(box)).put("frame_edge_cut", false).toString()
        }
    }
    private fun withBox(box: List<Number>) = JSONObject(reply("MEDIUM")).put("box_2d", JSONArray(box))
    private fun parse(value: JSONObject) = FrameGroundingContract.parse(value.toString())

    @Test fun actual046InputsRemainHistoricalFailuresButExposeEnumDisagreement() {
        assertEquals("MEDIUM", parse(withBox(listOf(243, 434, 893, 674))).framing)
        val close = parse(withBox(listOf(100, 150, 999, 999)))
        assertEquals("CLOSE", close.framing)
        assertFalse(close.tightenSafe)
        assertEquals("MEDIUM", parse(withBox(listOf(313, 464, 933, 624))).framing)
        assertEquals("MODEL_LOCALIZATION_APP_FRAMING", close.observation.getString("origin"))
        assertEquals("visible-extent-v1", close.observation.getString("revision"))
    }

    @Test fun negativeOrUnclearResultCannotBePromotedByBox() {
        for (key in listOf("target_visible", "extent_clear")) {
            val result = parse(JSONObject(reply("WIDE")).put(key, false))
            assertEquals("UNCERTAIN", result.framing)
            assertFalse(result.tightenSafe)
        }
    }

    @Test fun malformedCoordinatesAndTypesAreRejectedWithoutRepair() {
        for (evidence in listOf(123, true)) assertThrows(IllegalArgumentException::class.java) {
            parse(JSONObject(reply("MEDIUM")).put("evidence", evidence))
        }
        for (box in listOf(listOf(700, 100, 200, 900), listOf(0, 100, 0, 900),
            listOf(-1, 100, 600, 900), listOf(100, 100, 1001, 900), listOf(100.5, 100, 600, 900))) {
            assertThrows(IllegalArgumentException::class.java) { parse(withBox(box)) }
        }
        assertThrows(IllegalArgumentException::class.java) { parse(JSONObject(reply("MEDIUM")).put("target_visible", "true")) }
        assertThrows(IllegalArgumentException::class.java) { parse(JSONObject(reply("MEDIUM")).put("extent_clear", JSONObject.NULL)) }
    }

    @Test fun fixedDeadbandsHoldAtEveryBoundaryAndDoNotUseDesiredTarget() {
        for ((span, expected) in listOf(399 to "WIDE", 400 to "WIDE", 401 to "UNCERTAIN",
            449 to "UNCERTAIN", 450 to "MEDIUM", 451 to "MEDIUM", 799 to "MEDIUM",
            800 to "MEDIUM", 801 to "UNCERTAIN", 849 to "UNCERTAIN", 850 to "CLOSE", 851 to "CLOSE")) {
            assertEquals("span=$span", expected, parse(withBox(listOf(50, 400, 50 + span, 600))).framing)
        }
    }

    @Test fun tallThinAndWideShortTargetsUseMaxExtentAndSmallEdgeTargetsAreUncertain() {
        assertEquals("CLOSE", parse(withBox(listOf(50, 450, 950, 550))).framing)
        assertEquals("CLOSE", parse(withBox(listOf(450, 50, 550, 950))).framing)
        assertEquals("UNCERTAIN", parse(withBox(listOf(10, 300, 310, 600))).framing)
        assertEquals("CLOSE", parse(withBox(listOf(10, 300, 310, 600)).put("frame_edge_cut", true)).framing)
    }

    @Test fun zoomSafetyRequiresEntireVisibleExtentInsideConservativeCenteredCrop() {
        assertTrue(parse(withBox(listOf(275, 275, 725, 725))).tightenSafe)
        for (box in listOf(listOf(274, 275, 725, 725), listOf(275, 274, 725, 725),
            listOf(275, 275, 726, 725), listOf(275, 275, 725, 726))) assertFalse(parse(withBox(box)).tightenSafe)
        assertFalse(parse(withBox(listOf(275, 275, 725, 725)).put("frame_edge_cut", true)).tightenSafe)
    }

    @Test fun visibleBottleBetweenSizesReceivesMovementAdvice() {
        val liveBox = withBox(listOf(320, 559, 737, 678))
        assertEquals(FramingIssue.BETWEEN, parse(liveBox).issue)
        assertEquals(FramingIssue.MISSING, parse(liveBox.put("target_visible", false)).issue)
        assertEquals(FramingIssue.EDGE, parse(withBox(listOf(5, 300, 310, 600))).issue)
    }

    @Test fun repeatedCoordinatesFromNanoCannotChooseAnAngle() {
        val duplicate = reply("WIDE").replace("\"box_2d\":", "\"box_2d\":[0,0,990,990],\"box_2d\":")
        assertThrows(LocalOutputFailure::class.java) { FrameGroundingContract.parse(duplicate) }
    }
}
