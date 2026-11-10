package com.example.spatialdj.p170bd28c

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.spatialdj.p170bd28c.data.audio.SessionFiles
import com.example.spatialdj.p170bd28c.domain.audio.LoopEngine
import com.example.spatialdj.p170bd28c.domain.model.RecordingMode
import com.example.spatialdj.p170bd28c.domain.model.SongPart
import com.example.spatialdj.p170bd28c.ui.loop.FingertipGate
import com.example.spatialdj.p170bd28c.ui.loop.LoopViewModel
import com.example.spatialdj.p170bd28c.ui.loop.PanelInput
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs

/**
 * Device UI performance, invoked explicitly with am instrument -e score <JSON>.
 * The engine and VM are inspected only. Every musical edit goes through measured UI
 * controls and PanelInput.hands, including new/save/export and every individual note.
 * This replays fingertip input; it is not adb screen tapping or physical hand tracking.
 */
class UiMusicPerformance {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var vm: LoopViewModel
    private lateinit var input: PanelInput
    private lateinit var engine: LoopEngine
    private lateinit var attachment: Entity
    private val trace = JSONArray()
    private var clicks = 0

    private fun field(value: Any, name: String): Any? =
        value.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(value)

    private fun <T> find(type: Class<T>): T? {
        val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        fun scan(value: Any?, depth: Int): T? {
            if (value == null || depth > 35 || !seen.add(value)) return null
            if (type.isInstance(value)) return type.cast(value)
            if (value is ViewModelStoreOwner) scan(value.viewModelStore, depth + 1)?.let { return it }
            if (value is android.app.Activity) scan(value.window.decorView, depth + 1)?.let { return it }
            if (value is android.view.ViewGroup) {
                for (i in 0 until value.childCount) scan(value.getChildAt(i), depth + 1)?.let { return it }
            }
            if (value is android.util.SparseArray<*>) {
                for (i in 0 until value.size()) scan(value.valueAt(i), depth + 1)?.let { return it }
                return null
            }
            if (value is Pair<*, *>) {
                scan(value.first, depth + 1)?.let { return it }
                scan(value.second, depth + 1)?.let { return it }
                return null
            }
            if (value is Map<*, *>) {
                value.values.forEach { scan(it, depth + 1)?.let { result -> return result } }
                return null
            }
            if (value is Iterable<*>) {
                value.forEach { scan(it, depth + 1)?.let { result -> return result } }
                return null
            }
            if (value is Array<*>) {
                value.forEach { scan(it, depth + 1)?.let { result -> return result } }
                return null
            }
            if (value is StateFlow<*>) return scan(value.value, depth + 1)
            if (value is androidx.compose.runtime.State<*>) return scan(value.value, depth + 1)
            var cls: Class<*>? = value.javaClass
            while (cls != null && cls != Any::class.java) {
                if (listOf("com.pico.", "androidx.lifecycle.", "androidx.activity.", "androidx.fragment.",
                        "androidx.compose.", "com.example.spatialdj.", "android.view.", "android.widget.")
                        .any { cls!!.name.startsWith(it) }) {
                    cls.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !it.type.isPrimitive }.forEach { f ->
                        scan(runCatching { f.isAccessible = true; f.get(value) }.getOrNull(), depth + 1)?.let { return it }
                    }
                }
                cls = cls.superclass
            }
            return null
        }
        Stage.entries.flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
            .distinct().forEach { scan(it, 0)?.let { result -> return result } }
        return null
    }

    private suspend fun await(label: String, timeout: Long = 15000, predicate: () -> Boolean) {
        try { withTimeout(timeout) { while (!predicate()) delay(4) } }
        catch (timeoutError: TimeoutCancellationException) { error("Timed out: $label") }
        check(vm.state.value.error == null) { vm.state.value.error ?: "Audio error" }
    }

    /** Resolve a currently registered, measured Compose control, never a fixed pixel coordinate. */
    private suspend fun locate(id: String): FingertipGate.Surface {
        var face: FingertipGate.Surface? = null
        withTimeout(15000) {
            while (face == null) {
                face = withContext(Dispatchers.Main) {
                    @Suppress("UNCHECKED_CAST")
                    val registered = field(input, "controls") as Map<String, Any>
                    registered[id]?.let { field(it, "surface") as FingertipGate.Surface }
                }
                if (face == null) delay(4)
            }
        }
        return checkNotNull(face).also {
            val r = it.rect
            check(r.width > 0 && r.height > 0 && r.x >= 0 && r.y >= 0 &&
                r.x + r.width <= input.gate.widthPixels + 1 &&
                r.y + r.height <= input.gate.heightPixels + 1) { "Invalid live bounds: $id" }
        }
    }

    private suspend fun click(id: String, fraction: Float = .5f, expectedBeat: Double? = null,
                              lane: Int? = null, resolved: FingertipGate.Surface? = null) {
        if (expectedBeat == null) {
            // Save/export disable input until a subsequent rendered frame observes busy=false.
            // File completion alone does not establish that the contact gate is ready again.
            withTimeout(15000) {
                while (!withContext(Dispatchers.Main) { input.gate.active && !vm.state.value.busy }) delay(4)
            }
        }
        val face = resolved ?: locate(id)
        withContext(Dispatchers.Main) {
            check(input.gate.active) { "Live panel input is inactive" }
            @Suppress("UNCHECKED_CAST")
            val registered = field(input, "controls") as Map<String, Any>
            check(registered[id]?.let { field(it, "surface") } == face) { "Element moved after locating: $id" }
            val r = face.rect
            val x = r.x + face.insetPixels + (r.width - 2 * face.insetPixels) * fraction
            val y = r.y + r.height / 2
            fun tip(z: Float): FingertipGate.Tip {
                val local = Vector3((x / input.gate.widthPixels - .5f) * input.gate.widthMeters,
                    (.5f - y / input.gate.heightPixels) * input.gate.heightMeters, z)
                val stage = attachment.convertPositionTo(local, null)
                val back = attachment.convertPositionFrom(stage, null)
                check(abs(local.x - back.x) < .0001f && abs(local.y - back.y) < .0001f &&
                    abs(local.z - back.z) < .0001f) { "Panel coordinate conversion failed" }
                return FingertipGate.Tip(1, back.x, back.y, back.z)
            }
            val before = engine.snapshot()
            if (expectedBeat != null) {
                check(before.recording == lane && abs(before.beat - expectedBeat) < .11) {
                    "Missed beat $expectedBeat: actual=${before.beat} recording=${before.recording}"
                }
            }
            val now = SystemClock.uptimeMillis()
            input.hands(now, listOf(tip(.05f)))
            input.hands(now, listOf(tip(.005f)))
            input.hands(now, listOf(tip(.04f)))
            val after = engine.snapshot()
            if (expectedBeat != null) {
                check(after.lanes[lane!!].count == before.lanes[lane].count + 1) { "UI note did not record: $id" }
            }
            clicks++
            trace.put(JSONObject().put("step", clicks).put("id", id).put("x", x).put("y", y)
                .put("fraction", fraction).put("cycle", before.cycle).put("beat", before.beat)
                .put("targetBeat", expectedBeat ?: JSONObject.NULL))
            Log.i("UiMusic", "CLICK $id beat=${before.beat} cycle=${before.cycle} bounds=$r")
        }
        if (expectedBeat == null) delay(80)
    }

    private suspend fun tools(open: Boolean) {
        val wanted = if (open) "save" else "lane0"
        val visible = withContext(Dispatchers.Main) {
            @Suppress("UNCHECKED_CAST")
            (field(input, "controls") as Map<String, Any>).containsKey(wanted)
        }
        if (!visible) click("tools")
        locate(wanted)
    }

    private suspend fun recordLane(step: JSONObject) {
        val lane = step.getInt("lane")
        val notes = step.getJSONArray("notes")
        click("lane$lane")
        await("select lane $lane") { vm.state.value.selected == lane }
        // Require the new page's pads before arming; never retain previous-track coordinates.
        locate("pad-$lane-0")
        val prior = engine.exportSession().lanes[lane].events
        click("record")
        await("record lane $lane", 20000) { engine.snapshot().recording == lane }
        val startCycle = engine.snapshot().cycle
        Log.i("UiMusic", "RECORD_START lane=$lane overdub=${prior.isNotEmpty()} notes=${notes.length()}")
        for (i in 0 until notes.length()) {
            val note = notes.getJSONObject(i)
            val beat = note.getDouble("at")
            val id = "pad-$lane-${note.getInt("pad")}"
            // Locate before the target beat. Android's runOnMainSync waits for UI idle,
            // which is unsuitable for musical timing; use the main dispatcher directly.
            val face = locate(id)
            await("lane $lane beat $beat", 12000) {
                val s = engine.snapshot()
                check(s.recording == lane && s.cycle == startCycle) { "Recording ended before note $i" }
                s.beat >= beat
            }
            click(id, expectedBeat = beat, lane = lane, resolved = face)
        }
        await("commit recorded loop", 12000) {
            val s = engine.snapshot()
            s.recording == null && s.armed == null && s.cycle > startCycle
        }
        val recorded = engine.exportSession().lanes[lane].events
        val expected = (prior.map { it.beat to it.pad } +
            (0 until notes.length()).map { notes.getJSONObject(it).getDouble("at") to notes.getJSONObject(it).getInt("pad") })
            .groupingBy { it }.eachCount()
        check(recorded.map { it.beat to it.pad }.groupingBy { it }.eachCount() == expected) {
            "Recorded notes differ from score in lane $lane"
        }
        Log.i("UiMusic", "RECORD_OK lane=$lane count=${recorded.size}")
    }

    @Test fun performScore(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("score") != null)
        val scoreText = checkNotNull(InstrumentationRegistry.getArguments().getString("score")) {
            "Run music/touch_house/perform.py; score is required, never run via connectedAndroidTest."
        }
        val score = JSONObject(scoreText)
        val context = instrumentation.targetContext
        val resultFile = File(context.filesDir, "ui-music-result.json")
        context.startActivity(Intent().setClassName(context.packageName,
            "com.example.spatialdj.p170bd28c.platform.LaunchActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var found: LoopViewModel? = null
        withTimeout(45000) {
            while (found == null) {
                found = withContext(Dispatchers.Main) { find(LoopViewModel::class.java) }
                if (found == null) delay(250)
            }
        }
        vm = checkNotNull(found)
        await("spatial audio ready", 45000) {
            vm.state.value.ready && vm.state.value.status != null && vm.state.value.output == "左右空间音箱"
        }
        input = withContext(Dispatchers.Main) { checkNotNull(find(PanelInput::class.java)) }
        engine = withContext(Dispatchers.Main) { field(vm, "engine") as LoopEngine }
        attachment = withContext(Dispatchers.Main) {
            @Suppress("UNCHECKED_CAST")
            val speakers = field(field(vm, "factory")!!, "speakers") as Pair<Entity, Entity>
            checkNotNull(speakers.first.getParent()!!.findEntity("FingerOnlyLoopPanel"))
        }
        // Preserve the user's previous saved project before the score presses New/Save.
        val saved = File(context.filesDir, "saved.loop")
        if (saved.exists()) saved.copyTo(File(context.filesDir, "before-ui-music-${System.currentTimeMillis()}.loop"))
        try {
            tools(true)
            if (engine.snapshot().running) click("play")
            click("new"); click("new")
            if(engine.snapshot().mode!=RecordingMode.FullSong) click("mode-1")
            tools(false); click("part-${SongPart.Drop.ordinal}"); tools(true)
            check(engine.snapshot().mode==RecordingMode.FullSong && engine.snapshot().part==SongPart.Drop)
            check(engine.snapshot().lanes.all { it.count == 0 })
            val bpm = score.getInt("bpm")
            check(bpm in 60..180 && bpm % 5 == 0) { "BPM must be reachable by the UI's +/-5 buttons" }
            // Saturate at 180 so even a previous non-multiple-of-five session is normalized through the UI.
            while (engine.snapshot().bpm < 180) click("bpm+")
            while (engine.snapshot().bpm > bpm) click("bpm-")
            click("bars${score.getInt("bars")}")
            if (!engine.snapshot().quantize) click("quantize")
            if (engine.snapshot().metronome) click("metro")
            click("master", score.getDouble("master").toFloat())
            check(engine.snapshot().bpm == bpm && engine.snapshot().bars == score.getInt("bars"))
            tools(false)
            val volumes = score.getJSONArray("volumes")
            for (lane in 0..4) {
                click("lane$lane"); locate("vol$lane")
                click("vol$lane", volumes.getDouble(lane).toFloat())
            }
            val steps = score.getJSONArray("steps")
            for (i in 0 until steps.length()) recordLane(steps.getJSONObject(i))
            tools(true)
            click("save")
            await("UI save complete", 15000) { !vm.state.value.busy && vm.state.value.notice == "工程已保存" }
            val session = SessionFiles.read(saved)
            check(session == engine.exportSession()) { "Saved project differs from live UI performance" }
            click("export")
            await("UI WAV export complete", 60000) {
                !vm.state.value.busy && vm.state.value.notice.startsWith("已导出")
            }
            val exported = checkNotNull(context.getExternalFilesDir("exports")!!.listFiles()?.maxByOrNull { it.lastModified() })
            check(exported.length() > 1000000)
            tools(false); click("lane0")
            val firstTriggers = engine.snapshot().triggers
            var sounding = 0
            repeat(30) {
                delay(500)
                check(vm.state.value.error == null && engine.snapshot().running)
                if (engine.snapshot().peak > .001f) sounding++
            }
            check(sounding >= 20 && engine.snapshot().triggers > firstTriggers + 100) { "Live loop is silent" }
            val counts = session.lanes.map { it.events.size }
            resultFile.writeText(JSONObject().put("ok", true).put("title", score.getString("title"))
                .put("bpm", session.bpm).put("bars", session.bars).put("counts", JSONArray(counts))
                .put("clicks", clicks).put("input", "live-control-locator/fingertip-contact/release")
                .put("wav", exported.absolutePath).put("wavBytes", exported.length())
                .put("soundingSamples", sounding).put("trace", trace).toString(2))
            Log.i("UiMusic", "PERFORMANCE_OK bpm=${session.bpm} counts=$counts clicks=$clicks wav=${exported.name}")
        } catch (failure: Throwable) {
            resultFile.writeText(JSONObject().put("ok", false).put("error", failure.toString())
                .put("clicks", clicks).put("trace", trace).toString(2))
            throw failure
        }
        // Do not restore/erase the score. The host relaunches and locates Load/Play after
        // instrumentation exits, since Android terminates the instrumented process.
    }

    @Test fun resumeSavedMusic(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("listenSeconds") != null)
        val context = instrumentation.targetContext
        context.startActivity(Intent().setClassName(context.packageName,
            "com.example.spatialdj.p170bd28c.platform.LaunchActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var found: LoopViewModel? = null
        withTimeout(45000) {
            while (found == null) {
                found = withContext(Dispatchers.Main) { find(LoopViewModel::class.java) }
                if (found == null) delay(250)
            }
        }
        vm = checkNotNull(found)
        await("audio ready", 45000) { vm.state.value.output == "左右空间音箱" && vm.state.value.status != null }
        input = withContext(Dispatchers.Main) { checkNotNull(find(PanelInput::class.java)) }
        engine = withContext(Dispatchers.Main) { field(vm, "engine") as LoopEngine }
        attachment = withContext(Dispatchers.Main) {
            @Suppress("UNCHECKED_CAST")
            val speakers = field(field(vm, "factory")!!, "speakers") as Pair<Entity, Entity>
            checkNotNull(speakers.first.getParent()!!.findEntity("FingerOnlyLoopPanel"))
        }
        tools(true); click("load"); click("load")
        await("load saved UI music") { !vm.state.value.busy && vm.state.value.notice == "已载入上次工程" }
        tools(false)
        if(engine.snapshot().mode==RecordingMode.FullSong) {
            click("part-${SongPart.Drop.ordinal}"); click("audition")
        } else click("play")
        check(engine.snapshot().running && engine.snapshot().lanes.all { it.count > 0 })
        Log.i("UiMusic", "RESUMED_SAVED_MUSIC")
        // Keep this explicitly requested runner alive for listening; the host can stop it.
        val seconds = InstrumentationRegistry.getArguments().getString("listenSeconds")?.toLong() ?: 600
        repeat(seconds.toInt()) { delay(1000); check(vm.state.value.error == null) }
    }
}
