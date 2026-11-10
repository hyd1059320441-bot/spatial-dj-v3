package com.example.spatialdj.p170bd28c.ui.loop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spatialdj.p170bd28c.domain.model.*
import com.pico.spatial.ui.design.*
import kotlin.math.roundToInt

private val ink = Color(ClubPalette.Ink)
private val muted = Color(ClubPalette.Muted)
private val cream = Color(ClubPalette.Surface)
private val orange = Color(ClubPalette.Orange)
private val mint = Color(ClubPalette.Teal)
private val blue = Color(ClubPalette.Blue)
private val butter = Color(ClubPalette.Amber)
private val cyan = Color(ClubPalette.Teal)
private val amber = Color(ClubPalette.Amber)
private val laneColors = ClubPalette.lanes.map { Color(it) }

@Composable
private fun TouchButton(id: String,label: String,input: PanelInput, modifier: Modifier = Modifier,
                        color: Color = cream, large: Boolean = false, detail: String? = null,
                        icon: TransportIcon? = null, selected: Boolean = false, autoPressed: Boolean = false,
                        action: ()->Unit) {
    val callback by rememberUpdatedState(action)
    val owner=remember(id,input) { Any() }
    DisposableEffect(id,input,owner) { input.attach(id,owner); onDispose { input.unregister(id,owner) } }
    SideEffect { input.callback(id,owner) { callback() } }
    val feedback by remember(id,input) { input.controlFeedback(id) }
    val active = feedback and 2 != 0 || autoPressed
    val near = feedback and 1 != 0
    val fill = when { active -> amber; near -> cyan; else -> color }
    val shape = RoundedCornerShape(if(large) 22.dp else 15.dp)
    Box(modifier.heightIn(min=if(large) 80.dp else 52.dp)
        .onGloballyPositioned { input.register(id,owner,it,false) { callback() } }) {
        Box(Modifier.fillMaxSize().padding(top=5.dp).background(ink,shape))
        Button(onClick = {},modifier = Modifier.fillMaxSize().padding(
            top=if(active) 4.dp else 0.dp,bottom=if(active) 0.dp else 5.dp).border(2.dp,ink,shape),
            shape = shape,
            size = ButtonDefaults.buttonSize(minWidth=0.dp,minHeight=0.dp),
            contentPadding = PaddingValues(horizontal=10.dp,vertical=if(large) 4.dp else 6.dp),
            colors = ButtonDefaults.buttonColors(containerColor=fill,contentColor=ink)) {
            Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(if(large) 2.dp else 1.dp,Alignment.CenterVertically)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally),
                    verticalAlignment=Alignment.CenterVertically) {
                    if(icon!=null) TransportSymbol(icon,Modifier.size(18.dp))
                    LoopText("$id.label",label,input,fontSize=if(large) 27.sp else 19.sp,
                        strong=true,textAlign=TextAlign.Center)
                }
                if(detail!=null) LoopText("$id.detail",detail,input,Modifier.fillMaxWidth(),
                    color=muted,fontSize=15.sp,textAlign=TextAlign.Center)
            }
        }
        if(selected) Box(Modifier.align(Alignment.TopCenter).padding(top=5.dp)
            .width(26.dp).height(3.dp).background(ink,RoundedCornerShape(2.dp)))
    }
}

private enum class TransportIcon { Play, Stop, Record }

/** Transport symbols are geometry, so they never depend on platform glyph coverage. */
@Composable
private fun TransportSymbol(icon: TransportIcon,modifier: Modifier = Modifier) {
    Canvas(modifier) {
        when(icon) {
            TransportIcon.Play -> drawPath(Path().apply {
                moveTo(size.width*.18f,size.height*.08f)
                lineTo(size.width*.9f,size.height*.5f)
                lineTo(size.width*.18f,size.height*.92f)
                close()
            },ink)
            TransportIcon.Stop -> drawRoundRect(ink,cornerRadius=androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            TransportIcon.Record -> drawCircle(ink,size.minDimension*.44f)
        }
    }
}

@Composable
private fun TouchSlider(id: String,label: String,value: Float,input: PanelInput,modifier: Modifier = Modifier,
                        color: Color = mint, action: (Float)->Unit) {
    val callback by rememberUpdatedState(action)
    val owner=remember(id,input) { Any() }
    DisposableEffect(id,input,owner) { input.attach(id,owner); onDispose { input.unregister(id,owner) } }
    SideEffect { input.callback(id,owner) { callback(it) } }
    val feedback by remember(id,input) { input.controlFeedback(id) }
    val fill = when { feedback and 2 != 0 -> amber; feedback and 1 != 0 -> cyan; else -> cream }
    Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            LoopText("$id.label",label,input,Modifier.weight(1f),fontSize=18.sp,strong=true)
            LoopText("$id.value","${(value*100).roundToInt()}%",input,Modifier.width(72.dp),
                fontSize=18.sp,strong=true,textAlign=TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(56.dp).background(fill,RoundedCornerShape(17.dp))
            .border(2.dp,ink,RoundedCornerShape(17.dp))
            .onGloballyPositioned { input.register(id,owner,it,true) { callback(it) } },contentAlignment=Alignment.Center) {
            Slider(value=value,onValueChange={},modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp),
                colors=SliderDefaults.sliderColors(trackColor=Color(ClubPalette.Track),progressColor=color,
                    progressHighColor=amber,thumbColor=ink,thumbHighColor=amber))
        }
    }
}

