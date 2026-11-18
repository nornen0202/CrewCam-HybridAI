package com.galaxycrew.crewcam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VirtualCropTest {
    @Test
    fun virtualZoomCentersTheObservedSubjectInTheNewInput() {
        val subject = UnitBox(.55f, .60f, .65f, .70f)
        val zoomed = VirtualCrop().scaledAround(subject.x, subject.y, 4f)
        val matched = requireNotNull(zoomed.project(subject))
        assertEquals(.25f, zoomed.fraction, .001f)
        assertEquals(.5f, matched.x, .001f)
        assertEquals(.5f, matched.y, .001f)
        assertEquals(.4f, matched.width, .001f)
        assertEquals(.4f, matched.height, .001f)
    }

    @Test
    fun edgeZoomStaysInsideSourceAndRejectsAnOutsideReference() {
        val rightEdge = VirtualCrop().scaledAround(.99f, .5f, 4f)
        assertEquals(1f, rightEdge.x + rightEdge.fraction, .001f)
        assertNull(rightEdge.project(UnitBox(.1f, .4f, .2f, .5f)))
        val full = rightEdge.scaledAround(.5f, .5f, .1f)
        assertEquals(VirtualCrop(), full)
    }
}
