package com.example.spatialdj.p170bd28c

import com.example.spatialdj.p170bd28c.data.audio.SessionFiles
import com.example.spatialdj.p170bd28c.domain.audio.*
import com.example.spatialdj.p170bd28c.domain.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.RandomAccessFile

class SessionFilesTest {
    @get:Rule val folder=TemporaryFolder()
    private fun session()=LoopSession(bpm=137,bars=1,lanes=List(5) {
        LaneSession(listOf(NoteEvent(.25,it%4)),.4f,it==2)
    })
    @Test fun exactSaveLoadRoundTripIncludesAllFiveTracks() {
        val file=folder.newFile(); SessionFiles.write(file,session()); assertEquals(session(),SessionFiles.read(file))
        SessionFiles.write(file,session().copy(bpm=120)); assertEquals(120,SessionFiles.read(file).bpm)
    }
    @Test fun corruptTruncatedOversizedAndTrailingFilesAreRejected() {
        val file=folder.newFile(); file.writeBytes(ByteArray(0))
        assertThrows(IllegalArgumentException::class.java) { SessionFiles.read(file) }
        SessionFiles.write(file,session()); file.appendBytes(byteArrayOf(1))
        assertThrows(IllegalArgumentException::class.java) { SessionFiles.read(file) }
        SessionFiles.write(file,session()); RandomAccessFile(file,"rw").use { it.setLength(12) }
        assertThrows(java.io.EOFException::class.java) { SessionFiles.read(file) }
        file.writeBytes(ByteArray(32769)); assertThrows(IllegalArgumentException::class.java) { SessionFiles.read(file) }
    }
    @Test fun wavHeaderLengthAndAudioMatchFourLoopExport() {
        val file=folder.newFile(); val e=LoopEngine(SoundBank(1000)); SessionFiles.wav(file,e,session())
        val bytes=file.readBytes()
        assertEquals("RIFF",bytes.copyOfRange(0,4).toString(Charsets.US_ASCII))
        assertEquals("WAVE",bytes.copyOfRange(8,12).toString(Charsets.US_ASCII))
        fun le(at: Int)=(0..3).sumOf { (bytes[at+it].toInt() and 255) shl (it*8) }
        assertEquals(44+ kotlin.math.ceil(16*60.0/137*1000).toInt()*4,bytes.size)
        assertEquals(bytes.size-44,le(40)); assertEquals(1000,le(24))
        assertTrue(bytes.drop(44).any { it!=0.toByte() })
    }
}
