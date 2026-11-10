package com.example.spatialdj.p170bd28c

import com.example.spatialdj.p170bd28c.domain.audio.*
import com.example.spatialdj.p170bd28c.domain.model.*
import com.example.spatialdj.p170bd28c.data.audio.AutoScoreFiles
import org.junit.Assert.*
import org.junit.Test

class AutomaticPerformanceTest {
    private fun score()=AutoScore("测试舞曲",137,1,.78f,List(5) { .7f },listOf(
        AutoStep(0,listOf(NoteEvent(0.0,0),NoteEvent(1.0,0))),
        AutoStep(0,listOf(NoteEvent(.5,1),NoteEvent(3.75,2))),
        AutoStep(1,listOf(NoteEvent(1.0,0),NoteEvent(3.0,3))),
        AutoStep(2,listOf(NoteEvent(.5,1),NoteEvent(2.5,3))),
        AutoStep(3,listOf(NoteEvent(0.0,1),NoteEvent(1.5,4))),
        AutoStep(4,listOf(NoteEvent(0.0,2),NoteEvent(2.0,3))),
    ))
    private fun engine()=LoopEngine(SoundBank(1000))
    private fun advance(engine: LoopEngine,frames: Int) {
        val buffer=ShortArray(384)
        repeat(frames/192) { engine.render(buffer) }
        if(frames%192!=0) engine.render(ShortArray(frames%192*2))
    }

    @Test fun sixStepsActuallyRecordAndOverdubBeforeContinuousPlayback() {
        val e=engine(); val score=score()
        e.startAutomatic(score)
        assertEquals(RecordingMode.FullSong,e.snapshot().mode)
        assertEquals(SongPart.Drop,e.snapshot().part)
        assertEquals(SongPart.entries,e.snapshot().sections.map { it.part })
        assertEquals(4,e.snapshot().countIn)
        assertEquals(List(5) { 0 },e.snapshot().lanes.map { it.count })
        advance(e,1800)
        assertEquals(0,e.snapshot().recording)
        assertEquals(1,e.snapshot().auto!!.notesPlayed)
        assertTrue(e.exportSession().lanes.all { it.events.isEmpty() }) // Not a preloaded loop.
        advance(e,1800)
        assertEquals(2,e.exportSession().lanes[0].events.size) // First loop committed.
        assertEquals(2,e.snapshot().auto!!.step) // Second pass overdubs the same lane.
        advance(e,10000)
        assertFalse(e.snapshot().auto!!.active)
        assertEquals(12,e.snapshot().auto!!.notesPlayed)
        assertEquals(score.expectedSession(),e.exportSession())
        assertTrue(e.snapshot().running)
        val triggers=e.snapshot().triggers
        advance(e,4000)
        assertTrue(e.snapshot().triggers>triggers+12)
    }

    @Test fun playingPartsAndCountsFollowClipsAcrossRepeatedLoopsWithoutMutatingRecordedProject() {
        val e=engine()
        val clips=listOf(SongPart.Drop,SongPart.Breakdown,SongPart.BuildUp,SongPart.Drop,SongPart.Outro).mapIndexed { i,part ->
            AutoSection("Section $i",if(i==1) 2 else 1,List(5) { 1f },
                List(5) { lane -> if(lane==i%5) listOf(NoteEvent(0.0,0),NoteEvent(3.75,1)) else emptyList() },part)
        }
        val score=score().copy(bpm=120,sections=clips)
        e.startAutomatic(score)
        advance(e,(4+score.steps.size*4)*500+1)
        repeat(2) {
            for(clip in clips) repeat(clip.loops) {
                val status=e.snapshot()
                assertEquals(RecordingMode.FullSong,status.mode)
                assertEquals(clip.part,status.part)
                assertEquals(clip.title,status.auto!!.section)
                assertTrue(status.arrangementPlaying)
                assertEquals(clip.lanes.map { it.size },status.lanes.map { it.count })
                assertEquals(clip.lanes.sumOf { it.size },status.sections.single { it.part==clip.part }.notes)
                assertTrue(clip.lanes[status.auto.lane].isNotEmpty())
                assertFalse(status.lanes.any { it.canUndo })
                assertEquals(score.expectedSession(),e.exportSession())
                advance(e,2000)
            }
        }
        e.stop()
        assertEquals(SongPart.Drop,e.snapshot().part)
        assertFalse(e.snapshot().arrangementPlaying)
        assertEquals(score.expectedSession().lanes.map { it.events.size },e.snapshot().lanes.map { it.count })
    }

