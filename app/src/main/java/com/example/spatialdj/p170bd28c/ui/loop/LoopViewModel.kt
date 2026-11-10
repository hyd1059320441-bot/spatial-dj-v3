package com.example.spatialdj.p170bd28c.ui.loop

import android.content.Context
import android.content.ContentValues
import android.os.Process
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.spatialdj.p170bd28c.data.audio.*
import com.example.spatialdj.p170bd28c.domain.audio.LoopEngine
import com.example.spatialdj.p170bd28c.domain.audio.SoundBank
import com.example.spatialdj.p170bd28c.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.concurrent.thread

data class LoopUiState(
    val ready: Boolean = false, val status: LoopStatus? = null, val selected: Int = 0,
    val height: Float = 1.1f, val output: String = "准备音频", val notice: String = "",
    val busy: Boolean = false, val error: String? = null, val exit: Boolean = false,
    val autoBackup: Boolean = false,
)
sealed interface LoopAction {
    data class Select(val lane: Int): LoopAction
    data class Pad(val lane: Int, val pad: Int): LoopAction
    data class Volume(val lane: Int, val value: Float): LoopAction
    data class Master(val value: Float): LoopAction
    data class Tempo(val value: Int): LoopAction
    data class Bars(val value: Int): LoopAction
    data class Mode(val value: RecordingMode): LoopAction
    data class Part(val value: SongPart): LoopAction
    data class Height(val delta: Float): LoopAction
    data class Mute(val lane: Int): LoopAction
    data object Play: LoopAction
    data object Audition: LoopAction
    data object Record: LoopAction
    data object Quantize: LoopAction
    data object Metronome: LoopAction
    data object Undo: LoopAction
    data object Clear: LoopAction
    data object Save: LoopAction
    data object Load: LoopAction
    data object Export: LoopAction
    data object New: LoopAction
    data object Exit: LoopAction
    data object Automatic: LoopAction
    data object RestoreBeforeAuto: LoopAction
}

