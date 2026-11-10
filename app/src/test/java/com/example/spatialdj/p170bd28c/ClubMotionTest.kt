package com.example.spatialdj.p170bd28c

import com.example.spatialdj.p170bd28c.ui.loop.*
import com.example.spatialdj.p170bd28c.domain.audio.*
import com.example.spatialdj.p170bd28c.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ClubMotionTest {
    private fun same(a: ClubMotion.V,b: ClubMotion.V) {
        assertEquals(a.x,b.x,1e-7); assertEquals(a.y,b.y,1e-7); assertEquals(a.z,b.z,1e-7)
    }
    @Test fun everyDancePoseKeepsBoneLengthsFeetAboveFloorAndTargetsReachable() {
        for(e in listOf(0.0,.25,.68,1.0)) for(i in 0..3) for(t in 0..1600) {
            val p=ClubMotion.pose(t/100.0-4,e,i)
            for(leg in listOf(p.left,p.right)) {
                assertEquals(ClubMotion.THIGH,(leg.knee-leg.hip).length(),1e-7)
                assertEquals(ClubMotion.SHIN,(leg.ankle-leg.knee).length(),1e-7)
                assertTrue("Shoe penetrated dance floor",leg.ankle.y>=ClubMotion.ANKLE-1e-7)
                val target=ClubMotion.foot(t/100.0-4,if(leg==p.left) -1 else 1,e,i)
                same(target,leg.ankle)
            }
        }
    }
    @Test fun supportFootStaysPlantedWhileHipsAndKneeMove() {
        val a=ClubMotion.pose(.1,1.0,0); val b=ClubMotion.pose(.4,1.0,0)
        same(a.left.ankle,b.left.ankle)
        assertTrue((a.hips-b.hips).length()>.01)
        assertTrue((a.left.knee-b.left.knee).length()>.01)
        for(side in listOf(-1,1)) {
            same(ClubMotion.foot(.1,side,1.0),ClubMotion.foot(.4,side,1.0))
            assertTrue(ClubMotion.foot(if(side==-1) .75 else 1.75,side,1.0).y>.105)
        }
    }
    @Test fun choreographyIsContinuousPeriodicAndCharactersHaveDifferentArticulation() {
        for(i in 0..3) {
            for(t in 0..100) {
                val a=ClubMotion.pose(t/10.0,1.0,i); val b=ClubMotion.pose(t/10.0+8,1.0,i)
                same(a.hips,b.hips); same(a.left.ankle,b.left.ankle); same(a.right.ankle,b.right.ankle)
                assertEquals(a.leftArm.shoulderZ,b.leftArm.shoulderZ,.0001f)
            }
        }
        for(i in 0..3) for(side in listOf(-1,1)) for(boundary in -4..8) {
            val a=ClubMotion.foot(boundary-1e-7,side,1.0,i)
            val b=ClubMotion.foot(boundary+1e-7,side,1.0,i)
            assertTrue((a-b).length()<1e-6)
        }
        val arms=(0..3).map { ClubMotion.pose(.6,1.0,it).leftArm }
        assertEquals(4,arms.toSet().size)
        for(i in 0..3) for(t in 0..800) {
            val p=ClubMotion.pose(t/100.0,1.0,i)
            for(arm in listOf(p.leftArm,p.rightArm)) {
                assertTrue("Hand raised above shoulder",abs(arm.shoulderZ)<30)
                assertTrue("Arm swing exceeds a restrained groove",abs(arm.shoulderX)<=31)
            }
        }
    }
    @Test fun allFourDancesLandAndDipOnSameMusicBeatWithoutCharacterPhaseOffsets() {
        for(beat in -4..16) for(i in 0..3) {
            val hit=ClubMotion.pose(beat.toDouble(),1.0,i)
            val off=ClubMotion.pose(beat+.5,1.0,i)
            assertTrue("Dancer$i dips off the kick",hit.hips.y<off.hips.y-.01)
            assertEquals(ClubMotion.ANKLE,hit.left.ankle.y,1e-8)
            assertEquals(ClubMotion.ANKLE,hit.right.ankle.y,1e-8)
        }
    }
    @Test fun fourStylesDifferInFootworkTorsoAndArmsWithLowFootLift() {
        assertEquals(4,(0..3).map(ClubMotion::style).toSet().size)
        val feet=(0..3).map { i -> (0..32).map { ClubMotion.pose(it/4.0,1.0,i).left.ankle } }
        assertEquals("Footwork patterns are just time offsets",4,feet.toSet().size)
        for(i in 0..3) for(t in 0..800) {
            val p=ClubMotion.pose(t/100.0,1.0,i)
            assertTrue(p.left.ankle.y<=.133 && p.right.ankle.y<=.133)
            assertTrue(abs(p.torsoZ)<=5.01 && abs(p.torsoY)<=8.01)
        }
    }
    @Test fun ikHandlesVerticalSingularAndOutOfReachTargetsWithoutInvalidCoordinates() {
        val hip=ClubMotion.V(0.0,.8,0.0)
        for(target in listOf(hip,ClubMotion.V(0.0,.8,2.0),ClubMotion.V(0.0,.8,-2.0),
            ClubMotion.V(0.0,5.0,0.0),ClubMotion.V(0.0,-5.0,0.0))) {
            val leg=ClubMotion.leg(hip,target)
            assertEquals(.36,(leg.knee-leg.hip).length(),1e-7)
            assertEquals(.36,(leg.ankle-leg.knee).length(),1e-7)
            assertTrue(leg.knee.x.isFinite() && leg.knee.y.isFinite() && leg.knee.z.isFinite())
        }
    }
    @Test fun beatExtrapolationIsBoundedCountInSafeAndStopsImmediately() {
        val c=ClubBeatClock()
        c.sample(-4.0,120,true,1_000_000_000)
        assertEquals(-3.95,c.beat(1_025_000_000),1e-8)
        assertEquals(-3.9,c.beat(5_000_000_000),1e-8)
        c.sample(8.0,180,true,2_000_000_000)
        assertEquals(8.06,c.beat(2_020_000_000),1e-8)
        c.sample(8.1,180,false,2_100_000_000)
        assertEquals(8.1,c.beat(5_000_000_000),1e-8)
    }
    @Test fun absoluteAudioBeatDoesNotResetAtDifferentLengthPartBoundaries() {
        val e=LoopEngine(SoundBank(1000))
        var song=LoopSession(bpm=137,bars=1).withMode(RecordingMode.FullSong)
        SongPart.entries.forEachIndexed { i,part ->
            song=song.withSection(SongSection(part,listOf(1,2,4,1,2)[i]))
        }
        e.load(song); e.playArrangement()
        val buffer=ShortArray(146)
        var previous=0.0
        val seen=mutableSetOf<SongPart>()
        repeat(220) {
            e.render(buffer)
            val s=e.snapshot(); seen.add(s.part)
            assertTrue(s.elapsedBeats>=previous)
            if(s.running) assertEquals((it+1)*73*137/60_000.0,s.elapsedBeats,1e-8)
            previous=s.elapsedBeats
        }
        assertEquals(SongPart.entries.toSet(),seen)
        e.stop(); e.record(0)
        assertEquals(-4.0018,e.snapshot().elapsedBeats,.002)
    }
    @Test fun palettePreservesRgbBetweenArgbAndSpatialRgba() {
        assertEquals("0x76d3c8ff",ClubPalette.rgba(ClubPalette.Teal))
        assertEquals("0x24313dff",ClubPalette.rgba(ClubPalette.Ink))
    }
}
