package com.example.spatialdj.p170bd28c.domain.audio

import com.example.spatialdj.p170bd28c.domain.model.*
import kotlin.math.*

/** All public operations share one short render lock. Time advances by samples, not UI frames. */
class LoopEngine(val bank: SoundBank = SoundBank()) {
    private var session = LoopSession.newProject()
    private var current = session.section()
    private val partUndo = Array(5) { arrayOfNulls<LaneSession>(5) }
    private val undo get() = partUndo[session.selectedPart.ordinal]
    private val cursor = IntArray(5)
    private var running = false
    private var frame = 0L
    private var cycle = 0L
    private var loopStartBeat = 0.0
    private var arrangementPlaying = false
    private var armed: Int? = null
    private var recording: Int? = null
    private var startCycle = 0L
    private var working: ArrayList<NoteEvent>? = null
    private var metronome = false
    private var lastClickBeat = Long.MIN_VALUE
    private var peak = 0f
    private var triggers = 0L
    private var message = "先选音轨，录下一圈，再继续叠加"
    private class AutoRun(val score: AutoScore) {
        var step = 0
        var note = 0
        var played = 0
        val totalNotes = score.steps.sumOf { it.notes.size }
        var finished = false
        var recorded = false
        var section = 0
        var sectionStart = 0L
        var flashLane = 0
        var flashPad: Int? = null
        var flashUntil = 0L
    }
    private var automatic: AutoRun? = null
    private class Voice {
        var sample: FloatArray? = null
        var position = 0
        var lane = -1
        var gain = 0f
        var serial = 0L
    }
    private val voices = Array(48) { Voice() }
    private val mix = BounceMix(bank.sampleRate).apply { reset(session.bpm) }
    private var serial = 0L
    private val beatFrames get() = bank.sampleRate * 60.0 / session.bpm
    private val loopBeats get() = current.bars * 4.0
    private fun due(beat: Double) = ceil(beat * beatFrames - 1e-8).toLong()

    private fun playingAutoSection(): AutoSection? =
        automatic?.takeIf { it.recorded && !it.finished }?.let { it.score.sections[it.section] }

    @Synchronized fun snapshot(): LoopStatus {
        val clip=playingAutoSection()
        val visiblePart=clip?.part ?: session.selectedPart
        return LoopStatus(running, session.bpm, current.bars, session.quantize,
        if (frame < 0) 0.0 else (frame / beatFrames - loopStartBeat).coerceIn(0.0, loopBeats),
        cycle, if (frame < 0) (4-floor((frame+due(4.0))/beatFrames).toInt()).coerceIn(1,4) else 0, armed, recording,
        session.master, metronome, current.lanes.mapIndexed { i, lane ->
            LaneStatus(if (recording == i) working!!.size else clip?.lanes?.get(i)?.size ?: lane.events.size,
                lane.volume, lane.muted, clip==null && undo[i] != null)
        }, peak, triggers, message, automatic?.let { auto ->
            AutoProgress(!auto.finished,auto.score.title,auto.step+1,auto.score.steps.size,
                if(auto.recorded) auto.flashLane else auto.score.steps[auto.step].lane,auto.played,auto.totalNotes,
                auto.flashPad.takeIf { frame<auto.flashUntil && !auto.finished },
                if(auto.recorded && !auto.finished) auto.score.sections[auto.section].title else null)
        }, session.mode, visiblePart, arrangementPlaying || clip!=null,
        if (session.mode == RecordingMode.FullSong) SongPart.entries.map {
            val part = session.section(it)
            SectionStatus(it, part.bars, if(clip!=null && it==visiblePart) clip.lanes.sumOf { lane -> lane.size }
                else part.lanes.sumOf { lane -> lane.events.size })
        } else listOf(SectionStatus(SongPart.Drop, current.bars, current.lanes.sumOf { it.events.size })),
        elapsedBeats = frame / beatFrames)
    }

    @Synchronized fun startAutomatic(score: AutoScore) {
        val checked=score.validate() // Validate before stopping/mutating the current project.
        load(checked.emptySession())
        metronome=false
        automatic=AutoRun(checked)
        record(checked.steps.first().lane)
        message="自动演奏：四拍预备后开始逐轨录音"
    }

