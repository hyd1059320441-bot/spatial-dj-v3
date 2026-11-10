package com.example.spatialdj.p170bd28c

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.graphics.Paint
import android.media.AudioTrack
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.spatialdj.p170bd28c.data.audio.SessionFiles
import com.example.spatialdj.p170bd28c.data.audio.AutoScoreFiles
import com.example.spatialdj.p170bd28c.domain.audio.LoopEngine
import com.example.spatialdj.p170bd28c.domain.model.*
import com.example.spatialdj.p170bd28c.ui.loop.*
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hand.HandTrackingProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap
import java.io.File
import kotlin.math.abs

/** All inspection and generated input are instrumentation-only, never an exported app endpoint. */
class LiveLoopTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun field(value: Any,name: String): Any? =
        value.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(value)
    private fun <T> find(type: Class<T>): T? {
        val seen=Collections.newSetFromMap(IdentityHashMap<Any,Boolean>())
        fun scan(value: Any?,depth: Int): T? {
            if(value==null || depth>35 || !seen.add(value)) return null
            if(type.isInstance(value)) return type.cast(value)
            if(value is ViewModelStoreOwner) scan(value.viewModelStore,depth+1)?.let { return it }
            if(value is android.app.Activity) scan(value.window.decorView,depth+1)?.let { return it }
            if(value is android.view.ViewGroup) {
                for(i in 0 until value.childCount) scan(value.getChildAt(i),depth+1)?.let { return it }
            }
            if(value is android.util.SparseArray<*>) {
                for(i in 0 until value.size()) scan(value.valueAt(i),depth+1)?.let { return it }
                return null
            }
            if(value is Pair<*,*>) {
                scan(value.first,depth+1)?.let { return it }; scan(value.second,depth+1)?.let { return it }
                return null
            }
            if(value is Map<*,*>) { value.values.forEach { scan(it,depth+1)?.let { result->return result } }; return null }
            if(value is Iterable<*>) { value.forEach { scan(it,depth+1)?.let { result->return result } }; return null }
            if(value is Array<*>) { value.forEach { scan(it,depth+1)?.let { result->return result } }; return null }
            if(value is StateFlow<*>) return scan(value.value,depth+1)
            if(value is androidx.compose.runtime.State<*>) return scan(value.value,depth+1)
            var cls: Class<*>?=value.javaClass
            while(cls!=null && cls!=Any::class.java) {
                val name=cls.name
                if(name.startsWith("com.pico.") || name.startsWith("androidx.lifecycle.") ||
                    name.startsWith("androidx.activity.") || name.startsWith("androidx.fragment.") ||
                    name.startsWith("androidx.compose.") || name.startsWith("com.example.spatialdj.") ||
                    name.startsWith("android.view.") || name.startsWith("android.widget.")) {
                    cls.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !it.type.isPrimitive }.forEach { f ->
                        val child=runCatching { f.isAccessible=true; f.get(value) }.getOrNull()
                        scan(child,depth+1)?.let { return it }
                    }
                }
                cls=cls.superclass
            }
            return null
        }
        val monitor=ActivityLifecycleMonitorRegistry.getInstance()
        Stage.entries.flatMap { monitor.getActivitiesInStage(it) }.distinct().forEach { scan(it,0)?.let { result->return result } }
        Log.i("SpatialLoopTest","Probe type=${type.simpleName} objects=${seen.size}")
        return null
    }
    private suspend fun await(timeout: Long=15000,predicate: ()->Boolean) {
        withTimeout(timeout) { while(!predicate()) delay(50) }
    }
    @Test fun chineseLatinFontsAndEveryPanelLayout(): Unit = runBlocking {
        val context=instrumentation.targetContext
        context.startActivity(Intent().setClassName(context.packageName,
            "com.example.spatialdj.p170bd28c.platform.LaunchActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var vm: LoopViewModel?=null
        await(45000) { instrumentation.runOnMainSync { vm=find(LoopViewModel::class.java) }; vm!=null }
        val liveVm=checkNotNull(vm)
        await(45000) { liveVm.state.value.ready && liveVm.state.value.output=="左右空间音箱" }
        var liveInput: PanelInput?=null
        await { instrumentation.runOnMainSync { liveInput=find(PanelInput::class.java) }; liveInput!=null }
        val input=checkNotNull(liveInput)
        val engine=withContext(Dispatchers.Main) { field(liveVm,"engine") as LoopEngine }
        @Suppress("UNCHECKED_CAST")
        val ui=withContext(Dispatchers.Main) { field(liveVm,"mutable") as MutableStateFlow<LoopUiState> }
        val original=engine.exportSession()
        val selected=liveVm.state.value.selected
        val report=JSONArray()
        val paints=listOf(R.font.loop_sans_sc_regular,R.font.loop_sans_sc_semibold).map { resource ->
            Paint().apply { typeface=checkNotNull(ResourcesCompat.getFont(context,resource)); textSize=40f }
        }
        val chars=mutableSetOf<Char>()
        fun controls(): Map<String,FingertipGate.Surface> {
            @Suppress("UNCHECKED_CAST")
            return (field(input,"controls") as Map<String,Any>)
                .mapValues { field(it.value,"surface") as FingertipGate.Surface }
        }
        suspend fun tap(id: String,fraction: Float=.5f) {
            await { withMain { controls().containsKey(id) && input.gate.active } }
            withContext(Dispatchers.Main) {
                val face=checkNotNull(controls()[id])
                val r=face.rect
                val x=r.x+face.insetPixels+(r.width-2*face.insetPixels)*fraction
                val y=r.y+r.height/2
                fun tip(z: Float)=FingertipGate.Tip(1,
                    (x/input.gate.widthPixels-.5f)*input.gate.widthMeters,
                    (.5f-y/input.gate.heightPixels)*input.gate.heightMeters,z)
                val now=SystemClock.uptimeMillis()
                input.hands(now,listOf(tip(.05f)))
                input.hands(now,listOf(tip(.005f)))
                input.hands(now,listOf(tip(.04f)))
            }
            delay(180)
        }
        suspend fun audit(page: String,expect: String,allowNoticeEllipsis: Boolean=false) {
            await { withMain { input.textMeasurements().containsKey(expect) } }
            delay(150)
            withContext(Dispatchers.Main) {
                val measured=input.textMeasurements()
                val faces=controls()
                assertTrue("No actual text layout for $page",measured.size>=28)
                measured.filterValues { it.layout.hasVisualOverflow }.forEach { (id,sample) ->
                    Log.w("SpatialLoopTest","TextOverflow page=$page id=$id text=${sample.layout.layoutInput.text} " +
                        "size=${sample.layout.size} paragraphHeight=${sample.layout.multiParagraph.height} " +
                        "width=${sample.layout.didOverflowWidth} height=${sample.layout.didOverflowHeight}")
                }
                measured.forEach { (id,sample) ->
                    val layout=sample.layout; val r=sample.rect
                    val text=layout.layoutInput.text.text
                    chars.addAll(text.filterNot(Char::isWhitespace).toList())
                    assertEquals("$page/$id family",LoopFontFamily,layout.layoutInput.style.fontFamily)
                    assertEquals("$page/$id fake weight",FontSynthesis.None,layout.layoutInput.style.fontSynthesis)
                    assertFalse("$page/$id replacement glyph",text.contains('\uFFFD'))
                    for(paint in paints) for(c in text.filterNot(Char::isWhitespace)) {
                        assertTrue("$page/$id missing glyph '$c'",paint.hasGlyph(c.toString()))
                    }
                    if(id!="notice" || !allowNoticeEllipsis) {
                        assertFalse("$page/$id clipped '$text' size=${layout.size}",layout.hasVisualOverflow)
                        assertFalse("$page/$id unexpectedly ellipsized",layout.isLineEllipsized(layout.lineCount-1))
                    } else assertTrue("Long errors must end with ellipsis",layout.isLineEllipsized(layout.lineCount-1))
                    assertTrue("$page/$id outside panel $r",r.x>=0 && r.y>=0 &&
                        r.x+r.width<=input.gate.widthPixels+1 && r.y+r.height<=input.gate.heightPixels+1)
                    val controlId=id.substringBeforeLast('.')
                    faces[controlId]?.takeUnless { it.slider }?.let { control ->
                        val c=control.rect
                        assertTrue("$page/$id outside button",r.x>=c.x && r.y>=c.y &&
                            r.x+r.width<=c.x+c.width+1 && r.y+r.height<=c.y+c.height+1)
                        // Transport labels are centered together with their vector icon.
                        if(controlId !in setOf("play","record","auto")) {
                            assertEquals("$page/$id horizontal center",c.x+c.width/2,r.x+r.width/2,2f)
                        }
                    }
                    if(id.endsWith(".value")) assertEquals(TextAlign.End,layout.layoutInput.style.textAlign)
                }
                fun baseline(id: String)=measured.getValue(id).let { it.rect.y+it.layout.firstBaseline }
                for(lane in 1..4) if(faces.containsKey("lane0")) {
                    assertEquals("Track title baselines",baseline("lane0.label"),baseline("lane$lane.label"),1f)
                    assertEquals("Track detail baselines",baseline("lane0.detail"),baseline("lane$lane.detail"),1f)
                }
                val lane=liveVm.state.value.selected
                assertEquals("Slider label baseline",baseline("vol$lane.label"),baseline("master.label"),1f)
                assertEquals("Slider number baseline",baseline("vol$lane.value"),baseline("master.value"),1f)
                assertEquals("Slider track alignment",faces.getValue("vol$lane").rect.y,faces.getValue("master").rect.y,1f)
                faces.forEach { (id,surface) ->
                    val r=surface.rect
                    assertTrue("$page/$id small touch target",r.height/input.gate.heightPixels*input.gate.heightMeters>=.035f)
                    faces.filterKeys { it>id }.forEach { (other,face) ->
                        val b=face.rect
                        val overlapX=minOf(r.x+r.width,b.x+b.width)-maxOf(r.x,b.x)
                        val overlapY=minOf(r.y+r.height,b.y+b.height)-maxOf(r.y,b.y)
                        assertFalse("$page overlapping controls $id/$other",overlapX>1 && overlapY>1)
                    }
                }
                report.put(JSONObject().put("page",page).put("textCount",measured.size)
                    .put("controlCount",faces.size).put("unexpectedOverflow",0)
                    .put("noticeEllipsis",allowNoticeEllipsis).put("alignment","PASS"))
                Log.i("SpatialLoopTest","TypographyPASS page=$page texts=${measured.size} controls=${faces.size}")
            }
        }
        try {
            withContext(Dispatchers.Main) { engine.newSession(); liveVm.action(LoopAction.Select(0)) }
            audit("initial","pad-0-0.label")
            val dense=LoopSession(bpm=180,bars=4,master=1f,lanes=Lane.entries.map { lane ->
                LaneSession(events=List(256) { NoteEvent((it%64)/4.0,it%lane.pads.size) },volume=1f)
            })
            withContext(Dispatchers.Main) { engine.load(dense) }
            for(lane in 0..4) {
                tap("lane$lane")
                val last=Lane.entries[lane].pads.lastIndex
                audit("lane-$lane-256notes","pad-$lane-$last.label")
            }
            tap("vol4",0f); tap("master",0f); audit("volume-zero","vol4.value")
            tap("vol4",1f); tap("master",1f); audit("volume-100","master.value")
            tap("mute4"); audit("muted","mute4.label"); tap("mute4")
            tap("tools"); audit("tools-180bpm-4bars","export.label")
            tap("mode-1"); audit("tools-full-song","mode-1.label")
            tap("tools")
            for(part in SongPart.entries) {
                tap("part-${part.ordinal}")
                audit("full-${part.title}","part-${part.ordinal}.label")
            }
            tap("tools"); audit("full-song-tools","clear.label")
            tap("mode-0")
            assertEquals(RecordingMode.DropOnly,engine.snapshot().mode)
            withContext(Dispatchers.Main) { engine.load(LoopSession(bpm=60,bars=1,quantize=false)) }
            audit("tools-60bpm-1bar-free","tempo")
            tap("tools")
            // Destructive confirmation is displayed without executing a clear.
            tap("clear"); audit("confirmation","notice")
            tap("record")
            await { (liveVm.state.value.status?.countIn ?: 0) > 0 }
            audit("count-in","record.label")
            await(15000) { liveVm.state.value.status?.recording==4 }
            audit("recording","record.label")
            tap("record")
            tap("record"); audit("armed-next-circle","record.label"); tap("record")
            tap("play"); audit("stopped","play.label")
            withContext(Dispatchers.Main) {
                ui.value=ui.value.copy(error="音频错误：文件加载失败，请重新载入工程。".repeat(20))
            }
            audit("long-error-two-lines","notice",allowNoticeEllipsis=true)
            withContext(Dispatchers.Main) { ui.value=ui.value.copy(error=null,notice="") }
            val probe="节拍俱乐部 底鼓 拍手 贝斯 旋律 和弦 A1 C2 G4 Am WAV BPM 0123456789 + - % / · …"
            for(paint in paints) for(c in probe.filterNot(Char::isWhitespace)) assertTrue("Probe '$c'",paint.hasGlyph(c.toString()))
            File(context.filesDir,"typography-report.json").writeText(JSONObject().put("ok",true)
                .put("uniqueCharacters",chars.size).put("fontWeights",JSONArray(listOf(400,600)))
                .put("pages",report).toString(2))
            Log.i("SpatialLoopTest","AllTypographyPASS pages=${report.length()} glyphCharacters=${chars.size}")
        } finally {
            withContext(Dispatchers.Main) {
                ui.value=ui.value.copy(error=null,notice="")
                liveVm.foreground(true); engine.load(original); liveVm.action(LoopAction.Select(selected))
            }
        }
    }

    private fun <T> withMain(block: ()->T): T {
        var value: T?=null
        instrumentation.runOnMainSync { value=block() }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun loopRoot(entity: Entity): Entity {
        var current=entity
        while(current.getName()!="LoopV3Root") current=checkNotNull(current.getParent())
        return current
    }

    @Test fun shallowContactMovesBetweenLiveButtonsAndPageReset(): Unit = runBlocking {
        val context=instrumentation.targetContext
        context.startActivity(Intent().setClassName(context.packageName,
            "com.example.spatialdj.p170bd28c.platform.LaunchActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var vm: LoopViewModel?=null
        await(45000) { withMain { vm=find(LoopViewModel::class.java); vm!=null } }
        val live=checkNotNull(vm)
        await(45000) { live.state.value.ready && live.state.value.output=="左右空间音箱" }
        val input=withMain { checkNotNull(find(PanelInput::class.java)) }
        val scene=withMain { checkNotNull(find(LoopScene::class.java)) }
        val engine=withMain { field(live,"engine") as LoopEngine }
        val original=engine.exportSession()
        val selected=live.state.value.selected
        @Suppress("UNCHECKED_CAST")
        fun faces()=(field(input,"controls") as Map<String,Any>)
            .mapValues { field(it.value,"surface") as FingertipGate.Surface }
        fun contact(id: String,z: Float,finger: Int) {
            val r=faces().getValue(id).rect
            input.hands(SystemClock.uptimeMillis(),listOf(FingertipGate.Tip(finger,
                ((r.x+r.width/2)/input.gate.widthPixels-.5f)*input.gate.widthMeters,
                (.5f-(r.y+r.height/2)/input.gate.heightPixels)*input.gate.heightMeters,z)))
        }
        try {
            withMain { engine.newSession(); live.action(LoopAction.Select(0)); input.reset() }
            await { withMain { input.gate.active && faces().containsKey("lane0") } }
            var moves=0
            for(finger in listOf(1,6)) {
                repeat(20) {
                    withMain {
                        contact("lane0",.035f,finger); contact("lane0",.007f,finger)
                        assertEquals(0,live.state.value.selected)
                        for(lane in 1..4) {
                            contact("lane$lane",.018f,finger)
                            contact("lane$lane",.007f,finger)
                            assertEquals("Cross-control/page-reset contact lost",lane,live.state.value.selected)
                            moves++
                        }
                        contact("lane4",.035f,finger)
                    }
                    delay(25)
                }
            }
            withMain {
                contact("tools",.035f,1); contact("tools",.007f,1)
                val before=engine.snapshot().metronome
                contact("metro",.018f,1); contact("metro",.007f,1)
                assertEquals("Tools reset blocked another control",!before,engine.snapshot().metronome)
                contact("metro",.035f,1)
            }
            await { withMain { faces().containsKey("mode-1") } }
            withMain {
                contact("tools",.035f,6); contact("tools",.007f,6); contact("tools",.035f,6)
                val root=checkNotNull(field(scene,"root") as Entity?)
                val mount=root.findEntity("SlopedConsole")!!
                for(name in listOf("ConsoleBody","ConsoleOutline")) {
                    assertTrue("$name intersects native control plane",
                        root.findEntity(name)!!.getVisualBounds(mount,false).max.z<-.04f)
                }
            }
            File(context.filesDir,"shallow-contact-report.json").writeText(JSONObject()
                .put("ok",true).put("crossButtonContacts",moves).put("bothIndexFingers",true)
                .put("pageResetNavigation",true).put("hoverLiftMeters",.018)
                .put("consoleGeometryBehindMeters",.04).put("physicalTouchVerified",false).toString(2))
        } finally {
            withMain { engine.load(original); live.action(LoopAction.Select(selected)); input.reset() }
        }
    }

    @Test fun defaultFullSongFourBarsAndReliableToolsNavigation(): Unit = runBlocking {
        val context=instrumentation.targetContext
        context.startActivity(Intent().setClassName(context.packageName,
            "com.example.spatialdj.p170bd28c.platform.LaunchActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var found: LoopViewModel?=null
        await(45000) { withMain { found=find(LoopViewModel::class.java); found!=null } }
        val vm=checkNotNull(found)
        await(45000) { vm.state.value.ready && vm.state.value.output=="左右空间音箱" }
        val input=withMain { checkNotNull(find(PanelInput::class.java)) }
        val engine=withMain { field(vm,"engine") as LoopEngine }
        @Suppress("UNCHECKED_CAST")
        val ui=withMain { field(vm,"mutable") as MutableStateFlow<LoopUiState> }
        val original=engine.exportSession()
        var toggles=0
        fun controls(): Map<String,FingertipGate.Surface> {
            @Suppress("UNCHECKED_CAST")
            return (field(input,"controls") as Map<String,Any>)
                .mapValues { field(it.value,"surface") as FingertipGate.Surface }
        }
        suspend fun tap(id: String,finger: Int=1) {
            await { withMain { input.gate.active && controls().containsKey(id) } }
            withContext(Dispatchers.Main) {
                val r=controls().getValue(id).rect
                fun tip(z: Float)=FingertipGate.Tip(finger,
                    ((r.x+r.width/2)/input.gate.widthPixels-.5f)*input.gate.widthMeters,
                    (.5f-(r.y+r.height/2)/input.gate.heightPixels)*input.gate.heightMeters,z)
                val now=SystemClock.uptimeMillis()
                input.hands(now,listOf(tip(.04f))); input.hands(now,listOf(tip(.007f)))
                input.hands(now,listOf(tip(.04f)))
            }
            delay(80)
        }
        suspend fun tools(open: Boolean,finger: Int=1) {
            tap("tools",finger)
            await(3000) { withMain { controls().containsKey("mode-1")==open } }
            toggles++
        }
        try {
            withMain { engine.newSession(); vm.action(LoopAction.Select(0)); input.reset() }
            await { vm.state.value.status?.mode==RecordingMode.FullSong && vm.state.value.status?.bars==4 }
            assertEquals(SongPart.Intro,engine.snapshot().part)
            assertTrue(engine.exportSession().sections.all { it.bars==4 })
            if(withMain { controls().containsKey("mode-1") }) tools(false)
            for(i in 0 until 20) {
                tap("lane${i%5}")
                tools(true,if(i%2==0) 1 else 6)
                assertTrue(withMain { controls().containsKey("undo") && controls().containsKey("clear") })
                tools(false,if(i%2==0) 6 else 1)
                assertTrue(withMain { controls().containsKey("part-0") })
                // These same IDs move/recreate between tools and performance layouts.
                assertTrue(withMain { controls().containsKey("pad-${i%5}-0") })
            }
            for(busy in listOf(true,false)) {
                withMain { ui.value=ui.value.copy(busy=busy,error=if(busy) null else "测试错误") }
                await { withMain { field(input,"editingEnabled")==false } }
                tools(true); tools(false,6)
                val triggers=engine.snapshot().triggers
                tap("pad-${vm.state.value.selected}-0")
                assertEquals("Editing leaked through busy/error navigation",triggers,engine.snapshot().triggers)
                withMain { ui.value=ui.value.copy(busy=false,error=null) }
                await { withMain { field(input,"editingEnabled")==true } }
            }
            tools(true); tap("mode-0"); assertEquals(RecordingMode.DropOnly,engine.snapshot().mode)
            tap("mode-1"); assertEquals(RecordingMode.FullSong,engine.snapshot().mode); tools(false)
            tap("auto")
            await { engine.snapshot().auto?.active==true }
            tools(true)
            assertTrue("Auto state reset closed tools",withMain { controls().containsKey("mode-1") })
            tools(false,6)
            tap("auto"); await { engine.snapshot().auto==null }
            assertEquals(RecordingMode.FullSong,engine.snapshot().mode); assertEquals(4,engine.snapshot().bars)
            File(context.filesDir,"tools-navigation-report.json").writeText(JSONObject().put("ok",true)
                .put("mode","FullSong").put("defaultBarsPerPart",4).put("defaultPart","Intro")
                .put("toolPageTransitions",toggles).put("bothHands",true)
                .put("toolsUsableDuringBusyErrorAndAuto",true).put("editsBlockedDuringBusyError",true)
                .put("modeSwitchAndAutoRestorePreserveDefaults",true)
                .put("input","instrumentation contact through measured control faces")
                .put("physicalFingertipLatencyVerified",false).toString(2))
            Log.i("SpatialLoopTest","ToolsNavigationPASS transitions=$toggles defaults=FullSong/4bars " +
                "bothHands=true busy/error/autoNavigation=true")
        } finally {
            withMain { ui.value=ui.value.copy(busy=false,error=null); vm.foreground(true); engine.load(original) }
        }
    }

    @Test fun clubRoomLitHumansBeatAnimationAndSceneDisposal(): Unit = runBlocking {
        val context=instrumentation.targetContext
        fun launch() = context.startActivity(Intent().setClassName(context.packageName,
            "com.example.spatialdj.p170bd28c.platform.LaunchActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        launch()
        var found: LoopViewModel?=null
        await(45000) { withMain { found=find(LoopViewModel::class.java); found!=null } }
        val vm=checkNotNull(found)
        await(45000) { vm.state.value.ready && vm.state.value.output=="左右空间音箱" }
        val scene=withMain { checkNotNull(find(LoopScene::class.java)) }
        val room=withMain { field(scene,"room") as ClubRoom }
        val engine=withMain { field(vm,"engine") as LoopEngine }
        val original=engine.exportSession()
        val root=withMain { field(room,"root") as Entity }
        fun named(name: String)=checkNotNull(root.findEntity(name))
        val moving=mutableSetOf<String>()
        val prior=mutableMapOf<String,Vector3>()
        val intensities=mutableListOf<Float>()
        val landings=List(2) { mutableListOf<Vector3>() }
        val report=JSONObject()
        fun inspect() {
            val floor=named("DanceFloor").getVisualBounds(root,false)
            for(i in 0..1) {
                val cone=named("LightShaft$i")
                val end=cone.convertPositionTo(Vector3(0f,0f,-1f),root)
                assertEquals("Shaft misses dance floor",.08f,end.y,.003f)
                assertTrue("Shaft outside dance floor",abs(end.x)<1.5f && end.z in -4.4f.. -2.4f)
                landings[i].add(end)
                val haze=cone.components.get<ModelComponent>()!!.materials[0] as com.pico.spatial.core.ecs.resource.UnlitMaterial
                assertFalse("Light shaft writes opaque depth",haze.getDepthWrite())
                assertTrue(haze.getDepthTest())
                assertTrue("Haze obscures dancers",haze.getOpacity()<=.051f)
            }
            for(i in 0..3) {
                val rig=named("Dancer$i")
                for(side in listOf(-1,1)) {
                    val shoulder=named("Dancer${i}Shoulder$side").components.get<TransformComponent>()!!.eulerAngles
                    assertTrue("Dancer$i arms still flail",abs(shoulder.roll)<30 && abs(shoulder.pitch)<=31)
                    val thigh=named("Dancer${i}Thigh$side")
                    val shin=named("Dancer${i}Shin$side")
                    val knee=named("Dancer${i}Knee$side").components.get<TransformComponent>()!!.position
                    val ankle=named("Dancer${i}Shoe$side").components.get<TransformComponent>()!!.position
                    val thighEnd=thigh.convertPositionTo(Vector3(0f,-.5f,0f),rig)
                    val shinStart=shin.convertPositionTo(Vector3(0f,.5f,0f),rig)
                    val shinEnd=shin.convertPositionTo(Vector3(0f,-.5f,0f),rig)
                    fun close(a: Vector3,b: Vector3) {
                        assertEquals(a.x,b.x,.0002f); assertEquals(a.y,b.y,.0002f); assertEquals(a.z,b.z,.0002f)
                    }
                    // Shared cube mesh is unit height; entity scale encodes the .36m bone.
                    close(knee,thighEnd); close(knee,shinStart); close(ankle,shinEnd)
                    val shoe=named("Dancer${i}Sole$side").getVisualBounds(root,false)
                    assertTrue("Dancer$i shoe penetrated stage ${shoe.min.y}/${floor.max.y}",
                        shoe.min.y>=floor.max.y-.002f)
                    for(name in listOf("Dancer${i}Knee$side","Dancer${i}Hand$side","Dancer${i}Shoe$side")) {
                        val p=named(name).convertPositionTo(Vector3.ZERO,rig)
                        prior[name]?.let { old ->
                            if(abs(p.x-old.x)+abs(p.y-old.y)+abs(p.z-old.z)>.006f) moving.add(name)
                        }
                        prior[name]=p
                    }
                }
            }
            intensities.add(named("DanceSpot0").components.get<SpotLightComponent>()!!.intensity)
        }
        try {
            withMain {
                assertEquals(4,root.getChildren().count { it.getName().matches(Regex("Dancer[0-3]")) })
                assertEquals(4,root.getChildren().count { it.components.has<DirectionalLightComponent>() ||
                    it.components.has<SpotLightComponent>() || it.components.has<PointLightComponent>() })
                assertFalse(named("DanceSpot0").components.get<SpotLightComponent>()!!.castsShadowEnabled)
                assertFalse(named("DanceSpot1").components.get<SpotLightComponent>()!!.castsShadowEnabled)
                assertEquals(4f,root.components.get<StageEnvironmentLightingComponent>()!!.intensityExponent,.01f)
                val fillDirections=listOf("ClubKeyLight","ClubSideFill0").map { name ->
                    val entity=named(name)
                    val fill=entity.components.get<DirectionalLightComponent>()!!
                    assertEquals(800f,fill.intensity,.01f)
                    assertFalse(fill.castsShadowEnabled)
                    assertEquals(1f,fill.color.red,.001f)
                    assertTrue("Fill must stay neutral",fill.color.blue>=.95f)
                    val direction=entity.convertPositionTo(Vector3(0f,0f,-1f),root) -
                        entity.convertPositionTo(Vector3.ZERO,root)
                    assertTrue("Side fill must light downwards",direction.y< -.4f)
                    direction
                }
                for(normal in listOf(Vector3(1f,0f,0f),Vector3(-1f,0f,0f),
                    Vector3(0f,0f,1f),Vector3(0f,0f,-1f),Vector3(0f,1f,0f))) {
                    assertTrue("A wall/floor has no direct fill: $normal",fillDirections.any {
                        -(normal.x*it.x+normal.y*it.y+normal.z*it.z)>.4f
                    })
                }
                for(i in 0..3) {
                    val skin=named("Dancer${i}Face").components.get<ModelComponent>()!!.materials[0] as PhysicallyBasedMaterial
                    val expected=com.pico.spatial.core.math.Color4.fromSRGBHex("0xf9f6f0ff")
                    val actual=skin.getBaseColor()
                    assertEquals("Dancer$i white skin red",expected.red,actual.red,.001f)
                    assertEquals("Dancer$i white skin green",expected.green,actual.green,.001f)
                    assertEquals("Dancer$i white skin blue",expected.blue,actual.blue,.001f)
                }
                for(i in 0..1) {
                    val light=named("DanceSpot$i").components.get<SpotLightComponent>()!!.color
                    assertTrue("Shared light strongly tinted",light.red-light.blue<=.05f && light.green-light.blue<=.05f)
                }
                assertNull(root.findEntity("DanceSpot2")); assertNull(root.findEntity("DanceSpot3"))
                for(name in listOf("RoomSideWall-1","RoomBackWall","DoorLeaf","BoothSeat-1","BoothSeat1")) {
                    val mat=named(name).components.get<ModelComponent>()!!.materials[0] as PhysicallyBasedMaterial
                    val c=mat.getBaseColor()
                    val prior=com.pico.spatial.core.math.Color4.fromSRGBHex("0x111a23ff")
                    assertTrue("$name finish still nearly black",minOf(c.red,c.green,c.blue)>prior.red*2)
                }
                val ceiling=named("RoomCeiling").getVisualBounds(root,false)
                assertEquals(2.85f,ceiling.min.y,.001f)
                assertTrue("Roof does not cover room",ceiling.min.x< -3.4f && ceiling.max.x>3.4f &&
                    ceiling.min.z< -5.4f && ceiling.max.z>1.8f)
                for(name in listOf("CeilingBeam0","CeilingBeam5","DoorLeaf","DoorHandle","ExitSignBacking",
                    "LoungeBooth-1","LoungeBooth1","LoungeTable-1","ClubBar","BarBottle0",
                    "PAStack-1","PAStack1","LightShaft0","LightShaft1",
                    "CeilingDiffusePanel0","CeilingDiffusePanel1","CeilingDiffusePanel2"))
                    assertTrue("$name missing",named(name).valid)
                for(i in listOf(0,2)) {
                    assertTrue(ClubCast.people[i].female)
                    assertTrue(named(if(i==0) "Dancer0Ponytail" else "Dancer2LongHair-1").valid)
                }
                for(side in listOf(-1,1)) {
                    val stack=named("PAStack$side")
                    val forward=stack.convertPositionTo(Vector3(0f,0f,1f),root)
                    val at=stack.convertPositionTo(Vector3.ZERO,root)
                    assertTrue("PA points out of dance floor",abs(forward.x)<abs(at.x) && forward.z>at.z)
                    assertEquals(0f,named("PASubwoofer$side").getVisualBounds(root,false).min.y,.002f)
                }
                for(name in listOf("RoomFloor","RoomCeiling","RoomBackWall","RoomFrontWall","RoomSideWall-1",
                    "RoomSideWall1","Dancer0Face","Dancer1Jacket","Dancer2Thigh-1")) {
                    assertTrue("$name must respond to actual light",
                        named(name).components.get<ModelComponent>()!!.materials[0] is PhysicallyBasedMaterial)
                }
                assertEquals(named("Dancer0Thigh-1").components.get<ModelComponent>()!!.mesh,
                    named("Dancer3Shin1").components.get<ModelComponent>()!!.mesh)
                val outer=field(scene,"root") as Entity
                assertNull(outer.findEntity("CatEar0--0.078"))
                val mount=outer.findEntity("SlopedConsole")!!
                assertEquals(1.1f,mount.components.get<TransformComponent>()!!.position.y,.001f)
                assertEquals(1.1f,vm.state.value.height,.001f)
                fun children(entity: Entity): List<Entity> = listOf(entity)+entity.getChildren().flatMap(::children)
                assertTrue("Desk audio/links still exist",children(outer).none {
                    it.getName().startsWith("Monitor") || it.getName().matches(Regex("Speaker[0-9].*"))
                })
                val panelWidth=(field(scene,"width") as Float)
                for(i in 0..1) {
                    val ornament=outer.findEntity("ConsoleOrnament$i")!!
                    val bounds=ornament.getVisualBounds(mount,true)
                    assertTrue("Ornament overlaps pad faces",if(i==0) bounds.max.x< -panelWidth/2-.025f
                        else bounds.min.x>panelWidth/2+.025f)
                    val plate=outer.findEntity("OrnamentPlate$i")!!.getVisualBounds(mount,false)
                    assertTrue("Ornament plate protrudes through panel",plate.max.z< -.011f)
                }
                val sources=checkNotNull(scene.speakers)
                assertEquals(named("PAMain-1"),sources.first)
                assertEquals(named("PAMain1"),sources.second)
                @Suppress("UNCHECKED_CAST")
                val bound=field(checkNotNull(field(vm,"factory")),"speakers") as Pair<Entity,Entity>
                assertEquals(sources,bound)
                val panel=outer.findEntity("FingerOnlyLoopPanel")!!
                assertTrue("Wall intersects console",panel.convertPositionTo(Vector3.ZERO,root).z> -1f)
                val info=context.packageManager.getActivityInfo(android.content.ComponentName(context,
                    "com.example.spatialdj.p170bd28c.platform.LaunchActivity"),android.content.pm.PackageManager.GET_META_DATA)
                assertEquals("Must launch in full virtual room",3,info.metaData.getInt("pico.spatial.stage.style"))
                engine.load(LoopSession(bpm=132,bars=2,lanes=List(5) { lane ->
                    LaneSession(listOf(NoteEvent(0.0,0),NoteEvent(1.0,1),NoteEvent(2.0,2),NoteEvent(3.0,3)),
                        volume=if(lane==0) .8f else .5f)
                }))
                engine.play()
            }
            val before=withMain { field(room,"frames") as Long }
            repeat(30) { delay(100); withMain { inspect() } }
            for(points in landings) {
                assertTrue("Shared spotlight stayed fixed",points.maxOf { it.x }-points.minOf { it.x }>.4f)
                assertTrue("Shared spotlight did not sweep between rows",points.maxOf { it.z }-points.minOf { it.z }>.3f)
            }
            assertEquals("Every knee, hand and shoe must articulate",24,moving.size)
            assertTrue("Beat-responsive lights did not change",intensities.max()-intensities.min()>150)
            assertTrue("Actual ECS hook did not advance",withMain { (field(room,"frames") as Long)-before>60 })
            assertTrue("Audio stopped during room animation",engine.snapshot().running)
            assertTrue("Dance clock drifted from audio",withMain {
                abs((field(room,"lastBeat") as Double)-engine.snapshot().elapsedBeats)<.35
            })
            val activeLight=intensities.average()
            withMain { engine.stop() }
            delay(1600)
            val idleLight=withMain { named("DanceSpot0").components.get<SpotLightComponent>()!!.intensity }
            assertTrue("Idle lighting failed to settle",idleLight<activeLight-100)
            withMain { engine.load(original); vm.action(LoopAction.Exit) }
            await(15000) { withMain { field(room,"closed")==true } }
            val stopped=withMain { field(room,"frames") }
            delay(150)
            withMain {
                assertEquals(stopped,field(room,"frames"))
                assertFalse(root.valid)
                assertTrue((field(room,"resources") as List<*>).isEmpty())
                assertNull(field(vm,"worker"))
            }
            launch()
            var replacement: LoopScene?=null
            await(45000) { withMain { replacement=find(LoopScene::class.java); replacement!=null && replacement!==scene } }
            val newRoom=withMain { field(checkNotNull(replacement),"room") as ClubRoom }
            await { withMain { (field(newRoom,"frames") as Long)>3 } }
            report.put("ok",true).put("roomEnclosure",6).put("fullStage",true).put("dancers",4)
                .put("realLights",4).put("directionalFills",2).put("movingSpots",2)
                .put("sideFillLux",800).put("opposingFillsReachAllWallsAndFloor",true)
                .put("shadowMaps",0).put("movingJointsAndFeet",moving.size)
                .put("femaleCharacters",2).put("danceStyles",4).put("maximumShoulderSpreadDegrees",30)
                .put("environmentFillLux",800).put("ambientEV",4).put("finishedCeilingHeight",2.85)
                .put("allFourSkinMaterials","F9F6F0").put("neutralSharedSpotlights",true)
                .put("defaultConsoleHeightMeters",1.1)
                .put("lighterCornerFurnitureAndWalls",true)
                .put("entranceAndExitPictogram",true).put("visibleLightShafts",2)
                .put("shaftsEndOnDanceFloorAndDoNotWriteDepth",true).put("barAndLounge",true)
                .put("paFloorSupportAndInwardAim",true).put("audioBoundToRoomPA",true)
                .put("deskAudioAndLinksRemoved",true).put("ornamentsOutsidePadFaces",true)
                .put("sharedSpotsSweepBothAxes",true).put("individualSpotAssignmentRemoved",true)
                .put("actualIkEndpointsAndFloorClearance",true).put("sampleClockSync",true)
                .put("beatLightMin",intensities.min()).put("beatLightMax",intensities.max())
                .put("idleLight",idleLight).put("sceneExitReleasesAndReopenWorks",true)
                .put("fingertipVerification","deferred by user")
            File(context.filesDir,"club-room-report.json").writeText(report.toString(2))
            Log.i("SpatialLoopTest","ClubRoomPASS enclosure=6 full=true dancers=4 moving=24 lights=4 " +
                "sharedSweeps=true height=1.10 roomPA=true ornamentsClear=true ik/floor/disposal/reopen=true")
        } finally {
            if(withMain { field(room,"closed")!=true }) withMain { engine.load(original) }
        }
    }

    @Test fun manualDropAndFullArrangementRecordingStoragePlaybackAndExport(): Unit = runBlocking {
        val context=instrumentation.targetContext
        context.startActivity(Intent().setClassName(context.packageName,
            "com.example.spatialdj.p170bd28c.platform.LaunchActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var found: LoopViewModel?=null
        await(45000) { withMain { found=find(LoopViewModel::class.java); found!=null } }
        val vm=checkNotNull(found)
        await(45000) { vm.state.value.ready && vm.state.value.output=="左右空间音箱" }
        val input=withMain { checkNotNull(find(PanelInput::class.java)) }
        val engine=withMain { field(vm,"engine") as LoopEngine }
        val original=engine.exportSession()
        val saved=File(context.filesDir,"saved.loop")
        val savedBytes=saved.takeIf(File::exists)?.readBytes()
        fun controls(): Map<String,FingertipGate.Surface> {
            @Suppress("UNCHECKED_CAST")
            return (field(input,"controls") as Map<String,Any>)
                .mapValues { field(it.value,"surface") as FingertipGate.Surface }
        }
        suspend fun tap(id: String) {
            await { withMain { input.gate.active && controls().containsKey(id) } }
            withContext(Dispatchers.Main) {
                val r=controls().getValue(id).rect
                fun point(z: Float)=FingertipGate.Tip(1,
                    ((r.x+r.width/2)/input.gate.widthPixels-.5f)*input.gate.widthMeters,
                    (.5f-(r.y+r.height/2)/input.gate.heightPixels)*input.gate.heightMeters,z)
                val now=SystemClock.uptimeMillis()
                input.hands(now,listOf(point(.05f)))
                input.hands(now,listOf(point(.005f)))
                input.hands(now,listOf(point(.04f)))
            }
            delay(180)
        }
        val recorded=JSONArray()
        try {
            withContext(Dispatchers.Main) {
                val longPart=LoopSession(bpm=180,bars=4).withMode(RecordingMode.FullSong).withSection(
                    SongSection(SongPart.Intro,4,List(5) {
                        LaneSession(if(it==0) listOf(NoteEvent(15.75,0)) else emptyList())
                    }))
                engine.load(longPart); vm.action(LoopAction.Select(0))
            }
            if(!withMain { controls().containsKey("mode-1") }) tap("tools")
            tap("clear"); tap("clear"); tap("bars1"); tap("undo")
            assertEquals(1,engine.snapshot().bars)
            assertEquals(0,engine.snapshot().lanes[0].count)
            assertFalse(engine.snapshot().lanes[0].canUndo)
            engine.exportSession().validate()
            withContext(Dispatchers.Main) {
                engine.load(LoopSession(bpm=180,bars=1)); vm.action(LoopAction.Select(0))
            }
            // Start on the performance page regardless of earlier tests' tray state.
            if(withMain { controls().containsKey("mode-1") }) tap("tools")
            tap("tools"); tap("mode-1"); tap("tools")
            assertEquals(RecordingMode.FullSong,engine.snapshot().mode)
            for(part in SongPart.entries) {
                tap("part-${part.ordinal}")
                assertEquals(part,engine.snapshot().part)
                tap("record")
                await(10000) { engine.snapshot().recording==0 }
                tap("pad-0-${part.ordinal}")
                await(10000) { engine.snapshot().recording==null && engine.snapshot().armed==null }
                assertEquals(1,engine.exportSession().section(part).lanes[0].events.size)
                recorded.put(JSONObject().put("part",part.title).put("notes",1))
            }
            tap("play") // Stop the last part's recording loop.
            val project=engine.exportSession()
            tap("tools")
            tap("clear"); tap("clear")
            assertEquals(0,engine.snapshot().lanes[0].count)
            tap("undo"); assertEquals(1,engine.snapshot().lanes[0].count)
            assertEquals(project,engine.exportSession())
            tap("save"); await { !vm.state.value.busy }
            assertEquals(project,SessionFiles.read(saved))
            tap("mode-0")
            assertEquals(SongPart.Drop,engine.snapshot().part)
            assertEquals(1,engine.snapshot().lanes[0].count)
            tap("mode-1"); assertEquals(project.sections,engine.exportSession().sections)
            tap("load"); tap("load"); await { !vm.state.value.busy }
            assertEquals(project,engine.exportSession())
            tap("export"); await(60000) { !vm.state.value.busy }
            assertTrue(vm.state.value.notice.startsWith("已导出全曲"))
            val wav=context.getExternalFilesDir("exports")!!.listFiles()!!.maxBy { it.lastModified() }
            val expectedFrames=kotlin.math.ceil(20*60.0/180*48000).toInt()
            assertEquals(44L+expectedFrames*4L,wav.length())
            val header=wav.inputStream().use { stream -> ByteArray(44).also { stream.read(it) } }
            assertEquals("RIFF",header.copyOfRange(0,4).toString(Charsets.US_ASCII))
            tap("tools"); tap("play")
            val observed=linkedSetOf<String>()
            var sounding=0
            await(15000) {
                val s=engine.snapshot()
                observed.add(s.part.title)
                if(s.peak>.001f) sounding++
                !s.running
            }
            assertEquals(SongPart.entries.map { it.title },observed.toList())
            assertTrue("Recorded full song was silent",sounding>=3)
            assertEquals(project.sections,engine.exportSession().sections)
            assertEquals(project.lanes,engine.exportSession().lanes)
            tap("part-${SongPart.Drop.ordinal}"); tap("audition")
            delay(3000)
            assertTrue(engine.snapshot().running); assertFalse(engine.snapshot().arrangementPlaying)
            assertEquals(SongPart.Drop,engine.snapshot().part)
            tap("audition")
            File(context.filesDir,"arrangement-report.json").writeText(JSONObject().put("ok",true)
                .put("manualRecordings",recorded).put("playbackOrder",JSONArray(observed.toList()))
                .put("independentUndoAndModePreservation",true).put("fullSaveLoad",true)
                .put("shortenedPartCannotRestoreOutOfRangeNotes",true)
                .put("exportFrames",expectedFrames).put("exportBytes",wav.length())
                .put("dropAuditionLoops",true).put("fingertipVerification","deferred by user").toString(2))
            Log.i("SpatialLoopTest","ArrangementPASS manualParts=5 modes=2 save/load/undo/orderedPlayback/dropAudition " +
                "wavBytes=${wav.length()} sounding=$sounding")
        } finally {
            withContext(Dispatchers.Main) { vm.foreground(true); engine.load(original) }
            if(savedBytes!=null) saved.writeBytes(savedBytes) else saved.delete()
        }
    }

    @Ignore("用户暂停指尖验证；加强后的场景预测断言尚未通过，不计为已验证")
    @Test fun sceneFrameMarkerPredictionRawContactAndTrackingLoss(): Unit = runBlocking {
        val context=instrumentation.targetContext
        context.startActivity(Intent().setClassName(context.packageName,
            "com.example.spatialdj.p170bd28c.platform.LaunchActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var found: LoopViewModel?=null
        await(45000) { withMain { found=find(LoopViewModel::class.java); found!=null } }
        val vm=checkNotNull(found)
        await(45000) { vm.state.value.ready && vm.state.value.output=="左右空间音箱" }
        val scene=withMain { checkNotNull(find(LoopScene::class.java)) }
        val input=withMain { checkNotNull(find(PanelInput::class.java)) }
        val engine=withMain { field(vm,"engine") as LoopEngine }
        val original=engine.exportSession()
        await { withMain { input.gate.active && field(scene,"stream")!=null } }
        val provider=withMain { field(scene,"provider") as HandTrackingProvider }
        val panel=withMain { field(scene,"panel") as Entity }
        @Suppress("UNCHECKED_CAST")
        val markers=withMain { (field(scene,"markers") as List<Pair<Entity,Any>>).map { it.first } }
        val stream=withMain { checkNotNull(field(scene,"stream")) }
        val sampleClass=LoopScene::class.java.declaredClasses.first { it.simpleName=="HandSample" }
        val constructor=sampleClass.declaredConstructors.first().apply { isAccessible=true }
        val sampleField=stream.javaClass.getDeclaredField("sample").apply { isAccessible=true }
        fun inject(local: Vector3?,priorLocal: Vector3?=null) {
            val stage=local?.let { panel.convertPositionTo(it,null) }
            val point=stage?.let { MarkerPredictor.Point(it.x,it.y,it.z) }
            val priorStage=priorLocal?.let { panel.convertPositionTo(it,null) }
            val prior=priorStage?.let { MarkerPredictor.Point(it.x,it.y,it.z) }
            val received=java.lang.System.nanoTime()
            sampleField.set(stream,constructor.newInstance(point,null,received,prior,null,
                if(prior!=null) received-12_000_000 else 0L))
            Log.i("SpatialLoopTest","MarkerInject local=$local stage=$stage prior=$priorStage received=$received")
        }
        suspend fun freshAwait(label: String,predicate: ()->Boolean) {
            try {
                withTimeout(3000) { while(!withContext(Dispatchers.Main) { predicate() }) delay(2) }
            } catch(error: TimeoutCancellationException) {
                val state=withContext(Dispatchers.Main) {
                    "enabled=${markers[0].enabled} position=${markers[0].components.get<TransformComponent>()!!.position}"
                }
                throw AssertionError("Marker $label: $state",error)
            }
        }
        try {
            withContext(Dispatchers.Main) {
                provider.stop() // Suspend sensor callbacks so injected samples reach the actual ECS hook.
                engine.newSession(); vm.action(LoopAction.Select(0)); input.reset()
                sampleField.set(stream,null)
            }
            delay(120)
            @Suppress("UNCHECKED_CAST")
            val surface=withMain {
                val controls=field(input,"controls") as Map<String,Any>
                field(controls.getValue("pad-0-0"),"surface") as FingertipGate.Surface
            }
            val r=surface.rect
            val x=((r.x+r.width/2)/input.gate.widthPixels-.5f)*input.gate.widthMeters
            val y=(.5f-(r.y+r.height/2)/input.gate.heightPixels)*input.gate.heightMeters
            val before=engine.snapshot().triggers
            withContext(Dispatchers.Main) { inject(Vector3(x,y,.035f)) }
            freshAwait("first fresh sample") {
                markers[0].enabled && markers[0].components.get<TransformComponent>()!!.position.z>.03f
            }
            Log.i("SpatialLoopTest","MarkerScene first actual ECS transform written")
            // This snapshot models consecutive 12 ms provider samples, including skipped scene callbacks.
            withContext(Dispatchers.Main) { inject(Vector3(x,y,.019f),Vector3(x,y,.035f)) }
            var displayed=0f
            var traced=false
            freshAwait("predicted second sample") {
                displayed=markers[0].components.get<TransformComponent>()!!.position.z
                if(!traced && markers[0].enabled && displayed in .0189f.. .0191f) {
                    traced=true
                    @Suppress("UNCHECKED_CAST")
                    val predictor=(field(scene,"predictors") as Array<MarkerPredictor>)[0]
                    val state=listOf("latest","time","vx","vy","vz","predictable").associateWith { field(predictor,it) }
                    Log.i("SpatialLoopTest","MarkerRawInsteadOfPrediction state=$state now=${java.lang.System.nanoTime()}")
                }
                markers[0].enabled && displayed<.017f
            }
            assertEquals("Predicted visual point activated a pad",before,engine.snapshot().triggers)
            withContext(Dispatchers.Main) { inject(Vector3(x,y,.009f)) }
            freshAwait("raw contact") { engine.snapshot().triggers==before+1 }
            delay(120)
            assertFalse("Lost/stale marker kept showing",withMain { markers[0].enabled })
            assertTrue("Lost sample held a button",withMain { input.gate.held().isEmpty() })
            withContext(Dispatchers.Main) { inject(Vector3(x,y,-.02f)) }
            delay(30)
            assertEquals("Reacquisition behind the panel retriggered",before+1,engine.snapshot().triggers)
            withContext(Dispatchers.Main) { inject(null) }
            freshAwait("loss") { markers.none { it.enabled } }
            withContext(Dispatchers.Main) {
                scene.active(false,input.gate)
                assertNull(field(scene,"listener"))
                assertNull(field(scene,"stream"))
                scene.active(true,input.gate)
                assertNotSame("Paused listener mailbox reused",stream,field(scene,"stream"))
            }
            File(context.filesDir,"marker-scene-report.json").writeText(JSONObject().put("ok",true)
                .put("input","injected tracking mailbox through actual ECS update")
                .put("rawZ",.019).put("displayedZ",displayed)
                .put("predictionCannotTriggerPad",true).put("actualContactStrikes",1)
                .put("staleAndTrackingLossHideMarker",true).put("lifecycleListenerRelease",true)
                .put("physicalTrackingLatencyVerified",false).toString(2))
            Log.i("SpatialLoopTest","MarkerScenePASS rawZ=.019 displayZ=$displayed rawContactOnly=true staleHidden=true listenerReleased=true")
        } finally {
            withContext(Dispatchers.Main) {
                scene.active(false,input.gate); scene.active(true,input.gate); engine.load(original)
            }
        }
    }

    @Test fun automaticButtonPlaybackAndRestoration(): Unit = runBlocking {
        val context=instrumentation.targetContext
        context.startActivity(Intent().setClassName(context.packageName,
            "com.example.spatialdj.p170bd28c.platform.LaunchActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var found: LoopViewModel?=null
        await(45000) { withMain { found=find(LoopViewModel::class.java); found!=null } }
        val vm=checkNotNull(found)
        await(45000) { vm.state.value.ready && vm.state.value.output=="左右空间音箱" }
        val scene=withMain { checkNotNull(find(LoopScene::class.java)) }
        val input=withMain { checkNotNull(find(PanelInput::class.java)) }
        val engine=withMain { field(vm,"engine") as LoopEngine }
        val score=context.assets.open("music/neon-bounce.json").bufferedReader().use { AutoScoreFiles.read(it.readText()) }
        val initial=engine.exportSession()
        val saved=File(context.filesDir,"saved.loop")
        val savedBytes=saved.takeIf(File::exists)?.readBytes()
        val backupFile=File(context.filesDir,"before-auto.loop")
        val backupBytes=backupFile.takeIf(File::exists)?.readBytes()
        val pages=JSONArray()
        fun controls(): Map<String,FingertipGate.Surface> {
            @Suppress("UNCHECKED_CAST")
            return (field(input,"controls") as Map<String,Any>)
                .mapValues { field(it.value,"surface") as FingertipGate.Surface }
        }
        suspend fun tap(id: String,finger: Int=1): Double {
            await { withMain { input.gate.active && controls().containsKey(id) } }
            val elapsed=withContext(Dispatchers.Main) {
                val r=controls().getValue(id).rect
                fun tip(z: Float)=FingertipGate.Tip(finger,
                    ((r.x+r.width/2)/input.gate.widthPixels-.5f)*input.gate.widthMeters,
                    (.5f-(r.y+r.height/2)/input.gate.heightPixels)*input.gate.heightMeters,z)
                val now=SystemClock.uptimeMillis()
                input.hands(now,listOf(tip(.04f)))
                val start=java.lang.System.nanoTime()
                input.hands(now,listOf(tip(.01f)))
                val duration=(java.lang.System.nanoTime()-start)/1e6
                input.hands(now,listOf(tip(.03f)))
                duration
            }
            return elapsed
        }
        fun auditAuto(step: Int) {
            val status=vm.state.value.status!!
            val progress=status.auto!!
            val lane=progress.lane
            val measured=input.textMeasurements()
            assertEquals(RecordingMode.FullSong,status.mode)
            assertTrue("Automatic button layout missing",measured.containsKey("auto.label"))
            for((id,sample) in measured) {
                assertFalse("Auto step $step/$id clipped ${sample.layout.layoutInput.text}",sample.layout.hasVisualOverflow)
                assertEquals(LoopFontFamily,sample.layout.layoutInput.style.fontFamily)
            }
            if(progress.active && step!=7) {
                assertTrue("Auto must reveal the current lane",controls().containsKey("pad-$lane-0"))
                assertTrue("Full mode audition control missing",controls().containsKey("audition"))
                for(part in SongPart.entries) assertTrue("Full mode part missing: $part",controls().containsKey("part-${part.ordinal}"))
                progress.section?.let { title ->
                    val clip=score.sections.single { it.title==title }
                    assertEquals(clip.part,status.part)
                    assertEquals(clip.lanes.map { it.size },status.lanes.map { it.count })
                    assertEquals(title,measured.getValue("transport-state").layout.layoutInput.text.text)
                    clip.lanes.forEachIndexed { i,notes ->
                        val expected=if(notes.isEmpty()) "本段休止" else "${notes.size} 个音符"
                        assertEquals(expected,measured.getValue("lane$i.detail").layout.layoutInput.text.text)
                    }
                }
            }
            pages.put(JSONObject().put("step",step).put("lane",lane).put("texts",measured.size)
                .put("part",status.part.name).put("section",progress.section).put("mode",status.mode.name))
        }
        try {
            // This test injects contact through live measured controls. Do not combine it with
            // unrelated real hand samples while observing the long automatic performance.
            withMain {
                val provider=field(scene,"provider") as HandTrackingProvider
                @Suppress("UNCHECKED_CAST")
                val listener=field(scene,"listener") as com.pico.spatial.tracking.DataProvider.DataListener<
                    com.pico.spatial.tracking.hand.HandTrackingData>
                provider.removeListener(listener)
                scene.javaClass.getDeclaredField("listener").apply { isAccessible=true }.set(scene,null)
                val stream=checkNotNull(field(scene,"stream"))
                stream.javaClass.getDeclaredField("sample").apply { isAccessible=true }.set(stream,null)
            }
            withContext(Dispatchers.Main) { engine.newSession(); vm.action(LoopAction.Select(0)) }
            await { withMain { controls().containsKey("pad-0-0") } }
            withContext(Dispatchers.Main) {
                @Suppress("UNCHECKED_CAST")
                val speakers=field(field(vm,"factory")!!,"speakers") as Pair<Entity,Entity>
                assertEquals(1.1f,loopRoot(speakers.first).findEntity("SlopedConsole")!!
                    .components.get<TransformComponent>()!!.position.y,.001f)
            }
            var original=LoopSession(bpm=120,bars=2,master=.63f,lanes=List(5) {
                LaneSession(listOf(NoteEvent(1.0,it%4)),volume=.31f)
            }).withMode(RecordingMode.FullSong)
            for(part in SongPart.entries.filter { it!=SongPart.Drop }) original=original.withSection(
                SongSection(part,1,List(5) { LaneSession(listOf(NoteEvent(2.0,it%4)),volume=.42f) }))
            withContext(Dispatchers.Main) { engine.load(original) }
            tap("auto")
            await { vm.state.value.status?.auto?.active==true }
            assertEquals(RecordingMode.FullSong,engine.snapshot().mode)
            assertEquals(SongPart.Drop,engine.snapshot().part)
            assertTrue("Must count in instead of loading a finished loop",engine.snapshot().countIn>0)
            assertTrue(engine.exportSession().lanes.all { it.events.isEmpty() })
            val observed=mutableSetOf<Int>()
            var flashed=false
            await(65000) {
                val progress=vm.state.value.status?.auto
                if(progress?.active==true) {
                    if(progress.flashPad!=null) flashed=true
                    if(progress.step !in observed && withMain {
                            vm.state.value.selected==progress.lane && controls().containsKey("pad-${progress.lane}-0")
                        }) {
                        withMain { auditAuto(progress.step) }
                        observed.add(progress.step)
                    }
                }
                progress?.section!=null
            }
            assertEquals((1..6).toSet(),observed)
            assertTrue("No pad strike feedback seen",flashed)
            assertEquals(score.expectedSession(),engine.exportSession())
            val counts=score.expectedSession().lanes.map { it.events.size }
            assertEquals(score.sections.first().lanes.map { it.size },engine.snapshot().lanes.map { it.count })
            assertTrue(engine.snapshot().running)
            var sounding=0
            @Suppress("UNCHECKED_CAST")
            val tracks=withMain { field(field(vm,"output")!!,"tracks") as List<AudioTrack> }
            val audioFrames=tracks.map { it.bufferSizeInFrames }
            val underruns=tracks.map { it.underrunCount }
            val sectionsSeen=mutableSetOf<String>()
            val sectionsAudited=mutableSetOf<String>()
            repeat(200) {
                delay(400)
                assertNull(vm.state.value.error)
                vm.state.value.status?.auto?.section?.let { sectionsSeen.add(it) }
                withMain {
                    val status=vm.state.value.status!!
                    assertEquals(RecordingMode.FullSong,status.mode)
                    val title=status.auto?.section
                    if(title!=null && title !in sectionsAudited &&
                        input.textMeasurements()["transport-state"]?.layout?.layoutInput?.text?.text==title &&
                        controls().containsKey("pad-${status.auto?.lane}-0")) {
                        auditAuto(8+sectionsAudited.size)
                        sectionsAudited.add(title)
                    }
                }
                if(engine.snapshot().peak>.001f) sounding++
            }
            assertTrue("Automatic music silent",sounding>=140)
            assertEquals("Missing changing musical sections",score.sections.map { it.title }.toSet(),sectionsSeen)
            assertEquals("Missing full-page section audits",sectionsSeen,sectionsAudited)
            val addedUnderruns=tracks.mapIndexed { i,track -> track.underrunCount-underruns[i] }
            assertTrue("Spatial playback underruns: $addedUnderruns",addedUnderruns.all { it<=2 })
            assertEquals("Automatic completion must not overwrite saved music",savedBytes?.toList(),saved.takeIf(File::exists)?.readBytes()?.toList())
            tap("tools"); delay(150)
            await { withMain { controls().containsKey("restore-auto") } }
            assertNull("Live tracking interfered with injected contacts",
                withMain { field(checkNotNull(field(scene,"stream")),"sample") })
            withMain { auditAuto(7) } // Tools with the new restore button; verify typography too.
            tap("restore-auto"); delay(100)
            assertEquals(original,engine.exportSession())
            assertFalse(vm.state.value.autoBackup)
            tap("auto"); delay(200) // Opens performance page, starts again.
            await { vm.state.value.status?.auto?.active==true }
            tap("auto"); delay(100)
            assertEquals(original,engine.exportSession())
            assertFalse(engine.snapshot().running)
            assertNull(engine.snapshot().auto)
            tap("auto"); delay(200)
            withContext(Dispatchers.Main) { vm.foreground(false) }
            assertEquals(original,engine.exportSession())
            assertNull(engine.snapshot().auto)
            withContext(Dispatchers.Main) { vm.foreground(true) }
            File(context.filesDir,"automatic-report.json").writeText(JSONObject().put("ok",true)
                .put("notes",score.steps.sumOf { it.notes.size }).put("counts",JSONArray(counts))
                .put("sectionsObserved",JSONArray(sectionsSeen.toList()))
                .put("fullModeThroughout",true).put("liveSectionCountsAndLabels",true)
                .put("states",pages).put("fingertipVerification","deferred by user")
                .put("sounding",sounding).put("audioBufferFrames",JSONArray(audioFrames))
                .put("playbackUnderrunsAdded",JSONArray(addedUnderruns))
                .put("liveTrackingIsolatedDuringInjectedContacts",true)
                .put("cancellationAndBackgroundRestore",true).put("savedMusicPreserved",true).toString(2))
            Log.i("SpatialLoopTest","AutomaticPASS notes=${score.steps.sumOf { it.notes.size }} steps=6 counts=$counts sounding=$sounding/200 " +
                "underruns=$addedUnderruns cancel/background/restore=true")
        } finally {
            withContext(Dispatchers.Main) {
                vm.foreground(true); engine.load(initial)
                scene.active(false,input.gate); scene.active(true,input.gate)
            }
            if(savedBytes!=null) saved.writeBytes(savedBytes) else saved.delete()
            if(backupBytes!=null) backupFile.writeBytes(backupBytes) else backupFile.delete()
        }
    }

    @Test fun realStageFiveLayerAudioStorageGeometryAndLifecycle(): Unit = runBlocking {
        val context=instrumentation.targetContext
        context.startActivity(Intent().setClassName(context.packageName,
            "com.example.spatialdj.p170bd28c.platform.LaunchActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var found: LoopViewModel?=null
        withTimeout(45000) {
            while(found==null) { found=withContext(Dispatchers.Main) { find(LoopViewModel::class.java) }; if(found==null) delay(500) }
        }
        val vm=checkNotNull(found)
        await { vm.state.value.ready && vm.state.value.output=="左右空间音箱" && vm.state.value.status!=null }
        var liveInput: PanelInput?=null
        await { instrumentation.runOnMainSync { liveInput=find(PanelInput::class.java) }; liveInput!=null }
        val input=checkNotNull(liveInput)
        fun controls(): Map<String,FingertipGate.Surface> {
            @Suppress("UNCHECKED_CAST")
            val registered=field(input,"controls") as Map<String,Any>
            return registered.mapValues { field(it.value,"surface") as FingertipGate.Surface }
        }
        suspend fun control(id: String): FingertipGate.Surface {
            var result: FingertipGate.Surface?=null
            await { instrumentation.runOnMainSync { result=controls()[id] }; result!=null }
            val rect=checkNotNull(result).rect
            assertTrue("$id touch face too small",rect.height/input.gate.heightPixels*input.gate.heightMeters>=.035f)
            assertTrue("$id outside panel",rect.x>=0 && rect.y>=0 &&
                rect.x+rect.width<=input.gate.widthPixels+1 && rect.y+rect.height<=input.gate.heightPixels+1)
            return checkNotNull(result)
        }
        suspend fun panel(): Entity = withContext(Dispatchers.Main) {
            val factory=field(vm,"factory")!!
            @Suppress("UNCHECKED_CAST")
            val speakers=field(factory,"speakers") as Pair<Entity,Entity>
            checkNotNull(loopRoot(speakers.first).findEntity("FingerOnlyLoopPanel"))
        }
        suspend fun contact(ids: List<String>,fractions: List<Float> = ids.map { .5f }) {
            val faces=ids.map { control(it) }; val panel=panel()
            withContext(Dispatchers.Main) {
                val now=SystemClock.uptimeMillis()
                fun tips(z: Float)=faces.mapIndexed { i,surface ->
                    val r=surface.rect
                    val x=r.x+surface.insetPixels+(r.width-2*surface.insetPixels)*fractions[i]
                    val y=r.y+r.height/2
                    val local=Vector3((x/input.gate.widthPixels-.5f)*input.gate.widthMeters,
                        (.5f-y/input.gate.heightPixels)*input.gate.heightMeters,z)
                    // Stage coordinates and actual attachment ancestry, then the production contact route.
                    val stage=panel.convertPositionTo(local,null)
                    val back=panel.convertPositionFrom(stage,null)
                    assertEquals(local.x,back.x,.0001f); assertEquals(local.y,back.y,.0001f)
                    FingertipGate.Tip(if(i==0) 1 else 6,back.x,back.y,back.z)
                }
                input.hands(now,tips(.05f))
                input.hands(now,tips(.005f))
                input.hands(now,tips(.04f))
            }
            delay(70)
        }
        suspend fun tap(id: String)=contact(listOf(id))
        val engine=withContext(Dispatchers.Main) { field(vm,"engine") as LoopEngine }
        val original=engine.exportSession()
        val savedFile=File(context.filesDir,"saved.loop")
        val savedBytes=if(savedFile.exists()) savedFile.readBytes() else null
        suspend fun act(action: LoopAction)=withContext(Dispatchers.Main) { vm.action(action) }
        try {
            withContext(Dispatchers.Main) { engine.newSession(); engine.mode(RecordingMode.DropOnly) }
            act(LoopAction.Tempo(180)); act(LoopAction.Bars(1))
            val factory=withContext(Dispatchers.Main) { field(vm,"factory")!! }
            @Suppress("UNCHECKED_CAST")
            val speakers=withContext(Dispatchers.Main) { field(factory,"speakers") as Pair<Entity,Entity> }
            withContext(Dispatchers.Main) {
                val root=loopRoot(speakers.first)
                assertEquals("LoopV3Root",root.getName())
                val room=checkNotNull(root.findEntity("ClubRoom"))
                assertEquals(4,room.getChildren().count { it.getName().matches(Regex("Dancer[0-3]")) })
                val mount=checkNotNull(root.findEntity("SlopedConsole"))
                val panel=checkNotNull(root.findEntity("FingerOnlyLoopPanel"))
                val body=checkNotNull(root.findEntity("ConsoleBody"))
                val bounds=body.getVisualBounds(mount,false)
                assertTrue("Body must be behind native panel",bounds.max.z < -.011f)
                // SpatialUI returns an attachment mount Entity, not the core component owner.
                assertTrue(panel.valid); assertEquals(mount,panel.getParent())
                assertEquals(1,root.getChildren().count { it.getName()=="SlopedConsole" })
                assertTrue(root.findEntity("ClubKeyLight")!!.components.has<DirectionalLightComponent>())
                assertTrue(root.findEntity("DanceSpot0")!!.components.has<SpotLightComponent>())
                val t=mount.components.get<TransformComponent>()!!
                assertEquals(1.1f,t.position.y,.001f)
                val target=Vector3(.1f,.05f,.02f)
                val stage=panel.convertPositionTo(target,null)
                val back=panel.convertPositionFrom(stage,null)
                assertEquals(target.x,back.x,.0001f); assertEquals(target.y,back.y,.0001f); assertEquals(target.z,back.z,.0001f)
                Log.i("SpatialLoopTest","GeometryPASS primitiveBoxes/bodyBehind/panelCount1/tipCoordinateRoundTrip")
            }
            withContext(Dispatchers.Main) {
                for((id,surface) in controls()) {
                    val r=surface.rect
                    assertTrue("$id must fit panel",r.x>=0 && r.y>=0 &&
                        r.x+r.width<=input.gate.widthPixels+1 && r.y+r.height<=input.gate.heightPixels+1)
                    assertTrue("$id must be finger sized",r.height/input.gate.heightPixels*input.gate.heightMeters>=.035f)
                }
            }
            tap("tools"); control("raise")
            tap("bpm+"); assertEquals(180,vm.state.value.status!!.bpm) // upper boundary
            tap("bpm-"); assertEquals(175,vm.state.value.status!!.bpm)
            tap("bpm+"); assertEquals(180,vm.state.value.status!!.bpm)
            tap("bars1"); tap("quantize"); assertFalse(vm.state.value.status!!.quantize); tap("quantize")
            tap("raise"); delay(200)
            withContext(Dispatchers.Main) {
                assertEquals(1.2f,loopRoot(speakers.first).findEntity("SlopedConsole")!!
                    .components.get<TransformComponent>()!!.position.y,.001f)
            }
            tap("lower"); tap("tools"); control("pad-0-0")
            tap("metro"); assertTrue(vm.state.value.status!!.metronome); tap("metro")
            for(lane in 0..4) {
                tap("lane$lane"); control("pad-$lane-0"); tap("record")
                await { vm.state.value.status?.recording==lane }
                // Both hand IDs pass through actual measured faces in the same sample.
                contact(listOf("pad-$lane-0","pad-$lane-1"))
                delay(220)
                tap("pad-$lane-2"); delay(150); tap("pad-$lane-3")
                await { vm.state.value.status?.recording==null && vm.state.value.status?.armed==null }
                assertEquals(4,vm.state.value.status!!.lanes[lane].count)
                Log.i("SpatialLoopTest","ContactRecordPASS lane=$lane cycle=${vm.state.value.status!!.cycle} notes=4 twoHands=true")
            }
            Log.i("SpatialLoopTest","FiveLayerPlayingSTART")
            var sounding=0
            repeat(60) {
                delay(500)
                assertNull(vm.state.value.error)
                if(vm.state.value.status!!.peak>.001f) sounding++
                if(it%10==0) Log.i("SpatialLoopTest","LiveAudio seconds=${it/2} cycle=${vm.state.value.status!!.cycle} peak=${vm.state.value.status!!.peak} output=${vm.state.value.output}")
            }
            assertTrue("Continuous five-layer mix was silent",sounding>=45)
            Log.i("SpatialLoopTest","FiveLayerPlayingEND sounding=$sounding/60")
            tap("tools"); control("save"); tap("save"); await { !vm.state.value.busy }
            assertEquals(listOf(4,4,4,4,4),SessionFiles.read(savedFile).lanes.map { it.events.size })
            tap("export"); await(60000) { !vm.state.value.busy }
            assertTrue(vm.state.value.notice.startsWith("已导出"))
            val exported=context.getExternalFilesDir("exports")!!.listFiles()!!.maxBy { it.lastModified() }
            assertTrue(exported.length()>1000000)
            val prefix=exported.inputStream().use { stream -> ByteArray(12).also { stream.read(it) } }
            assertEquals("RIFF",prefix.copyOfRange(0,4).toString(Charsets.US_ASCII))
            Log.i("SpatialLoopTest","FilesPASS fiveLaneSave wav=${exported.name} bytes=${exported.length()}")
            contact(listOf("master"),listOf(0f)); delay(750); assertEquals(0f,vm.state.value.status!!.peak,0f)
            contact(listOf("master"),listOf(.75f))
            tap("tools"); control("clear")
            contact(listOf("vol4"),listOf(.35f)); assertEquals(.35f,vm.state.value.status!!.lanes[4].volume,.002f)
            tap("mute4"); assertTrue(vm.state.value.status!!.lanes[4].muted); tap("mute4")
            for(lane in 0..4) { tap("lane$lane"); tap("clear"); tap("clear") }
            assertTrue(vm.state.value.status!!.lanes.all { it.count==0 })
            tap("tools"); control("load"); tap("load"); tap("load"); await { !vm.state.value.busy }
            assertEquals(listOf(4,4,4,4,4),vm.state.value.status!!.lanes.map { it.count })
            tap("play"); delay(400)
            withContext(Dispatchers.Main) { vm.foreground(false) }
            assertFalse(vm.state.value.status!!.running)
            assertNull(withContext(Dispatchers.Main) { field(vm,"worker") })
            withContext(Dispatchers.Main) { vm.foreground(true) }
            await { vm.state.value.output=="左右空间音箱" }
            assertNull(vm.state.value.error)
            Log.i("SpatialLoopTest","LiveLoopPASS measuredStageFingertipContact/twoHands/5layers/30sSound/tools/save/load/export/sliders/backgroundRelease")
        } finally {
            withContext(Dispatchers.Main) { vm.foreground(true); engine.load(original) }
            if(savedBytes!=null) savedFile.writeBytes(savedBytes) else savedFile.delete()
        }
    }
}
