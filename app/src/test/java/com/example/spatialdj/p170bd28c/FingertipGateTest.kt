package com.example.spatialdj.p170bd28c

import com.example.spatialdj.p170bd28c.ui.loop.FingertipGate
import org.junit.Assert.*
import org.junit.Test

class FingertipGateTest {
    private val pad=FingertipGate.Surface("pad",FingertipGate.Rect(100f,100f,200f,200f))
    private val second=FingertipGate.Surface("other",FingertipGate.Rect(600f,100f,200f,200f))
    private val slider=FingertipGate.Surface("slider",FingertipGate.Rect(100f,400f,700f,100f),true)
    private val surfaces=listOf(pad,second,slider)
    private fun gate()=FingertipGate().apply {
        widthMeters=1f; heightMeters=1f; widthPixels=1000f; heightPixels=1000f; active=true
    }
    private fun tip(id: Int=1,x: Float=200f,y: Float=200f,z: Float=.005f)=
        FingertipGate.Tip(id,x/1000-.5f,.5f-y/1000,z)
    private fun sample(g: FingertipGate,now: Long=100L,vararg tips: FingertipGate.Tip)=g.hands(now,tips.toList(),surfaces)

    @Test fun directContactNeedsNoNativePointerOrPinch() {
        val g=gate(); assertEquals("pad",sample(g,100,tip()).single().id)
    }
    @Test fun approachIsCyanButDoesNotStrikeFromDistance() {
        val g=gate(); assertTrue(sample(g,100,tip(z=.05f)).isEmpty())
        assertEquals(setOf("pad"),g.hover(100,surfaces))
        assertTrue(sample(g,110,tip(z=.013f)).isEmpty())
        assertEquals(1,sample(g,120,tip(z=.011f)).count { it.down })
    }
    @Test fun undersideCannotStrikeOnAppearanceOrOutwardCrossing() {
        val g=gate()
        for((i,z) in listOf(-.08f,-.02f,.004f,.020f).withIndex())
            assertTrue(sample(g,100+i*10L,tip(z=z)).none { it.down })
        sample(g,150,tip(z=.03f))
        assertTrue(sample(g,160,tip()).single().down)
    }
    @Test fun heldFingerNeverRetriggersAndHysteresisRejectsJitter() {
        val g=gate(); sample(g,100,tip())
        repeat(60) { i -> assertTrue(sample(g,110+i*5L,tip(z=if(i%2==0) .014f else .004f)).isEmpty()) }
        assertEquals(setOf("pad"),g.held())
    }
    @Test fun rapidLiftAndRetapWorkWithoutAWaitTimer() {
        val g=gate(); var time=100L
        repeat(100) {
            assertTrue(sample(g,time,tip()).single().down)
            assertTrue(sample(g,time+8,tip(z=.03f)).single().up)
            time+=16
        }
    }
    @Test fun indexRetractsOnlyTwoCentimetersToRepeatWithoutCooldown() {
        val g=gate()
        assertTrue(sample(g,100,tip(z=.011f)).single().down)
        assertTrue(sample(g,105,tip(z=.021f)).single().up)
        assertTrue(sample(g,110,tip(z=.011f)).single().down)
    }
    @Test fun fastCrossingBetweenFramesStrikesOnceAndReleases() {
        val g=gate(); sample(g,100,tip(z=.06f))
        val hits=sample(g,111,tip(z=-.06f))
        assertEquals(listOf(true,false),hits.map { it.down })
        assertTrue(hits.last().up); assertTrue(g.held().isEmpty())
        assertTrue(sample(g,122,tip(z=-.06f)).isEmpty())
    }
    @Test fun sweptContactUsesIntersectionNotFinalFingerPosition() {
        val g=gate(); sample(g,100,tip(x=240f,z=.06f))
        val hit=sample(g,111,tip(x=320f,z=-.05f))
        assertEquals("pad",hit.first().id); assertTrue(hit.first().down)
    }
    @Test fun trackingTeleportCannotBecomeASweptTap() {
        val g=gate(); sample(g,100,tip(z=.3f))
        assertTrue(sample(g,110,tip(z=-.2f)).none { it.down })
    }
    @Test fun onlyTwoIndexFingersCanStrikeAndHover() {
        val g=gate()
        val hits=sample(g,100,*Array(10) { i -> tip(i,if(i%2==0) 200f else 700f) })
        assertEquals(2,hits.count { it.down }); assertEquals(setOf(1,6),g.heldTips())
        assertEquals(setOf("pad","other"),g.held())
        val otherTips=(0..9).filterNot { it in FingertipGate.INDEX_IDS }.map { tip(it) }
        val excluded=gate()
        assertTrue(excluded.hands(100,otherTips,surfaces).isEmpty())
        assertTrue(excluded.hover(100,surfaces).isEmpty())
        assertTrue(excluded.tracked(100).isEmpty())
    }
    @Test fun oneFingerCannotActivateTwoOverlappingSurfaces() {
        val g=gate()
        assertEquals(1,g.hands(100,listOf(tip()),listOf(pad,pad.copy(id="overlap"))).count { it.down })
    }
    @Test fun missingTrackingReleasesAndRequiresRetractionOnReturn() {
        val g=gate(); sample(g,100,tip())
        assertTrue(sample(g,110).single().up)
        assertTrue(sample(g,120,tip()).isEmpty())
        sample(g,130,tip(z=.03f)); assertTrue(sample(g,140,tip()).single().down)
    }
    @Test fun staleTrackingCannotHoldOrSweep() {
        val g=gate(); sample(g,100,tip())
        assertTrue(g.expire(201).single().up)
        assertTrue(g.hover(201,surfaces).isEmpty())
        assertTrue(sample(g,202,tip()).isEmpty())
        sample(g,210,tip(z=.03f)); sample(g,220,tip())
        assertEquals(setOf("pad"),g.held())
    }
    @Test fun invalidTipsUnmeasuredPanelAndInactiveInputAreIgnored() {
        for(t in listOf(tip(x=Float.NaN),tip(z=Float.POSITIVE_INFINITY),tip(id=99))) {
            assertTrue(sample(gate(),100,t).isEmpty())
        }
        val g=gate(); g.widthMeters=0f; assertTrue(sample(g,100,tip()).isEmpty())
        g.widthMeters=1f; g.active=false; assertTrue(sample(g,100,tip()).isEmpty())
    }
    @Test fun sliderSetsPositionOnContactAndMovesWithoutPinch() {
        val g=gate(); assertEquals(0f,sample(g,100,tip(x=100f,y=450f)).single().value,0f)
        val h=sample(g,110,tip(x=800f,y=450f)).single()
        assertFalse(h.down); assertEquals(1f,h.value,0f)
    }
    @Test fun sliderClampsButCannotJumpToAnotherRow() {
        val g=gate(); sample(g,100,tip(x=200f,y=450f))
        assertEquals(1f,sample(g,110,tip(x=830f,y=450f)).single().value,0f)
        val hits=sample(g,120,tip(x=700f,y=200f))
        assertTrue(hits.single().up); assertEquals("slider",hits.single().id)
        assertTrue(sample(g,130,tip(x=700f,y=200f)).isEmpty())
    }
    @Test fun twoFingersCannotFightOverSameSlider() {
        val g=gate()
        val hits=sample(g,100,tip(1,200f,450f),tip(6,700f,450f))
        assertEquals(1,hits.count { it.down }); assertEquals(setOf(1),g.heldTips())
    }
    @Test fun sliderInsetsUseActualPixelDensity() {
        for((x,value) in listOf(136f to 0f,450f to .5f,764f to 1f)) {
            val g=gate()
            val h=g.hands(100,listOf(tip(x=x,y=450f)),listOf(slider.copy(insetPixels=36f))).single()
            assertEquals(value,h.value,0f)
        }
    }
    @Test fun removedOrMovedSurfaceReleasesWithoutActivatingReplacement() {
        val g=gate(); sample(g,100,tip())
        assertTrue(g.hands(110,listOf(tip()),listOf(pad.copy(id="new"))).single().up)
        assertTrue(g.hands(120,listOf(tip()),listOf(pad.copy(id="new"))).isEmpty())
    }
    @Test fun pauseAndTabResetRequireLiftBeforeAnotherStrike() {
        val g=gate(); sample(g,100,tip())
        assertTrue(g.reset().single().up); assertTrue(g.held().isEmpty())
        assertTrue(sample(g,110,tip()).isEmpty())
        sample(g,120,tip(z=.03f)); assertTrue(sample(g,130,tip()).single().down)
    }
    @Test fun pageResetKeepsRetractedSecondHandReadyForToolsWithoutAnotherLift() {
        val g=gate()
        sample(g,100,tip(1),tip(6,700f,z=.04f))
        g.reset()
        assertTrue("Held first hand must still retract",sample(g,110,tip(1)).none { it.down })
        assertEquals("other",sample(g,120,tip(6,700f)).single { it.down }.id)
    }
    @Test fun shallowLiftRearmsDifferentButtonWithoutFullRetraction() {
        val g=gate()
        assertTrue(sample(g,100,tip()).single().down)
        assertTrue(sample(g,116,tip(x=700f,z=.018f)).single().up)
        assertEquals("other",sample(g,132,tip(x=700f)).single { it.down }.id)
        assertTrue(sample(g,148,tip(x=700f)).none { it.down })
    }
    @Test fun lateralSlideAtContactDepthDoesNotPlayEveryCrossedPad() {
        val g=gate(); sample(g,100,tip())
        assertTrue(sample(g,116,tip(x=700f)).single().up)
        assertTrue(sample(g,132,tip(x=700f,z=.014f)).none { it.down })
        assertTrue(sample(g,148,tip(x=700f)).none { it.down })
        assertTrue(sample(g,164,tip(x=700f,z=.018f)).none { it.down })
        assertEquals("other",sample(g,180,tip(x=700f)).single { it.down }.id)
    }
    @Test fun shallowLiftAfterPageResetCanReachAnotherNavigationButton() {
        val g=gate(); sample(g,100,tip()); g.reset()
        assertTrue(sample(g,116,tip()).none { it.down })
        assertTrue(sample(g,132,tip(x=700f,z=.018f)).none { it.down })
        assertEquals("other",sample(g,148,tip(x=700f)).single { it.down }.id)
    }
    @Test fun sameButtonStillNeedsFullLiftAcrossPageReset() {
        val g=gate(); sample(g,100,tip()); g.reset()
        sample(g,116,tip(z=.018f))
        assertTrue(sample(g,132,tip()).none { it.down })
        sample(g,148,tip(z=.021f))
        assertTrue(sample(g,164,tip()).single().down)
    }
    @Test fun trackingLossCancelsPermissionToRearmAnotherButtonShallowly() {
        for(expired in listOf(false,true)) {
            val g=gate(); sample(g,100,tip()); g.reset()
            if(expired) g.expire(201) else sample(g,116)
            assertTrue(sample(g,if(expired) 210 else 132,tip(x=700f,z=.018f)).none { it.down })
            assertTrue(sample(g,if(expired) 226 else 148,tip(x=700f)).none { it.down })
        }
    }
    @Test fun undersideExitCannotUseShallowLateralRearm() {
        val g=gate(); sample(g,100,tip())
        assertTrue(sample(g,116,tip(z=-.04f)).single().up)
        sample(g,132,tip(x=700f,z=.018f))
        assertTrue(sample(g,148,tip(x=700f)).none { it.down })
        sample(g,164,tip(x=700f,z=.021f))
        assertEquals("other",sample(g,180,tip(x=700f)).single { it.down }.id)
    }
    @Test fun offPanelFingerAndPalmDoNotCreateAControlHit() {
        val g=gate(); assertTrue(sample(g,100,tip(x=-100f),tip(1,900f,900f)).isEmpty())
        // Scene only forwards INDEX_TIP; the gate also rejects every other finger ID.
        assertTrue(sample(g,110).isEmpty())
    }
}
