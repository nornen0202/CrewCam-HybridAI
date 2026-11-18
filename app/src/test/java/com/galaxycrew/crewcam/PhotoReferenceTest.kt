package com.galaxycrew.crewcam

import org.junit.Assert.*
import org.junit.Test

class PhotoReferenceTest {
    private fun scene(dx: Int = 0, dy: Int = 0, duplicate: Boolean = false): IntArray {
        val side = AppearanceReference.SIDE
        return IntArray(side * side) { i ->
            val x = i % side
            val y = i / side
            fun patch(px: Int, py: Int): Int? {
                if (px !in 0..13 || py !in 0..15) return null
                return if (px < 5) 0xffad1609.toInt()
                else if (py < 7) 0xff2b6734.toInt() else 0xffe6dd94.toInt()
            }
            patch(x - 16 - dx, y - 17 - dy)
                ?: (if (duplicate) patch(x - 44, y - 40) else null)
                ?: 0xff343230.toInt()
        }
    }

    private val area = UnitBox(16 / 64f, 17 / 64f, 30 / 64f, 33 / 64f)

    @Test
    fun selectionFollowsDetailInANewFullFrame() {
        val ref = AppearanceReference(25, "drag", area, "bottle", scene())
        val match = ref.locate(scene(12, 4))!!
        assertEquals(area.x + 12 / 64f, match.region.x, .04f)
        assertEquals(area.y + 4 / 64f, match.region.y, .04f)
        assertNull(ref.locate(IntArray(64 * 64) { 0xff343230.toInt() }))
    }

    @Test
    fun flatAndAmbiguousReferencesDoNotAssignASubject() {
        val flat = IntArray(64 * 64) { 0xff010101.toInt() }
        assertNull(AppearanceReference(4, "tap", area, "prop", flat).locate(flat))
        val same = scene(duplicate = true)
        assertNull(AppearanceReference(4, "drag", area, "prop", same).locate(same))
    }

    @Test
    fun fitRejectsLetterboxAndReversedDragsUsePhotoCoordinates() {
        val portrait = PhotoFit(200, 400, 200, 200)
        assertNull(portrait.point(30f, 50f))
        assertEquals(.5f, portrait.point(100f, 200f)!!.second, 0f)
        val landscape = PhotoFit(500, 200, 100, 200)
        assertNull(landscape.point(190f, 100f))
        assertEquals(.5f, landscape.point(250f, 100f)!!.first, 0f)
        assertEquals(UnitBox(.1f, .2f, .8f, .9f), UnitBox.drag(.8f, .9f, .1f, .2f))
        assertNull(UnitBox.drag(.3f, .3f, .301f, .305f))
    }

    @Test
    fun framingUsesTheWholeSubjectAndRejectsAnotherInstance() {
        val raw =
            """{"evidence":"red bottle","target_visible":true,"extent_clear":true,"box_2d":[200,200,750,700],"frame_edge_cut":false}"""
        val inside = FrameGroundingContract.parse(raw, UnitBox.around(.4f, .4f))
        assertEquals("MEDIUM", inside.framing)
        val outside = FrameGroundingContract.parse(raw, UnitBox.around(.92f, .92f))
        assertEquals(FramingIssue.REFERENCE, outside.issue)
        assertEquals("UNCERTAIN", outside.framing)
    }
}
