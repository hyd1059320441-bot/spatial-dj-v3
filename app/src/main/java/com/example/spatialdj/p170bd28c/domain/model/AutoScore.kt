package com.example.spatialdj.p170bd28c.domain.model

import kotlin.math.abs
import kotlin.math.round

data class AutoStep(val lane: Int, val notes: List<NoteEvent>)

data class AutoSection(val title: String, val loops: Int, val gains: List<Float>, val lanes: List<List<NoteEvent>>,
                       val part: SongPart = SongPart.Drop)

/** Recording passes use pad(); optional sections then evolve on the same sample clock. */
data class AutoScore(
    val title: String,
    val bpm: Int,
    val bars: Int,
    val master: Float,
    val volumes: List<Float>,
    val steps: List<AutoStep>,
    val sections: List<AutoSection> = emptyList(),
) {
    fun validate(): AutoScore {
        require(title.isNotBlank() && title.length<=80 && volumes.size==5 && steps.size in 1..16)
        require(steps.all { it.lane in 0..4 && it.notes.isNotEmpty() })
        require(steps.all { step -> step.notes.all { abs(it.beat*4-round(it.beat*4))<1e-8 } })
        expectedSession().validate()
        require(sections.size<=32)
        sections.forEach { section ->
            require(section.title.isNotBlank() && section.title.length<=40 && section.loops in 1..8)
            require(section.gains.size==5 && section.gains.all { it.isFinite() && it in 0f..1f })
            require(section.lanes.size==5)
            emptySession().copy(lanes=section.lanes.map { LaneSession(it) }).validate()
            require(section.lanes.all { notes -> notes.all { abs(it.beat*4-round(it.beat*4))<1e-8 } })
        }
        return copy(volumes=volumes.toList(),steps=steps.map {
            it.copy(notes=it.notes.sortedBy(NoteEvent::beat))
        },sections=sections.map { it.copy(gains=it.gains.toList(),
            lanes=it.lanes.map { notes -> notes.sortedBy(NoteEvent::beat) }) })
    }

    fun emptySession() = LoopSession(bpm=bpm,bars=bars,master=master,
        lanes=volumes.map { LaneSession(volume=it) }).withMode(RecordingMode.FullSong).copy(selectedPart=SongPart.Drop)

    fun expectedSession() = emptySession().copy(lanes=List(5) { lane ->
        LaneSession(steps.filter { it.lane==lane }.flatMap { it.notes }.sortedBy(NoteEvent::beat),
            volume=volumes[lane])
    })
}

data class AutoProgress(
    val active: Boolean,
    val title: String,
    val step: Int,
    val totalSteps: Int,
    val lane: Int,
    val notesPlayed: Int,
    val totalNotes: Int,
    val flashPad: Int?,
    val section: String? = null,
)