    @Test fun clipPartMetadataDefaultsForOldScoresAndRejectsUnknownParts() {
        val json="""{"title":"test","bpm":120,"bars":1,"master":1,"volumes":[1,1,1,1,1],
            "steps":[{"lane":0,"notes":[{"at":0,"pad":0}]}],
            "sections":[{"title":"break","part":"Breakdown","loops":1,"gains":[1,1,1,1,1],
            "lanes":[[],[],[],[{"at":0,"pad":0}],[]]}]}"""
        assertEquals(SongPart.Breakdown,AutoScoreFiles.read(json).sections.single().part)
        assertEquals(SongPart.Drop,AutoScoreFiles.read(json.replace("\"part\":\"Breakdown\",","")).sections.single().part)
        assertThrows(IllegalArgumentException::class.java) { AutoScoreFiles.read(json.replace("Breakdown","Invalid")) }
    }

    @Test fun automationTimingAndPcmDoNotDependOnRenderBatchSize() {
        val score=score().copy(sections=listOf(
            AutoSection("Breakdown",1,List(5) { .6f },List(5) { listOf(NoteEvent(.75,1)) },SongPart.Breakdown),
            AutoSection("Outro",1,List(5) { .8f },List(5) { listOf(NoteEvent(3.75,0)) },SongPart.Outro)))
        val a=engine(); val b=engine(); a.startAutomatic(score); b.startAutomatic(score)
        val pcm=ShortArray(42000); a.render(pcm)
        val one=ShortArray(2)
        repeat(21000) {
            b.render(one)
            assertEquals("Left frame $it",pcm[it*2],one[0])
            assertEquals("Right frame $it",pcm[it*2+1],one[1])
        }
        assertEquals(a.exportSession(),b.exportSession())
        assertEquals(a.snapshot().triggers,b.snapshot().triggers)
    }

    @Test fun stopAndRestoreCancelEveryPendingAutomaticStrike() {
        val e=engine()
        val original=LoopSession(bpm=120,bars=1,lanes=List(5) {
            LaneSession(listOf(NoteEvent(1.0,0)),volume=.33f)
        })
        e.load(original); e.startAutomatic(score()); advance(e,2000)
        e.load(original)
        assertNull(e.snapshot().auto)
        val before=e.snapshot().triggers
        advance(e,15000)
        assertEquals(before,e.snapshot().triggers)
        assertEquals(original,e.exportSession())
        e.startAutomatic(score()); advance(e,2000); e.stop()
        assertNull(e.snapshot().recording)
        assertNull(e.snapshot().auto)
    }

    @Test fun malformedScoreCannotReplaceCurrentSession() {
        val e=engine()
        val original=e.exportSession()
        for(invalid in listOf(score().copy(bpm=0),score().copy(volumes=listOf(.7f)),
            score().copy(steps=listOf(AutoStep(9,listOf(NoteEvent(0.0,0))))),
            score().copy(steps=listOf(AutoStep(0,listOf(NoteEvent(.1,0))))),
            score().copy(steps=listOf(AutoStep(0,List(257) { NoteEvent(0.0,0) }))),
            score().copy(steps=listOf(AutoStep(0,listOf(NoteEvent(4.0,0))))))) {
            assertThrows(IllegalArgumentException::class.java) { e.startAutomatic(invalid) }
            assertEquals(original,e.exportSession())
        }
    }
}