class LoopViewModel(private val context: Context): ViewModel() {
    private val mutable = MutableStateFlow(LoopUiState())
    val state = mutable.asStateFlow()
    private var engine: LoopEngine? = null
    private var output: AudioOutput? = null
    private var worker: Thread? = null
    @Volatile private var audioActive = false
    private var factory: AudioOutputFactory? = null
    private var foreground = true
    private var closed = false
    private var pendingDestructive: Pair<String,Long>? = null
    private var autoScore: AutoScore? = null
    private var beforeAuto: LoopSession? = null
    init {
        viewModelScope.launch {
            try {
                autoScore=withContext(Dispatchers.IO) {
                    context.assets.open("music/neon-bounce.json").bufferedReader().use { AutoScoreFiles.read(it.readText()) }
                }
                engine = withContext(Dispatchers.IO) { LoopEngine(SoundBank(loader={ lane,pad ->
                    SoundBank.readStereoWav(context.assets.open("audio/neon/$lane-$pad.wav"))
                })) }
                mutable.value = mutable.value.copy(ready = true)
                if (foreground && factory != null) startOutput()
                while (isActive) { refresh(); delay(33) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                fail(error)
            }
        }
    }
    fun bind(value: AudioOutputFactory?) {
        stopOutput(); factory = value
        if (value != null && engine != null && foreground && !closed) startOutput()
    }
    fun foreground(value: Boolean) {
        foreground = value
        if (!value) { cancelAuto(); engine?.stop(); stopOutput(); refresh() }
        else if (engine != null && output == null && factory != null && !closed) startOutput()
    }
    private fun startOutput() {
        try {
            val audio = checkNotNull(factory).create()
            output = audio
            audio.start()
            audioActive = true
            val synth = checkNotNull(engine)
            mutable.value = mutable.value.copy(output = audio.description, error = null)
            worker = thread(name = "SpatialLoopAudio") {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val pcm = ShortArray(384)
                try {
                    while (audioActive) {
                        synth.render(pcm)
                        if (!audio.write(pcm)) break
                    }
                } catch (error: Exception) {
                    if (audioActive) viewModelScope.launch {
                        if (output === audio && !closed) { fail(error); stopOutput() }
                    }
                }
            }
            Log.i("SpatialLoop", "AudioReady sampleRate=48000 frames=192 output=${audio.description}")
        } catch (error: Exception) { fail(error); stopOutput() }
    }
    private fun stopOutput() {
        audioActive = false
        output?.stop()
        worker?.join(1500)
        check(worker?.isAlive != true) { "音频线程未停止" }
        worker = null
        output?.close(); output = null
    }
    private fun fail(error: Exception) {
        engine?.stop()
        mutable.value = mutable.value.copy(error = "音频错误：${error.message}", notice = "")
        Log.e("SpatialLoop","Operation failed",error)
    }
    private fun refresh() { engine?.let {
        val status=it.snapshot()
        mutable.value = mutable.value.copy(status=status,
            selected=if(status.auto?.active==true) status.auto.lane else mutable.value.selected)
    } }
    private fun cancelAuto() {
        if(engine?.snapshot()?.auto?.active==true) {
            beforeAuto?.let { engine?.load(it) } ?: engine?.stop()
            mutable.value=mutable.value.copy(notice="自动演奏已停止，原工程已恢复")
        }
    }
    private fun confirm(key: String, label: String): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (pendingDestructive?.let { it.first == key && now-it.second < 3000 } == true) {
            pendingDestructive = null; return true
        }
        pendingDestructive = key to now
        mutable.value = mutable.value.copy(notice = "3 秒内再触碰一次，确认$label")
        return false
    }
    fun action(action: LoopAction) {
        if (closed) return
        if (action == LoopAction.Exit) { close(); mutable.value = mutable.value.copy(exit = true); return }
        val synth = engine ?: return
        if (!foreground || mutable.value.busy) return
        val lane = mutable.value.selected
        if(synth.snapshot().auto?.active==true && action !is LoopAction.Volume &&
            action !is LoopAction.Master && action !is LoopAction.Height &&
            action!=LoopAction.Automatic && action!=LoopAction.Play && action!=LoopAction.RestoreBeforeAuto) return
        when (action) {
            is LoopAction.Select -> mutable.value = mutable.value.copy(selected = action.lane.coerceIn(0,4), notice = "")
            is LoopAction.Pad -> synth.pad(action.lane,action.pad)
            is LoopAction.Volume -> synth.volume(action.lane,action.value)
            is LoopAction.Master -> synth.master(action.value)
            is LoopAction.Tempo -> synth.tempo(action.value)
            is LoopAction.Bars -> synth.bars(action.value)
            is LoopAction.Mode -> {
                pendingDestructive = null
                synth.mode(action.value); mutable.value = mutable.value.copy(notice = "")
            }
            is LoopAction.Part -> {
                pendingDestructive = null
                synth.selectPart(action.value); mutable.value = mutable.value.copy(notice = "")
            }
            is LoopAction.Height -> mutable.value = mutable.value.copy(height = (mutable.value.height+action.delta).coerceIn(.9f,1.8f))
            is LoopAction.Mute -> synth.mute(action.lane)
            LoopAction.Play -> {
                if(synth.snapshot().auto?.active==true) cancelAuto()
                else {
                    if(output == null) startOutput()
                    if(output != null) {
                        if (synth.snapshot().mode == RecordingMode.FullSong) synth.playArrangement() else synth.play()
                    }
                }
            }
            LoopAction.Audition -> {
                if (output == null) startOutput()
                if (output != null) synth.play()
            }
            LoopAction.Automatic -> {
                if(synth.snapshot().auto?.active==true) cancelAuto() else {
                    if(output==null) startOutput()
                    if(output!=null) {
                        beforeAuto=synth.exportSession()
                        val backup=checkNotNull(beforeAuto)
                        viewModelScope.launch(Dispatchers.IO) {
                            runCatching { SessionFiles.write(File(context.filesDir,"before-auto.loop"),backup) }
                                .onFailure { Log.w("SpatialLoop","Auto backup write failed",it) }
                        }
                        mutable.value=mutable.value.copy(autoBackup=true,notice="",selected=0)
                        pendingDestructive=null
                        synth.startAutomatic(checkNotNull(autoScore))
                        val score=checkNotNull(autoScore)
                        Log.i("SpatialLoop","AutomaticStarted score=${score.title} steps=${score.steps.size} " +
                            "notes=${score.steps.sumOf { it.notes.size }} sections=${score.sections.size} sampleClock=true")
                    }
                }
            }
            LoopAction.RestoreBeforeAuto -> beforeAuto?.let {
                synth.load(it)
                mutable.value=mutable.value.copy(notice="已恢复自动演奏前的工程",autoBackup=false)
                beforeAuto=null
            }
            LoopAction.Record -> { synth.record(lane); mutable.value = mutable.value.copy(notice = "") }
            LoopAction.Quantize -> synth.quantize()
            LoopAction.Metronome -> synth.metronome()
            LoopAction.Undo -> synth.undo(lane)
            LoopAction.Clear -> if (confirm("clear-${synth.snapshot().part}-$lane",
                "清空 ${synth.snapshot().part.title} 的${Lane.entries[lane].title}")) synth.clear(lane)
            LoopAction.New -> if (confirm("new","新建工程")) { synth.newSession(); mutable.value = mutable.value.copy(notice = "") }
            LoopAction.Save -> if(synth.snapshot().recording != null || synth.snapshot().armed != null) {
                mutable.value = mutable.value.copy(notice = "录完本圈后再保存工程")
            } else {
                val session = synth.exportSession()
                files("保存") { SessionFiles.write(File(context.filesDir,"saved.loop"),session); "工程已保存" }
            }
            LoopAction.Load -> if (confirm("load","载入上次保存")) {
                files("载入") {
                    val saved = SessionFiles.read(File(context.filesDir,"saved.loop"))
                    withContext(Dispatchers.Main) { synth.load(saved) }
                    "已载入上次工程"
                }
            }
            LoopAction.Export -> {
                val session = synth.exportSession()
                if(synth.snapshot().recording != null || synth.snapshot().armed != null) {
                    mutable.value = mutable.value.copy(notice = "录完本圈后再导出音乐")
                } else if (!session.hasNotes()) {
                    mutable.value = mutable.value.copy(notice = "先录一段，再导出音乐")
                } else files("导出") {
                    val name = "SpatialLoop-${System.currentTimeMillis()}.wav"
                    val file = File(context.getExternalFilesDir("exports"),name)
                    SessionFiles.wav(file,synth,session)
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME,name)
                        put(MediaStore.Audio.Media.MIME_TYPE,"audio/wav")
                        put(MediaStore.Audio.Media.RELATIVE_PATH,"Music/SpatialLoopV3")
                        put(MediaStore.Audio.Media.IS_PENDING,1)
                    }
                    val resolver = context.contentResolver
                    val uri = checkNotNull(resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,values))
                    try {
                        checkNotNull(resolver.openOutputStream(uri)).use { dest -> file.inputStream().use { it.copyTo(dest) } }
                        resolver.update(uri,ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING,0) },null,null)
                    } catch (error: Exception) { resolver.delete(uri,null,null); throw error }
                    Log.i("SpatialLoop","ExportReady file=${file.absolutePath} bytes=${file.length()}")
                    "已导出${if (session.mode == RecordingMode.FullSong) "全曲" else " 4 圈 Drop"} WAV 到 Music/SpatialLoopV3"
                }
            }
            LoopAction.Exit -> Unit
        }
        refresh()
    }
    private fun files(label: String, block: suspend () -> String) {
        mutable.value = mutable.value.copy(busy = true, notice = "正在$label…")
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { block() }
                mutable.value = mutable.value.copy(notice = result)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.value = mutable.value.copy(notice = "$label 失败：${error.message}")
                Log.e("SpatialLoop","File operation failed",error)
            } finally { mutable.value = mutable.value.copy(busy = false); refresh() }
        }
    }
    fun close() {
        if (closed) return
        closed = true; engine?.stop(); stopOutput(); factory = null
    }
    override fun onCleared() { close() }
    class Factory(private val context: Context): ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T: ViewModel> create(modelClass: Class<T>): T = LoopViewModel(context.applicationContext) as T
    }
}
