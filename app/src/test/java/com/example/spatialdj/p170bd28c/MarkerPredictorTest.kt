package com.example.spatialdj.p170bd28c

import com.example.spatialdj.p170bd28c.ui.loop.MarkerPredictor
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class MarkerPredictorTest {
    private fun point(x: Float,y: Float=0f,z: Float=0f)=MarkerPredictor.Point(x,y,z)
    @Test fun steadyMotionCoversShortDisplayLagWithoutChangingMeasuredContact() {
        val p=MarkerPredictor()
        p.sample(point(0f,z=.03f),1_000_000_000)
        val raw=point(.01f,z=.02f)
        p.sample(raw,1_010_000_000)
        val display=p.position(1_012_000_000)!!
        assertTrue(display.x>raw.x)
        assertTrue(display.z<raw.z)
        assertEquals(.02f,raw.z,0f)
        val distance=sqrt((display.x-raw.x)*(display.x-raw.x)+(display.z-raw.z)*(display.z-raw.z))
        assertTrue(distance<=.012001f)
    }
    @Test fun firstSampleTeleportsAndReversalsNeverOvershoot() {
        val p=MarkerPredictor()
        p.sample(point(0f),1_000_000_000)
        assertEquals(point(0f),p.position(1_002_000_000))
        p.sample(point(.01f),1_010_000_000)
        p.sample(point(.005f),1_020_000_000)
        assertEquals(point(.005f),p.position(1_023_000_000))
        p.sample(point(.5f),1_030_000_000)
        assertEquals(point(.5f),p.position(1_032_000_000))
    }
    @Test fun staleLossInvalidAndOutOfOrderDataCannotKeepMovingMarker() {
        val p=MarkerPredictor()
        p.sample(point(0f),1_000_000_000); p.sample(point(.01f),1_010_000_000)
        assertEquals(point(.01f),p.position(1_040_000_000)) // Prediction stops after 25 ms.
        assertNull(p.position(1_061_000_000))
        p.sample(point(.2f),1_070_000_000)
        assertEquals(point(.2f),p.position(1_075_000_000)) // No velocity across tracking gap.
        p.sample(point(.9f),1_060_000_000)
        assertEquals(point(.2f),p.position(1_075_000_000))
        p.sample(point(Float.NaN),1_080_000_000)
        assertNull(p.position(1_081_000_000))
        p.sample(point(0f),1_090_000_000); p.reset()
        assertNull(p.position(1_091_000_000))
    }
    @Test fun latestConsecutiveProviderPairRecoversAfterSkippedSceneFrames() {
        val p=MarkerPredictor()
        p.sample(point(0f,z=.035f),1_000_000_000)
        // The scene stalled; feed the two most recent provider callbacks when it resumes.
        p.sample(point(0f,z=.035f),1_088_000_000)
        p.sample(point(0f,z=.019f),1_100_000_000)
        val display=p.position(1_102_000_000)!!
        assertTrue("Fresh callback velocity was discarded with skipped scene frames",display.z<.017f)
        assertTrue(display.z>=.006999f)
    }
}
