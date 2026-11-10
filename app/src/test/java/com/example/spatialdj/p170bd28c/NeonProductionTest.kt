package com.example.spatialdj.p170bd28c

import com.example.spatialdj.p170bd28c.data.audio.AutoScoreFiles
import com.example.spatialdj.p170bd28c.domain.audio.*
import com.example.spatialdj.p170bd28c.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.DataOutputStream
import java.io.BufferedOutputStream
import kotlin.math.*

class NeonProductionTest {
    private val root=File("..").canonicalFile
    private fun score()=AutoScoreFiles.read(File(root,"music/neon_bounce/score.json").readText())
    private fun bank()=SoundBank(loader={ lane,pad ->
        SoundBank.readStereoWav(File(root,"app/src/main/assets/audio/neon/$lane-$pad.wav").inputStream())
    })
    @Test fun sectionsChangeWithoutReplacingRecordedProjectAndRenderExactProductionMaster() {
        val score=score(); val e=LoopEngine(bank()); e.startAutomatic(score)
        assertEquals(score,AutoScoreFiles.read(File(root,"app/src/main/assets/music/neon-bounce.json").readText()))
        assertEquals(setOf(SongPart.Drop,SongPart.Breakdown,SongPart.BuildUp,SongPart.Outro),
            score.sections.map { it.part }.toSet())
        val pcm=ShortArray(384)
        // Include the first frame of the next cycle so the final pass is committed.
        val recordFrames=ceil((4+score.steps.size*16)*48000*60.0/score.bpm).toInt()+1
        var remaining=recordFrames
        while(remaining>0) {
            val count=minOf(192,remaining)
            e.render(if(count==192) pcm else ShortArray(count*2)); remaining-=count
        }
        assertEquals(score.expectedSession(),e.exportSession())
        val outFile=File(root,"music/neon_bounce/neon-bounce.wav")
        val sectionFrames=(16*48000*60.0/score.bpm).toInt()
        val frames=sectionFrames*score.sections.sumOf { it.loops }
        var peak=0; var stereoDifference=0L
        val observed=mutableSetOf<String>()
        DataOutputStream(BufferedOutputStream(outFile.outputStream())).use { out ->
            fun le16(v: Int) { out.writeByte(v); out.writeByte(v ushr 8) }
            fun le32(v: Int) { le16(v); le16(v ushr 16) }
            out.writeBytes("RIFF"); le32(frames*4+36); out.writeBytes("WAVEfmt ")
            le32(16); le16(1); le16(2); le32(48000); le32(192000); le16(4); le16(16)
            out.writeBytes("data"); le32(frames*4)
            remaining=frames
            while(remaining>0) {
                val count=minOf(192,remaining)
                val buffer=if(count==192) pcm else ShortArray(count*2)
                e.render(buffer)
                if(remaining%sectionFrames<192) {
                    val status=e.snapshot()
                    val clip=score.sections.single { it.title==status.auto!!.section }
                    observed.add(clip.title)
                    assertEquals(RecordingMode.FullSong,status.mode)
                    assertEquals(clip.part,status.part)
                    assertEquals(clip.lanes.map { it.size },status.lanes.map { it.count })
                    assertEquals(score.expectedSession(),e.exportSession())
                }
                for(i in buffer.indices) { le16(buffer[i].toInt()); peak=max(peak,abs(buffer[i].toInt())) }
                for(i in 0 until count) stereoDifference+=abs(buffer[i*2].toInt()-buffer[i*2+1])
                remaining-=count
            }
        }
        assertEquals(score.sections.map { it.title }.toSet(),observed)
        assertTrue(e.snapshot().auto!!.active)
        assertEquals(score.steps.sumOf { it.notes.size },e.snapshot().auto!!.notesPlayed)
        assertTrue("Stereo bank/mix collapsed to mono",stereoDifference>frames*100L)
        assertTrue("Master clips",peak<32700)
        assertTrue("Master too quiet",peak>20000)
        println("NeonProductionPASS recordedNotes=${score.steps.sumOf { it.notes.size }} sections=${observed.size} " +
            "frames=$frames peak=$peak master=${outFile.absolutePath}")
    }
    @Test fun stereoDecoderRejectsTruncatedAndMonoAssets() {
        val bytes=File(root,"app/src/main/assets/audio/neon/0-0.wav").readBytes()
        assertThrows(IllegalArgumentException::class.java) { SoundBank.readStereoWav(bytes.copyOf(45).inputStream()) }
        val mono=bytes.copyOf().apply { this[22]=1 }
        assertThrows(IllegalArgumentException::class.java) { SoundBank.readStereoWav(mono.inputStream()) }
    }
    @Test fun sidechainRecoversAndResetClearsEveryAmbienceTail() {
        val mix=BounceMix(1000); mix.reset(128); mix.kick()
        assertEquals(.19f,mix.duck(),.0001f)
        repeat(240) { mix.process(.8f,-.8f,.8f,-.8f,1f) }
        assertEquals(1f,mix.duck(),0f)
        assertTrue(mix.left<.97f && mix.right>-.97f)
        mix.reset(128)
        repeat(1500) { mix.process(0f,0f,0f,0f,1f); assertEquals(0f,mix.left,0f); assertEquals(0f,mix.right,0f) }
    }
}
