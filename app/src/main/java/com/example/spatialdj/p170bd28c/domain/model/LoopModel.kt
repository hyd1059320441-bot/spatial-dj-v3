package com.example.spatialdj.p170bd28c.domain.model

enum class Lane(val title: String, val subtitle: String, val pads: List<String>) {
    Kick("底鼓", "打出节奏的骨架", listOf("底鼓", "闭镲", "开镲", "通鼓", "冲击", "上升")),
    Clap("拍手", "加上清脆的重拍", listOf("拍手", "军鼓", "轻拍", "重拍")),
    Bass("贝斯", "铺上弹跳的低音律动", listOf("A1", "C2", "D2", "E2", "F1", "G1")),
    Melody("旋律", "奏出你的主旋律", listOf("A3", "C4", "D4", "E4", "G4", "A4", "C5", "E5")),
    Chord("和弦", "叠加和声与氛围", listOf("Am", "F", "C", "G")),
}

data class NoteEvent(val beat: Double, val pad: Int, val velocity: Float = .8f)
data class LaneSession(val events: List<NoteEvent> = emptyList(), val volume: Float = .7f, val muted: Boolean = false)
enum class RecordingMode(val title: String) { DropOnly("Drop 循环"), FullSong("完整编曲") }
enum class SongPart(val title: String, val hint: String) {
    Intro("Intro", "开场：逐步引入节奏与氛围"),
    Breakdown("Breakdown", "间歇段：留出空间，突出旋律与和声"),
    BuildUp("Build-up", "推进段：加密节奏，为 Drop 积累张力"),
    Drop("Drop", "主段：完整的鼓组、贝斯与主旋律"),
    Outro("Outro", "收尾：逐步收回能量"),
}
data class SongSection(val part: SongPart, val bars: Int = 2, val lanes: List<LaneSession> = List(5) { LaneSession() })
data class LoopSession(
    val version: Int = 1, val bpm: Int = 110, val bars: Int = 2, val quantize: Boolean = true,
    val master: Float = .75f, val lanes: List<LaneSession> = List(5) { LaneSession() },
    val mode: RecordingMode = RecordingMode.DropOnly, val selectedPart: SongPart = SongPart.Drop,
    /** Drop stays in the original bars/lanes fields; the other four parts live here. */
    val sections: List<SongSection> = emptyList(),
) {
    fun section(part: SongPart = selectedPart): SongSection = if (part == SongPart.Drop)
        SongSection(part, bars, lanes) else sections.first { it.part == part }
    fun withSection(value: SongSection): LoopSession = if (value.part == SongPart.Drop)
        copy(bars = value.bars, lanes = value.lanes) else
        copy(sections = sections.map { if (it.part == value.part) value else it })
    fun withMode(value: RecordingMode): LoopSession = copy(version = 2, mode = value,
        selectedPart = if (value == RecordingMode.DropOnly) SongPart.Drop else
            if (mode == RecordingMode.DropOnly) SongPart.Intro else selectedPart,
        sections = sections.ifEmpty { SongPart.entries.filter { it != SongPart.Drop }.map {
            SongSection(it, bars, lanes.map { lane -> lane.copy(events = emptyList()) })
        } })
    fun hasNotes(): Boolean = lanes.any { it.events.isNotEmpty() } ||
        (mode == RecordingMode.FullSong && sections.any { part -> part.lanes.any { it.events.isNotEmpty() } })
    fun exportBeats(): Int = if (mode == RecordingMode.FullSong) SongPart.entries.sumOf { section(it).bars * 4 }
        else bars * 4 * 4
    fun validate(): LoopSession {
        require(version in 1..2) { "不支持的工程版本" }
        require(bpm in 60..180) { "速度无效" }
        require(master.isFinite() && master in 0f..1f)
        if (version == 1) require(mode == RecordingMode.DropOnly && selectedPart == SongPart.Drop && sections.isEmpty())
        else require(sections.map { it.part }.toSet() == SongPart.entries.filter { it != SongPart.Drop }.toSet() &&
            sections.size == 4 && (mode == RecordingMode.FullSong || selectedPart == SongPart.Drop)) { "编曲结构无效" }
        fun checked(part: SongSection): SongSection {
            require(part.bars in listOf(1, 2, 4) && part.lanes.size == 5) { "小节数或音轨数无效" }
            part.lanes.forEachIndexed { i, lane ->
            require(lane.volume.isFinite() && lane.volume in 0f..1f && lane.events.size <= 256)
            lane.events.forEach { event ->
                require(event.beat.isFinite() && event.beat >= 0 && event.beat < part.bars * 4)
                require(event.pad in Lane.entries[i].pads.indices)
                require(event.velocity.isFinite() && event.velocity in .05f..1f)
            }
            }
            return part.copy(lanes = part.lanes.map { it.copy(events = it.events.sortedBy(NoteEvent::beat)) })
        }
        return copy(lanes = checked(SongSection(SongPart.Drop, bars, lanes)).lanes,
            sections = sections.sortedBy { it.part.ordinal }.map(::checked))
    }
    companion object {
        /** App defaults are independent of the legacy v1 file/score constructor. */
        fun newProject(bpm: Int = 110, quantize: Boolean = true): LoopSession =
            LoopSession(bpm=bpm,bars=4,quantize=quantize,master=1f).withMode(RecordingMode.FullSong)
    }
}

data class LaneStatus(val count: Int, val volume: Float, val muted: Boolean, val canUndo: Boolean)
data class SectionStatus(val part: SongPart, val bars: Int, val notes: Int)
data class LoopStatus(
    val running: Boolean, val bpm: Int, val bars: Int, val quantize: Boolean,
    val beat: Double, val cycle: Long, val countIn: Int, val armed: Int?, val recording: Int?,
    val master: Float, val metronome: Boolean, val lanes: List<LaneStatus>, val peak: Float,
    val triggers: Long, val message: String, val auto: AutoProgress? = null,
    val mode: RecordingMode = RecordingMode.DropOnly, val part: SongPart = SongPart.Drop,
    val arrangementPlaying: Boolean = false, val sections: List<SectionStatus> = emptyList(),
    /** Absolute sample-clock beat, continuous across parts of different lengths, negative during count-in. */
    val elapsedBeats: Double = 0.0,
)

interface AudioOutput : AutoCloseable {
    val description: String
    fun start()
    fun write(stereo: ShortArray): Boolean
    fun stop()
}
fun interface AudioOutputFactory { fun create(): AudioOutput }