    @Synchronized fun exportSession(): LoopSession = session.copy(
        lanes = session.lanes.map { it.copy(events = it.events.toList()) },
        sections = session.sections.map { part -> part.copy(lanes = part.lanes.map { it.copy(events = it.events.toList()) }) })
    @Synchronized fun load(value: LoopSession) {
        val checked = value.validate()
        stop()
        session = checked
        current = session.section()
        mix.reset(session.bpm)
        partUndo.forEach { it.fill(null) }
        message = "工程已载入 · ${session.mode.title}"
    }
    @Synchronized fun mode(value: RecordingMode) {
        if (value == session.mode) return
        stop()
        session = session.withMode(value)
        current = session.section()
        message = "${value.title} · 已录内容保留"
    }
    @Synchronized fun selectPart(value: SongPart) {
        if (session.mode != RecordingMode.FullSong || value == session.selectedPart) return
        stop()
        session = session.copy(selectedPart = value)
        current = session.section()
        message = "${value.title} · ${value.hint}"
    }
    @Synchronized fun tempo(value: Int): Boolean {
        if (running) { message = "先停止，再调整速度"; return false }
        session = session.copy(bpm = value.coerceIn(60,180)); mix.reset(session.bpm); return true
    }
    @Synchronized fun bars(value: Int): Boolean {
        require(value in listOf(1,2,4))
        if (running || current.lanes.any { it.events.isNotEmpty() }) {
            message = "先停止；已有录音的部分需清空后调整小节数"; return false
        }
        if (current.bars != value) {
            current = current.copy(bars = value); session = session.withSection(current)
            // Undo notes from the old length may lie outside the new loop's valid beat range.
            undo.fill(null)
        }
        return true
    }
    @Synchronized fun quantize() { session = session.copy(quantize = !session.quantize) }
    @Synchronized fun metronome() { metronome = !metronome }
    @Synchronized fun master(value: Float) {
        if (value.isFinite()) session = session.copy(master = value.coerceIn(0f,1f))
    }
    @Synchronized fun volume(lane: Int, value: Float) {
        require(lane in 0..4)
        if (value.isFinite()) changeLane(lane, current.lanes[lane].copy(volume = value.coerceIn(0f,1f)))
    }
    @Synchronized fun mute(lane: Int) {
        require(lane in 0..4)
        changeLane(lane, current.lanes[lane].copy(muted = !current.lanes[lane].muted))
    }
    @Synchronized fun play() {
        if (running) { stop(); return }
        frame = 0; cycle = 0; loopStartBeat = 0.0; cursor.fill(0); lastClickBeat = Long.MIN_VALUE
        running = true; message = "${session.selectedPart.title} · 五轨同步循环中"
    }
    @Synchronized fun playArrangement() {
        if (running) { stop(); return }
        if (session.mode != RecordingMode.FullSong) { play(); return }
        session = session.copy(selectedPart = SongPart.Intro); current = session.section()
        play(); arrangementPlaying = true
        message = "全曲播放 · Intro → Breakdown → Build-up → Drop → Outro"
    }
    @Synchronized fun stop() {
        automatic=null
        arrangementPlaying = false
        running = false; armed = null; recording = null; working = null
        frame = 0; cycle = 0; loopStartBeat = 0.0; cursor.fill(0); voices.forEach { it.sample = null }
        mix.reset(session.bpm)
        peak = 0f; message = "已停止，乐段保留"
    }
    @Synchronized fun newSession() {
        stop(); session = LoopSession.newProject(bpm=session.bpm,quantize=session.quantize)
        current = session.section()
        partUndo.forEach { it.fill(null) }; message = "新工程：先选小节数，再录第一轨"
    }
    @Synchronized fun record(lane: Int) {
        require(lane in 0..4)
        if (arrangementPlaying) stop()
        if (armed != null || recording != null) {
            cancelRecording(); message = "本圈录音取消，原乐段保留"; return
        }
        armed = lane
        if (!running) {
            frame = -due(4.0); cycle = 0; loopStartBeat = 0.0; cursor.fill(0); lastClickBeat = Long.MIN_VALUE
            running = true; startCycle = 0
            message = "数四拍后录一圈，听到高音拍开始"
        } else {
            startCycle = if (frame < 0) 0 else cycle + 1
            message = "等下一圈开始录 ${Lane.entries[lane].title}"
        }
    }
    private fun cancelRecording() { armed = null; recording = null; working = null }
    @Synchronized fun undo(lane: Int) {
        require(lane in 0..4)
        if (armed == lane || recording == lane) cancelRecording()
        val prior = undo[lane] ?: return
        undo[lane] = current.lanes[lane]
        changeLane(lane, prior.copy(volume = current.lanes[lane].volume, muted = current.lanes[lane].muted)); seekCursor(lane)
        message = "${Lane.entries[lane].title} 撤销 / 恢复"
    }
    @Synchronized fun clear(lane: Int) {
        require(lane in 0..4)
        if (armed == lane || recording == lane) cancelRecording()
        undo[lane] = current.lanes[lane]
        changeLane(lane, current.lanes[lane].copy(events = emptyList()))
        cursor[lane] = 0; voices.filter { it.lane == lane }.forEach { it.sample = null }
    }
    @Synchronized fun pad(lane: Int, pad: Int, velocity: Float = .8f): Boolean {
        require(lane in 0..4 && pad in Lane.entries[lane].pads.indices)
        require(velocity.isFinite() && velocity in .05f..1f)
        triggerPad(lane,pad,velocity)
        if (recording == lane) {
            if (working!!.size >= 256) { message = "本轨已达 256 音符上限"; return false }
            val raw = (frame / beatFrames - loopStartBeat).coerceIn(0.0, loopBeats)
            val beat = if (session.quantize) ((raw * 4).roundToLong() / 4.0) % loopBeats else min(raw, loopBeats - 1e-7)
            working!!.add(NoteEvent(beat, pad, velocity))
        }
        return true
    }
    private fun triggerPad(lane: Int,pad: Int,gain: Float) {
        if(lane==0 && pad==0) mix.kick()
        trigger(bank.sounds[lane][pad],lane,gain)
    }
    private fun trigger(sample: FloatArray, lane: Int, gain: Float) {
        var target: Voice? = null
        for (v in voices) if (v.sample == null) { target = v; break }
        if (target == null) target = voices.minBy { it.serial }
        target.sample = sample; target.position = 0; target.lane = lane
        target.gain = gain; target.serial = ++serial
        if (lane >= 0) triggers++
    }
    private fun changeLane(index: Int, lane: LaneSession) {
        current = current.copy(lanes = current.lanes.mapIndexed { i, old -> if (index == i) lane else old })
        session = session.withSection(current)
    }
    private fun seekCursor(lane: Int) {
        var c = 0
        val events = current.lanes[lane].events
        while (c < events.size && due(loopStartBeat + events[c].beat) < frame) c++
        cursor[lane] = c
    }
    private fun boundary() {
        if (arrangementPlaying && cycle > 0) {
            val next = SongPart.entries.getOrNull(session.selectedPart.ordinal + 1)
            if (next == null) { stop(); message = "全曲播放完成 · 所有部分已保留"; return }
            session = session.copy(selectedPart = next); current = session.section()
            message = "全曲播放 · ${next.title}"
        }
        if (recording != null && cycle > startCycle) {
            val lane = recording!!
            undo[lane] = current.lanes[lane]
            changeLane(lane, current.lanes[lane].copy(events = working!!.sortedBy { it.beat }))
            recording = null; working = null
            message = "${Lane.entries[lane].title} 已循环，选下一轨继续叠录"
            automatic?.takeUnless { it.finished }?.let { auto ->
                if(auto.step==auto.score.steps.lastIndex) {
                    auto.recorded=true
                    auto.finished=auto.score.sections.isEmpty()
                    auto.sectionStart=cycle
                    message=if(auto.finished) "${auto.score.title}演奏完成，五轨循环中"
                        else "${auto.score.title} · ${auto.score.sections[0].title}"
                } else {
                    auto.step++; auto.note=0; auto.flashPad=null
                    armed=auto.score.steps[auto.step].lane; startCycle=cycle
                }
            }
        }
        automatic?.takeIf { it.recorded && !it.finished }?.let { auto ->
            if(cycle-auto.sectionStart>=auto.score.sections[auto.section].loops) {
                auto.section=(auto.section+1)%auto.score.sections.size
                auto.sectionStart=cycle
                auto.flashPad=null
                message="${auto.score.title} · ${auto.score.sections[auto.section].title}"
            }
            val clip=auto.score.sections[auto.section]
            auto.flashLane=if(clip.lanes[3].isNotEmpty() && clip.gains[3]>0f) 3
                else (0..4).firstOrNull { clip.lanes[it].isNotEmpty() && clip.gains[it]>0f } ?: 0
        }
        if (armed != null && cycle == startCycle) {
            recording = armed; armed = null
            working = ArrayList(current.lanes[recording!!].events)
            message = "正在录 ${Lane.entries[recording!!].title}，满一圈自动循环"
        }
        cursor.fill(0)
    }
    private fun events(lane: Int): List<NoteEvent> {
        return playingAutoSection()?.lanes?.get(lane) ?: current.lanes[lane].events
    }
    @Synchronized fun render(stereo: ShortArray) {
        require(stereo.size % 2 == 0)
        var batchPeak = 0f
        for (n in 0 until stereo.size / 2) {
            if (running) {
                if (frame >= 0) {
                    if (frame == 0L) boundary()
                    while (running && frame >= due(loopStartBeat + loopBeats)) {
                        loopStartBeat += loopBeats; cycle++; boundary()
                    }
                    for (lane in 0..4) if (running) {
                        val events = events(lane)
                        while (cursor[lane] < events.size &&
                            frame >= due(loopStartBeat + events[cursor[lane]].beat)) {
                            val event = events[cursor[lane]++]
                            if (!current.lanes[lane].muted) {
                                triggerPad(lane,event.pad,event.velocity)
                                automatic?.takeIf { it.recorded && !it.finished && lane==it.flashLane }?.let { auto ->
                                    auto.flashLane=lane; auto.flashPad=event.pad
                                    auto.flashUntil=frame+bank.sampleRate*75/1000
                                }
                            }
                        }
                    }
                    // Schedule each pad on the shared audio sample clock, never on a UI timer.
                    // Existing committed tracks play above; this pass is recorded via pad().
                    automatic?.takeUnless { it.finished || it.recorded }?.let { auto ->
                        val step=auto.score.steps[auto.step]
                        if(recording==step.lane) {
                            while(auto.note<step.notes.size &&
                                frame>=due(loopStartBeat+step.notes[auto.note].beat)) {
                                val note=step.notes[auto.note++]
                                pad(step.lane,note.pad,note.velocity)
                                auto.played++; auto.flashPad=note.pad
                                auto.flashUntil=frame+bank.sampleRate*75/1000
                            }
                        }
                    }
                }
                val clickBeat = if (frame < 0) floor((frame+due(4.0))/beatFrames).toLong()-4
                    else floor(frame / beatFrames + 1e-9).toLong()
                if (running && clickBeat != lastClickBeat) {
                    if (frame < 0 || metronome || (frame == 0L && recording != null))
                        trigger(bank.click, -1, if (clickBeat % 4 == 0L) 1f else .6f)
                    lastClickBeat = clickBeat
                }
            }
            var left = 0f; var right = 0f
            var sendL = 0f; var sendR = 0f
            val duck=mix.duck()
            val auto=automatic
            val gains=if(auto!=null && auto.recorded && !auto.finished) auto.score.sections[auto.section].gains else null
            for (v in voices) {
                val sound = v.sample ?: continue
                val level = if (v.lane < 0) .8f else current.lanes[v.lane].let { if (it.muted) 0f else it.volume }
                val volume=v.gain*level*(if(v.lane>=0) gains?.get(v.lane) ?: 1f else 1f)*
                    (if(v.lane>=2) duck else 1f)
                val l=sound[v.position++]*volume; val r=sound[v.position++]*volume
                left+=l; right+=r
                if(v.lane>=3) { sendL+=l; sendR+=r }
                if (v.position == sound.size) v.sample = null
            }
            mix.process(left,right,sendL,sendR,session.master)
            val l=mix.left; val r=mix.right
            batchPeak = max(batchPeak, max(abs(l), abs(r)))
            stereo[n*2] = (l*32767).toInt().toShort(); stereo[n*2+1] = (r*32767).toInt().toShort()
            if (running) frame++
        }
        peak = batchPeak
    }
}
