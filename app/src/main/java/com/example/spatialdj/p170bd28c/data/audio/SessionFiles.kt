package com.example.spatialdj.p170bd28c.data.audio

import com.example.spatialdj.p170bd28c.domain.audio.LoopEngine
import com.example.spatialdj.p170bd28c.domain.model.*
import java.io.*
import kotlin.math.ceil

object SessionFiles {
    private fun DataOutputStream.lanes(value: List<LaneSession>) {
        for (lane in value) {
            writeFloat(lane.volume); writeBoolean(lane.muted); writeInt(lane.events.size)
            for (event in lane.events) { writeDouble(event.beat); writeInt(event.pad); writeFloat(event.velocity) }
        }
    }
    private fun DataInputStream.lanes() = List(5) {
        val volume = readFloat(); val muted = readBoolean(); val count = readInt()
        require(count in 0..256) { "音符数量无效" }
        LaneSession(List(count) { NoteEvent(readDouble(), readInt(), readFloat()) }, volume, muted)
    }
    fun write(file: File, session: LoopSession) {
        val checked = session.validate()
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { out ->
            out.writeInt(0x534C5633); out.writeInt(checked.version)
            out.writeInt(checked.bpm); out.writeInt(checked.bars); out.writeBoolean(checked.quantize); out.writeFloat(checked.master)
            out.lanes(checked.lanes)
            if (checked.version == 2) {
                out.writeInt(checked.mode.ordinal); out.writeInt(checked.selectedPart.ordinal)
                out.writeInt(checked.sections.size)
                for (part in checked.sections) {
                    out.writeInt(part.part.ordinal); out.writeInt(part.bars); out.lanes(part.lanes)
                }
            }
        }
        check(temporary.renameTo(file)) { "工程保存失败" }
    }
    fun read(file: File): LoopSession {
        require(file.length() in 1..131072) { "工程文件大小无效" }
        return DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            require(input.readInt() == 0x534C5633) { "不是 Spatial Loop 工程" }
            val version = input.readInt(); val bpm = input.readInt(); val bars = input.readInt()
            require(version in 1..2 && (version != 1 || file.length() <= 32768)) { "工程版本或大小无效" }
            val quantize = input.readBoolean(); val master = input.readFloat()
            var session = LoopSession(version, bpm, bars, quantize, master, input.lanes())
            if (version == 2) {
                val mode = requireNotNull(RecordingMode.entries.getOrNull(input.readInt())) { "录制模式无效" }
                val selected = requireNotNull(SongPart.entries.getOrNull(input.readInt())) { "当前部分无效" }
                require(input.readInt() == 4) { "编曲部分数量无效" }
                val sections = List(4) {
                    val part = requireNotNull(SongPart.entries.getOrNull(input.readInt())) { "编曲部分无效" }
                    SongSection(part, input.readInt(), input.lanes())
                }
                session = session.copy(mode = mode, selectedPart = selected, sections = sections)
            }
            require(input.read() == -1) { "工程文件含多余数据" }
            session.validate()
        }
    }
    /** Drop: four loops. Full song: each independently recorded part once, in song order. */
    fun wav(file: File, engine: LoopEngine, session: LoopSession) {
        val checked = session.validate()
        val offline = LoopEngine(engine.bank)
        offline.load(checked)
        if (checked.mode == RecordingMode.FullSong) offline.playArrangement() else offline.play()
        val frames = ceil(checked.exportBeats() * 60.0 / checked.bpm * engine.bank.sampleRate).toInt()
        file.parentFile?.mkdirs()
        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
            fun le16(v: Int) { out.writeByte(v); out.writeByte(v ushr 8) }
            fun le32(v: Int) { le16(v); le16(v ushr 16) }
            out.writeBytes("RIFF"); le32(36 + frames * 4); out.writeBytes("WAVEfmt ")
            le32(16); le16(1); le16(2); le32(engine.bank.sampleRate); le32(engine.bank.sampleRate * 4)
            le16(4); le16(16); out.writeBytes("data"); le32(frames * 4)
            val pcm = ShortArray(384)
            var remaining = frames
            while (remaining > 0) {
                offline.render(pcm)
                val n = minOf(192, remaining)
                for (i in 0 until n * 2) le16(pcm[i].toInt())
                remaining -= n
            }
        }
    }
}