/** Waveform emblem shares the room's back-wall motif. */
@Composable
private fun ClubWave(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w=size.width; val h=size.height
        drawRoundRect(ink,cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.24f))
        val heights=listOf(.27f,.50f,.76f,.50f,.27f)
        for((i,height) in heights.withIndex()) {
            drawRoundRect(if(i==2) butter else mint,
                Offset(w*(.14f+i*.15f),h*(1-height)/2),Size(w*.10f,h*height),
                cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.04f))
        }
    }
}

/** Read the sample clock only in this small strip, and only invalidate on a new beat. */
@Composable
private fun BeatStrip(live: State<LoopUiState>,bars: Int) {
    val beat by remember(live) { derivedStateOf {
        val s=live.value.status
        if(s?.running==true && s.countIn==0) s.beat.toInt() else -1
    } }
    Row(Modifier.fillMaxWidth().height(26.dp).background(Color(ClubPalette.Recess),RoundedCornerShape(10.dp))
        .padding(6.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        repeat(bars*4) { index ->
            Box(Modifier.weight(1f).fillMaxHeight().background(
                if(index==beat) mint else if(index%4==0) butter else Color(ClubPalette.Track),RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun TouchPad(live: State<LoopUiState>,lane: Int,pad: Int,input: PanelInput,
                     modifier: Modifier,action: (LoopAction)->Unit) {
    val active by remember(live,lane,pad) { derivedStateOf {
        val auto=live.value.status?.auto
        auto?.active==true && auto.lane==lane && auto.flashPad==pad
    } }
    TouchButton("pad-$lane-$pad",Lane.entries[lane].pads[pad],input,modifier,
        laneColors[lane],large=true,detail="音垫 ${pad+1}",autoPressed=active) {
        action(LoopAction.Pad(lane,pad))
    }
}

@Composable
fun LoopPanel(live: State<LoopUiState>,input: PanelInput,modifier: Modifier = Modifier,
              sceneError: String? = null,action: (LoopAction)->Unit) {
    // The console consumes only visible control values. Audio timing/level/strike counters
    // remain live for the room and the two small feedback composables above.
    val state by remember(live,sceneError) { derivedStateOf {
        val value=live.value
        value.copy(error=sceneError ?: value.error,status=value.status?.let { s ->
            s.copy(beat=0.0,cycle=0,peak=0f,triggers=0,elapsedBeats=0.0,
                auto=s.auto?.copy(notesPlayed=0,flashPad=null))
        })
    } }
    val s=state.status; val selected=state.selected
    var tools by remember { mutableStateOf(false) }
    val auto=s?.auto
    val full=s?.mode!=RecordingMode.DropOnly
    SideEffect { input.editing(!state.busy && state.error==null) }
    PicoTheme(colorScheme=PicoTheme.colorScheme.copy(fillPrimary=cream,fillSecondary=Color(ClubPalette.Recess),
        fillTertiary=Color(ClubPalette.Steel),labelPrimary=ink,labelPrimaryLight=ink,labelSecondary=muted)) {
        Column(modifier.width(1000.dp).height(700.dp).onGloballyPositioned(input::origin)
            .clip(RoundedCornerShape(28.dp)).background(cream)
            .border(3.dp,ink,RoundedCornerShape(28.dp)).padding(24.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(68.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                ClubWave(Modifier.size(62.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    LoopText("heading","节拍俱乐部",input,fontSize=28.sp,strong=true)
                    LoopText("tagline","食指演奏 · 随节拍起舞",input,color=muted,fontSize=16.sp)
                }
                TouchButton("auto",if(auto?.active==true) "停止演奏" else "自动演奏",input,
                    Modifier.width(164.dp).height(56.dp),orange,
                    icon=if(auto?.active==true) TransportIcon.Stop else TransportIcon.Play) {
                    input.reset(); tools=false; action(LoopAction.Automatic)
                }
                TouchButton("tools",if(tools) "返回演奏" else "工具箱",input,Modifier.width(120.dp).height(56.dp),butter) {
                    input.reset(); tools=!tools
                }
                TouchButton("exit","退出",input,Modifier.width(80.dp).height(56.dp)) { action(LoopAction.Exit) }
            }
            Row(Modifier.fillMaxWidth().height(66.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                TouchButton("play",if(s?.running==true) "停止播放" else if(full) "播放全曲" else "播放 Drop",input,
                    Modifier.width(if(full) 144.dp else 160.dp).fillMaxHeight(),mint,
                    icon=if(s?.running==true) TransportIcon.Stop else TransportIcon.Play) { action(LoopAction.Play) }
                if(full) TouchButton("audition",if(s?.running==true && !s.arrangementPlaying) "停止试听" else "试听本段",input,
                    Modifier.width(104.dp).fillMaxHeight(),blue) { action(LoopAction.Audition) }
                TouchButton("record",when {
                    s?.recording!=null -> "取消本圈录音"
                    s?.armed!=null -> "取消等待"
                    else -> "录下这一圈"
                },input,Modifier.width(if(full) 160.dp else 180.dp).fillMaxHeight(),orange,
                    icon=if(s?.recording==null && s?.armed==null) TransportIcon.Record else null) { action(LoopAction.Record) }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    LoopText("transport-state",when {
                        auto?.section!=null -> auto.section
                        auto?.active==true -> "自动演奏 · ${auto.step}/${auto.totalSteps} · ${Lane.entries[auto.lane].title}"
                        (s?.countIn ?:0)>0 -> "跟着数拍 · ${s?.countIn}"
                        s?.recording!=null -> "正在录音 · 满圈自动循环"
                        s?.armed!=null -> "下一圈开始录音"
                        s?.arrangementPlaying==true -> "全曲播放 · ${s.part.title}"
                        full -> "${s?.part?.title} · ${s?.part?.hint?.substringAfter("：")}"
                        else -> "Drop · 先选音轨，再录下主段"
                    },input,fontSize=19.sp,strong=true)
                    LoopText("tempo","${s?.bpm ?:110} BPM · ${s?.bars ?:4} 小节 · ${if(s?.quantize!=false) "1/16 对齐" else "自由节奏"}",
                        input,color=muted,fontSize=16.sp)
                }
                TouchButton("metro",if(s?.metronome==true) "节拍音 开" else "节拍音 关",input,
                    Modifier.width(if(full) 112.dp else 128.dp).fillMaxHeight(),blue) { action(LoopAction.Metronome) }
            }
            BeatStrip(live,s?.bars ?:4)
            if(!tools) Row(Modifier.fillMaxWidth().height(74.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Lane.entries.forEachIndexed { i,lane ->
                    val status=s?.lanes?.get(i)
                    val phase=when { s?.recording==i -> "录音中"; s?.armed==i -> "等待开录"
                        status?.muted==true -> "静音"; (status?.count ?:0)>0 -> "${status?.count} 个音符"
                        auto?.section!=null -> "本段休止"; else -> "等待录音" }
                    TouchButton("lane$i",lane.title,input,
                        Modifier.weight(1f).fillMaxHeight(),if(i==selected) laneColors[i] else Color(ClubPalette.Recess),
                        detail=phase,selected=i==selected) {
                        input.reset(); action(LoopAction.Select(i))
                    }
                }
            }
            if(tools) {
                Column(Modifier.weight(1f).fillMaxWidth().background(Color(ClubPalette.Recess),RoundedCornerShape(22.dp))
                    .border(2.dp,ink,RoundedCornerShape(22.dp)).padding(horizontal=12.dp,vertical=4.dp),
                    verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    LoopText("tools-heading","工程工具 · 录制与编曲",input,fontSize=20.sp,strong=true)
                    Row(Modifier.fillMaxWidth().height(52.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        for(mode in RecordingMode.entries) TouchButton("mode-${mode.ordinal}",mode.title,input,
                            Modifier.weight(1f).fillMaxHeight(),if(s?.mode==mode) mint else cream,
                            selected=s?.mode==mode) { input.reset(); action(LoopAction.Mode(mode)) }
                    }
                    Row(Modifier.fillMaxWidth().height(52.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        for((id,label,event) in listOf(Triple("save","保存工程",LoopAction.Save),
                            Triple("load","载入工程",LoopAction.Load),Triple("export","导出 WAV",LoopAction.Export),
                            Triple("new","新建工程",LoopAction.New))) {
                            TouchButton(id,label,input,Modifier.weight(1f).fillMaxHeight(),butter) { action(event) }
                        }
                        if(state.autoBackup) TouchButton("restore-auto","恢复原工程",input,
                            Modifier.weight(1f).fillMaxHeight(),mint) { action(LoopAction.RestoreBeforeAuto) }
                    }
                    Row(Modifier.fillMaxWidth().height(52.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        TouchButton("bpm-","速度 -5",input,Modifier.weight(1f).fillMaxHeight(),blue) { action(LoopAction.Tempo((s?.bpm ?:110)-5)) }
                        TouchButton("bpm+","速度 +5",input,Modifier.weight(1f).fillMaxHeight(),blue) { action(LoopAction.Tempo((s?.bpm ?:110)+5)) }
                        for(bars in listOf(1,2,4)) TouchButton("bars$bars","$bars 小节",input,Modifier.weight(1f).fillMaxHeight(),
                            if(s?.bars==bars) mint else cream) { action(LoopAction.Bars(bars)) }
                        TouchButton("quantize",if(s?.quantize!=false) "对齐 开" else "对齐 关",input,Modifier.weight(1f).fillMaxHeight()) { action(LoopAction.Quantize) }
                    }
                    Row(Modifier.fillMaxWidth().height(52.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        LoopText("height","台面 ${(state.height*100).roundToInt()} 厘米",input,Modifier.weight(1f),fontSize=19.sp,strong=true)
                        TouchButton("lower","降低 10 厘米",input,Modifier.width(148.dp).fillMaxHeight(),orange) { action(LoopAction.Height(-.1f)) }
                        TouchButton("raise","升高 10 厘米",input,Modifier.width(148.dp).fillMaxHeight(),mint) { action(LoopAction.Height(.1f)) }
                        TouchButton("undo","撤销 / 恢复",input,Modifier.width(132.dp).fillMaxHeight()) { action(LoopAction.Undo) }
                        TouchButton("clear","清空本轨",input,Modifier.width(122.dp).fillMaxHeight()) { action(LoopAction.Clear) }
                    }
                    LoopText("tools-hint","新建、载入与清空：3 秒内轻触两次确认。调整速度前请停止循环。",
                        input,color=muted,fontSize=15.sp)
                }
            } else {
                if(full) Row(Modifier.fillMaxWidth().height(52.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    SongPart.entries.forEach { part ->
                        val notes=s?.sections?.firstOrNull { it.part==part }?.notes ?: 0
                        TouchButton("part-${part.ordinal}",part.title,input,Modifier.weight(1f).fillMaxHeight(),
                            if(s?.part==part) mint else if(notes>0) butter else cream,selected=s?.part==part) {
                            input.reset(); action(LoopAction.Part(part))
                        }
                    }
                } else Row(Modifier.fillMaxWidth().height(52.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
                        LoopText("pads-heading","${Lane.entries[selected].title}音垫",input,fontSize=20.sp,strong=true)
                        LoopText("pads-hint",Lane.entries[selected].subtitle,input,color=muted,fontSize=15.sp)
                    }
                    TouchButton("undo","撤销 / 恢复",input,Modifier.width(140.dp).fillMaxHeight()) { action(LoopAction.Undo) }
                    TouchButton("clear","清空本轨",input,Modifier.width(130.dp).fillMaxHeight()) { action(LoopAction.Clear) }
                }
                val pads=Lane.entries[selected].pads
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    for(row in 0 until if(pads.size>4) 2 else 1) {
                        Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            for(pad in row*4 until minOf(row*4+4,pads.size)) {
                                TouchPad(live,selected,pad,input,Modifier.weight(1f).fillMaxHeight(),action)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(90.dp),verticalAlignment=Alignment.Bottom,
                horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                TouchSlider("vol$selected","本轨音量",s?.lanes?.get(selected)?.volume ?: .7f,input,Modifier.weight(1f),laneColors[selected]) {
                    action(LoopAction.Volume(selected,it))
                }
                TouchButton("mute$selected",if(s?.lanes?.get(selected)?.muted==true) "恢复声音" else "本轨静音",input,
                    Modifier.width(120.dp).height(56.dp),laneColors[selected]) { action(LoopAction.Mute(selected)) }
                TouchSlider("master","总音量",s?.master ?: 1f,input,Modifier.weight(1f),mint) { action(LoopAction.Master(it)) }
            }
            Box(Modifier.fillMaxWidth().height(48.dp),contentAlignment=Alignment.CenterStart) {
                LoopText("notice",state.error ?: state.notice.ifEmpty { s?.message ?: "乐器正在准备…" },input,
                    Modifier.fillMaxWidth(),color=if(state.error!=null) Color(ClubPalette.Error) else muted,
                    fontSize=16.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}
