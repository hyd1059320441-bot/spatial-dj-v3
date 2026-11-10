package com.example.spatialdj.p170bd28c

import com.example.spatialdj.p170bd28c.ui.loop.*
import org.junit.Assert.*
import org.junit.Test

class ClubLightingTest {
    @Test fun sharedSpotsSweepBothRowsAndSidesWithoutLeavingDanceFloorOrLightReach() {
        assertEquals(2,ClubLighting.origins.size)
        for(e in listOf(0.0,.25,.68,1.0)) for(i in 0..1) for(t in 0..640) {
            val target=ClubLighting.target(i,t/40.0,e)
            assertTrue(target.x in -1.5..1.5)
            assertTrue(target.z in -4.4.. -2.4)
            assertEquals(.08,target.y,1e-8)
            assertTrue((target-ClubLighting.origins[i]).length()<ClubLighting.RADIUS)
        }
        for(i in 0..1) {
            val points=(0..640).map { ClubLighting.target(i,it/40.0,1.0) }
            assertTrue(points.maxOf { it.x }-points.minOf { it.x }>2.4)
            assertTrue(points.maxOf { it.z }-points.minOf { it.z }>1.3)
            assertTrue("Still fixed on a person",ClubLighting.positions.all { person ->
                points.any { (it-person).length()>.8 }
            })
        }
    }
    @Test fun opposingSweepsAreContinuousPeriodicAndRemainActiveAtIdle() {
        for(t in 0..640) {
            val beat=t/40.0
            val left=ClubLighting.target(0,beat,1.0)
            val right=ClubLighting.target(1,beat,1.0)
            assertEquals(0.0,left.x+right.x,1e-7)
            assertEquals(-6.8,left.z+right.z,1e-7)
            for(i in 0..1) {
                val repeat=ClubLighting.target(i,beat+16,1.0)
                val current=ClubLighting.target(i,beat,1.0)
                assertEquals(current.x,repeat.x,1e-7)
                assertEquals(current.z,repeat.z,1e-7)
                assertTrue((ClubLighting.target(i,beat+.001,1.0)-current).length()<.002)
            }
        }
        assertTrue((ClubLighting.target(0,0.0,0.0)-ClubLighting.target(0,4.0,0.0)).length()>.3)
    }
}
