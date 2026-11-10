package com.example.spatialdj.p170bd28c

import com.example.spatialdj.p170bd28c.data.audio.SessionFiles
import com.example.spatialdj.p170bd28c.domain.audio.*
import com.example.spatialdj.p170bd28c.domain.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataOutputStream
import java.io.RandomAccessFile
import kotlin.math.ceil

class ArrangementTest {
    @get:Rule val folder = TemporaryFolder()
    private fun advance(e: LoopEngine, frames: Int) {
        val pcm = ShortArray(384)
        repeat(frames / 192) { e.render(pcm) }
        if (frames % 192 != 0) e.render(ShortArray(frames % 192 * 2))
    }
    private fun song(bpm: Int = 137): LoopSession {
        var project = LoopSession(bpm = bpm, bars = 1).withMode(RecordingMode.FullSong)
        SongPart.entries.forEachIndexed { i, part ->
            project = project.withSection(SongSection(part, listOf(1, 2, 4, 1, 2)[i],
                List(5) { lane -> LaneSession(if (lane == 0) listOf(NoteEvent(0.0, i)) else emptyList()) }))
        }
        return project.validate()
    }
    @Test fun freshAndNewProjectsUseFullArrangementFourBarsWithoutChangingLegacyOrScoreDefaults() {
        val e=LoopEngine(SoundBank(1000))
        fun defaults() {
            val project=e.exportSession().validate()
            assertEquals(RecordingMode.FullSong,project.mode)
            assertEquals(SongPart.Intro,project.selectedPart)
            assertEquals(2,project.version)
            SongPart.entries.forEach {
                assertEquals(4,project.section(it).bars)
                assertTrue(project.section(it).lanes.all { lane -> lane.events.isEmpty() })
            }
        }
        defaults()
        e.load(LoopSession(bpm=133,bars=1,quantize=false))
        assertEquals(RecordingMode.DropOnly,e.snapshot().mode); assertEquals(1,e.snapshot().bars)
        e.newSession(); defaults()
        assertEquals(133,e.snapshot().bpm); assertFalse(e.snapshot().quantize)
        val score=AutoScore("测试",128,2,.7f,List(5) { .5f },listOf(AutoStep(0,listOf(NoteEvent(0.0,0)))))
        e.startAutomatic(score)
        assertEquals(RecordingMode.FullSong,e.snapshot().mode)
        assertEquals(SongPart.Drop,e.snapshot().part)
        assertEquals(2,e.snapshot().bars)
    }
    @Test fun modesKeepEveryCommittedPartAndLegacyDrop() {
        val e = LoopEngine(SoundBank(1000))
        val legacy = LoopSession(bpm = 120, bars = 1, lanes = List(5) {
            LaneSession(if (it == 2) listOf(NoteEvent(1.0, 3)) else emptyList())
        })
        e.load(legacy); e.mode(RecordingMode.FullSong)
        assertEquals(SongPart.Intro, e.snapshot().part)
        assertTrue(e.exportSession().section(SongPart.Intro).lanes.all { it.events.isEmpty() })
        assertEquals(legacy.lanes, e.exportSession().section(SongPart.Drop).lanes)
        e.record(0); advance(e,2001); e.pad(0,1); advance(e,2000)
        assertEquals(1,e.exportSession().section(SongPart.Intro).lanes[0].events.size)
        e.mode(RecordingMode.DropOnly)
        assertFalse(e.snapshot().running)
        assertEquals(SongPart.Drop,e.snapshot().part)
        assertEquals(legacy.lanes,e.exportSession().lanes)
        e.mode(RecordingMode.FullSong)
        assertEquals(1,e.snapshot().lanes[0].count)
    }
    @Test fun allFivePartsRecordIndependentlyWithLocalUndoAndCancelledOverdub() {
        val e = LoopEngine(SoundBank(1000))
        e.load(LoopSession(bpm=120,bars=1).withMode(RecordingMode.FullSong))
        for (part in SongPart.entries) {
            e.selectPart(part); e.record(0); advance(e,2001)
            e.pad(0,part.ordinal); advance(e,2000); e.stop()
        }
        SongPart.entries.forEach { part ->
            assertEquals(part.ordinal,e.exportSession().section(part).lanes[0].events.single().pad)
        }
        e.selectPart(SongPart.Breakdown); e.clear(0)
        e.selectPart(SongPart.BuildUp); e.clear(0); e.undo(0)
        assertEquals(1,e.snapshot().lanes[0].count)
        e.selectPart(SongPart.Breakdown)
        assertEquals(0,e.snapshot().lanes[0].count); e.undo(0)
        assertEquals(1,e.snapshot().lanes[0].count)
        e.record(0); advance(e,2001); e.pad(0,0); e.selectPart(SongPart.Outro)
        assertNull(e.snapshot().recording)
        assertEquals(1,e.exportSession().section(SongPart.Breakdown).lanes[0].events.size)
    }
    @Test fun nonIntegralTempoAndDifferentPartLengthsUseOneContinuousSampleClock() {
        val e = LoopEngine(SoundBank(1000)); val song = song()
        e.load(song.copy(selectedPart=SongPart.BuildUp)); e.playArrangement()
        var elapsed=0; var beats=0
        for (part in SongPart.entries) {
            val firstFrame=ceil(beats*60.0/137*1000).toInt()
            advance(e,firstFrame+1-elapsed); elapsed=firstFrame+1
            assertEquals(part,e.snapshot().part)
            assertEquals(part.ordinal+1L,e.snapshot().triggers)
            beats += song.section(part).bars*4
            val end=ceil(beats*60.0/137*1000).toInt()
            advance(e,end-elapsed); elapsed=end
            assertEquals(part,e.snapshot().part)
            assertTrue(e.snapshot().running)
        }
        advance(e,1)
        assertFalse(e.snapshot().running); assertFalse(e.snapshot().arrangementPlaying)
        advance(e,10000); assertEquals(5L,e.snapshot().triggers)
        assertEquals(song.sections,e.exportSession().sections)
        assertEquals(song.lanes,e.exportSession().lanes)
    }
    @Test fun sectionAuditionLoopsOnlySelectedPartAndRecordExitsFullPlayback() {
        val e=LoopEngine(SoundBank(1000)); e.load(song(120))
        e.selectPart(SongPart.Drop); e.play(); advance(e,6001)
        assertEquals(SongPart.Drop,e.snapshot().part); assertEquals(4L,e.snapshot().triggers)
        assertFalse(e.snapshot().arrangementPlaying)
        e.stop(); e.playArrangement(); advance(e,2500)
        assertEquals(SongPart.Breakdown,e.snapshot().part)
        e.record(1)
        assertFalse(e.snapshot().arrangementPlaying)
        assertEquals(4,e.snapshot().countIn)
        advance(e,2001); assertEquals(1,e.snapshot().recording)
        assertEquals(SongPart.Breakdown,e.snapshot().part)
    }
    @Test fun fullSongPcmIsIndependentOfBufferSizeAcrossTransitionsAndCompletion() {
        val a=LoopEngine(SoundBank(1000)); val b=LoopEngine(SoundBank(1000))
        a.load(song()); b.load(song()); a.playArrangement(); b.playArrangement()
        val pcm=ShortArray(40000); a.render(pcm)
        val one=ShortArray(2)
        repeat(20000) {
            b.render(one)
            assertEquals("Left $it",pcm[it*2],one[0])
            assertEquals("Right $it",pcm[it*2+1],one[1])
        }
        assertEquals(a.exportSession(),b.exportSession())
        assertEquals(a.snapshot().triggers,b.snapshot().triggers)
        assertEquals(a.snapshot().running,b.snapshot().running)
    }
    @Test fun independentLengthsAndModeSurviveSaveLoadIncludingHiddenParts() {
        val file=folder.newFile(); val full=song().copy(selectedPart=SongPart.Outro)
        SessionFiles.write(file,full); assertEquals(full,SessionFiles.read(file))
        val hidden=full.withMode(RecordingMode.DropOnly)
        SessionFiles.write(file,hidden); assertEquals(hidden,SessionFiles.read(file))
        assertEquals(full.sections,SessionFiles.read(file).withMode(RecordingMode.FullSong).sections)
    }
    @Test fun shorteningEmptyPartCannotUndoOutOfRangeNotesOrEraseOtherPartsUndo() {
        val e=LoopEngine(SoundBank(1000))
        val project=song().withSection(SongSection(SongPart.BuildUp,4,
            List(5) { LaneSession(if(it==0) listOf(NoteEvent(15.75,0)) else emptyList()) }))
        e.load(project)
        e.selectPart(SongPart.Drop); e.clear(0)
        e.selectPart(SongPart.BuildUp); e.clear(0)
        assertTrue(e.snapshot().lanes[0].canUndo)
        assertTrue(e.bars(1)); e.undo(0)
        assertEquals(0,e.snapshot().lanes[0].count)
        assertFalse(e.snapshot().lanes[0].canUndo)
        e.exportSession().validate()
        e.selectPart(SongPart.Drop); e.undo(0)
        assertEquals(1,e.snapshot().lanes[0].count)
        assertEquals(project.section(SongPart.Outro),e.exportSession().section(SongPart.Outro))
    }
    @Test fun manuallyWrittenV1FileLoadsAsDropAndUpgradesWithoutLosingSettings() {
        val file=folder.newFile()
        DataOutputStream(file.outputStream()).use { out ->
            out.writeInt(0x534C5633); out.writeInt(1); out.writeInt(123); out.writeInt(2)
            out.writeBoolean(false); out.writeFloat(.62f)
            repeat(5) { lane ->
                out.writeFloat(.31f); out.writeBoolean(lane==3); out.writeInt(1)
                out.writeDouble(7.75); out.writeInt(0); out.writeFloat(.83f)
            }
        }
        val old=SessionFiles.read(file)
        assertEquals(RecordingMode.DropOnly,old.mode); assertEquals(SongPart.Drop,old.selectedPart)
        val full=old.withMode(RecordingMode.FullSong).validate()
        assertEquals(old.lanes,full.lanes); assertEquals(old.master,full.master)
        assertFalse(full.quantize); assertEquals(123,full.bpm); assertEquals(2,full.bars)
        SessionFiles.write(file,full); assertEquals(full,SessionFiles.read(file))
    }
    @Test fun maximumArrangementExceedsLegacySizeAndMalformedExtensionsAreRejected() {
        var dense=song()
        SongPart.entries.forEach { part -> dense=dense.withSection(SongSection(part,4,List(5) { lane ->
            LaneSession(List(256) { NoteEvent((it%64)/4.0,it%Lane.entries[lane].pads.size) })
        })) }
        val file=folder.newFile(); SessionFiles.write(file,dense)
        assertTrue(file.length()>32768); assertEquals(dense.validate(),SessionFiles.read(file))
        val invalid=listOf(song().copy(sections=song().sections.drop(1)),
            song().copy(sections=List(4) { song().sections[0] }),
            song().withSection(SongSection(SongPart.Intro,1,List(5) { LaneSession(listOf(NoteEvent(4.0,0))) })))
        invalid.forEach { value -> assertThrows(IllegalArgumentException::class.java) { value.validate() } }
        val empty=song().copy(lanes=List(5) { LaneSession() })
        SessionFiles.write(file,empty)
        // Base header (21 bytes) + five empty lanes (9 bytes each), then mode ordinal.
        RandomAccessFile(file,"rw").use { it.seek(66); it.writeInt(99) }
        assertThrows(IllegalArgumentException::class.java) { SessionFiles.read(file) }
        SessionFiles.write(file,empty); file.appendBytes(byteArrayOf(1))
        assertThrows(IllegalArgumentException::class.java) { SessionFiles.read(file) }
    }
    @Test fun wavIncludesEveryPartOnceAtIndependentBoundariesEvenWithEmptyDrop() {
        var project=LoopSession(bpm=120,bars=1).withMode(RecordingMode.FullSong)
        for (part in listOf(SongPart.Intro,SongPart.BuildUp,SongPart.Outro))
            project=project.withSection(SongSection(part,1,List(5) {
                LaneSession(if(it==0) listOf(NoteEvent(0.0,1)) else emptyList())
            }))
        assertTrue(project.hasNotes())
        val e=LoopEngine(SoundBank(1000,loader={ _,_ -> FloatArray(40) { .25f } }))
        e.load(project); e.play(); advance(e,1)
        val original=e.exportSession(); val priorStatus=e.snapshot()
        val file=folder.newFile(); SessionFiles.wav(file,e,project)
        val bytes=file.readBytes()
        assertEquals(44+10000*4,bytes.size) // Five one-bar parts, once; no four-loop multiplier.
        fun audible(start: Int)=bytes.copyOfRange(44+start*4,44+(start+50)*4).any { it!=0.toByte() }
        assertTrue(audible(0)); assertFalse(audible(2000)); assertTrue(audible(4000))
        assertFalse(audible(6000)); assertTrue(audible(8000))
        assertEquals(original,e.exportSession()); assertEquals(priorStatus,e.snapshot())
    }
    @Test fun automaticRestoreKeepsTheWholeArrangement() {
        val e=LoopEngine(SoundBank(1000)); val original=song()
        e.load(original)
        val backup=e.exportSession()
        e.startAutomatic(AutoScore("测试",120,1,.7f,List(5) { .5f },
            listOf(AutoStep(0,listOf(NoteEvent(0.0,0))))))
        advance(e,2200); e.load(backup)
        assertEquals(original,e.exportSession())
        assertNull(e.snapshot().auto); assertFalse(e.snapshot().running)
    }
}
