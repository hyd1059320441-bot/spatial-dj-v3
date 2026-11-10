package com.example.spatialdj.p170bd28c

import com.example.spatialdj.p170bd28c.domain.audio.*
import com.example.spatialdj.p170bd28c.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class LoopEngineTest {
    private fun engine(bpm: Int = 120,rate: Int = 1000) = LoopEngine(SoundBank(rate)).apply {
        load(LoopSession(bpm=bpm,bars=1))
    }
    private fun advance(e: LoopEngine,frames: Int) {
        val full = ShortArray(2048)
        repeat(frames/1024) { e.render(full) }
        if(frames%1024 != 0) e.render(ShortArray(frames%1024*2))
    }
    private fun startRecording(e: LoopEngine,lane: Int = 0) {
        e.record(lane)
        advance(e,ceil(4*e.bank.sampleRate*60.0/e.snapshot().bpm).toInt()+1)
        assertEquals(lane,e.snapshot().recording)
    }
    private fun patterned(bpm: Int =120) = LoopSession(bpm=bpm,bars=1,lanes = List(5) { lane ->
        LaneSession(if(lane==0) List(16) { NoteEvent(it/4.0,0) } else emptyList())
    })
    @Test fun fiveTracksRecordOneLoopThenReplayInSync() {
        val e = engine()
        startRecording(e); e.pad(0,0); advance(e,2000)
        for(lane in 1..4) {
            e.record(lane); advance(e,2000)
            assertEquals(lane,e.snapshot().recording)
            e.pad(lane,0); advance(e,2000)
            assertNull(e.snapshot().recording)
        }
        assertEquals(listOf(1,1,1,1,1),e.snapshot().lanes.map { it.count })
        e.stop(); val before = e.snapshot().triggers; e.play(); advance(e,1)
        assertEquals(5,e.snapshot().triggers-before)
        advance(e,8000)
        assertEquals(25,e.snapshot().triggers-before)
    }
    @Test fun liveAuditionDoesNotPlayRecordedEventTwiceInFirstPass() {
        val e = engine(); startRecording(e)
        val before=e.snapshot().triggers; e.pad(0,0); advance(e,1998)
        assertEquals(1,e.snapshot().triggers-before)
        advance(e,2); assertEquals(2,e.snapshot().triggers-before)
    }
    @Test fun nearestSixteenthWrapsToFirstBeat() {
        val e = engine(); startRecording(e); advance(e,1970); e.pad(0,0); advance(e,30)
        assertEquals(0.0,e.exportSession().lanes[0].events.single().beat,0.0)
    }
    @Test fun freeTimingPreservesOffGridBeat() {
        val e = engine(); e.quantize(); startRecording(e); advance(e,172); e.pad(0,0); advance(e,1828)
        assertEquals(.346,e.exportSession().lanes[0].events.single().beat,1e-9)
    }
    @Test fun oneHourNonIntegralTempoHasNoAccumulatingRoundingDrift() {
        val e = engine(137,100); e.load(patterned(137)); e.play()
        advance(e,360001)
        assertEquals(32881,e.snapshot().triggers)
        assertEquals(2055,e.snapshot().cycle)
        assertTrue(e.snapshot().beat < .03)
    }
    @Test fun chunkSizeDoesNotChangeSequencerOrPcm() {
        val a=engine(); val b=engine()
        a.load(patterned()); b.load(patterned()); a.play(); b.play()
        val large=ShortArray(12400); a.render(large)
        val small=ShortArray(2)
        for(i in 0 until 6200) { b.render(small); assertEquals(large[i*2],small[0]); assertEquals(large[i*2+1],small[1]) }
        assertEquals(a.snapshot().triggers,b.snapshot().triggers)
    }
    @Test fun nonIntegralCountInIsExactlyFourBeats() {
        val e=engine(137); e.record(0); assertEquals(4,e.snapshot().countIn)
        advance(e,1); assertEquals(4,e.snapshot().countIn)
        advance(e,ceil(4*1000*60.0/137).toInt()-1)
        assertEquals(0,e.snapshot().countIn); assertNull(e.snapshot().recording)
        advance(e,1); assertEquals(0,e.snapshot().recording)
    }
    @Test fun overdubKeepsPriorNotesAndUndoRestoresPriorLoop() {
        val e=engine(); e.load(LoopSession(bpm=120,bars=1,lanes=List(5) { LaneSession(if(it==0) listOf(NoteEvent(0.0,0)) else emptyList()) }))
        startRecording(e); advance(e,500); e.pad(0,1); advance(e,1500)
        assertEquals(2,e.snapshot().lanes[0].count)
        e.volume(0,.2f); e.undo(0); assertEquals(1,e.snapshot().lanes[0].count)
        assertEquals(.2f,e.snapshot().lanes[0].volume,0f)
        e.undo(0); assertEquals(2,e.snapshot().lanes[0].count)
    }
    @Test fun cancelAndStopDiscardUncommittedOverdub() {
        val e=engine(); e.load(patterned()); startRecording(e); e.pad(0,0); e.record(1)
        assertNull(e.snapshot().recording); assertNull(e.snapshot().armed); assertEquals(16,e.snapshot().lanes[0].count)
        e.record(0); advance(e,2000); e.pad(0,1); e.stop()
        assertEquals(16,e.snapshot().lanes[0].count)
    }
    @Test fun tempoAndLengthCannotChangeDuringRecordingOrExistingLoop() {
        val e=engine(); startRecording(e)
        assertFalse(e.tempo(180)); assertFalse(e.bars(4)); assertEquals(120,e.snapshot().bpm)
        e.pad(0,0); advance(e,2000); e.stop()
        assertTrue(e.tempo(137)); assertFalse(e.bars(4))
        e.newSession(); assertTrue(e.bars(4))
    }
    @Test fun mutedTrackRetainsLoopAndMasterZeroProducesSilence() {
        val e=engine(); e.load(patterned()); e.mute(0); e.play()
        val pcm=ShortArray(4000); e.render(pcm); assertTrue(pcm.all { it==0.toShort() })
        assertEquals(16,e.snapshot().lanes[0].count); e.mute(0); e.master(0f); e.render(pcm)
        assertTrue(pcm.all { it==0.toShort() }); e.master(1f); e.render(pcm)
        assertTrue(pcm.any { it!=0.toShort() })
    }
    @Test fun clearAndUndoWorkWithoutReplayingPastEvents() {
        val e=engine(); e.load(patterned()); e.play(); advance(e,800)
        e.clear(0); assertEquals(0,e.snapshot().lanes[0].count)
        val before=e.snapshot().triggers; e.undo(0); advance(e,1)
        assertEquals(before,e.snapshot().triggers); advance(e,1200)
        assertEquals(10,e.snapshot().triggers-before)
    }
    @Test fun eventCapAndVoiceCapSurviveDenseInput() {
        val e=engine(); startRecording(e)
        repeat(256) { assertTrue(e.pad(0,0)) }; assertFalse(e.pad(0,0))
        advance(e,2000); assertEquals(256,e.snapshot().lanes[0].count)
        assertTrue(e.snapshot().peak.isFinite()); e.stop(); advance(e,20); assertEquals(0f,e.snapshot().peak,0f)
    }
    @Test fun invalidAndUnsortedSessionsAreHandledBeforeMutation() {
        val e=engine(); e.load(patterned())
        val invalid=listOf(LoopSession(bpm=0),LoopSession(bars=3),LoopSession(version=7),
            LoopSession(master=Float.NaN),LoopSession(lanes=emptyList()),
            LoopSession(lanes=List(5) { LaneSession(listOf(NoteEvent(Double.NaN,0))) }),
            LoopSession(lanes=List(5) { LaneSession(listOf(NoteEvent(0.0,100))) }))
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { e.load(it) } }
        assertEquals(16,e.snapshot().lanes[0].count)
        val ordered=LoopSession(lanes=List(5) { LaneSession(listOf(NoteEvent(1.0,0),NoteEvent(0.0,0))) }).validate()
        assertEquals(0.0,ordered.lanes[0].events.first().beat,0.0)
    }
}
