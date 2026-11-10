package com.example.spatialdj.p170bd28c.content

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spatialdj.p170bd28c.data.audio.AndroidAudioOutputFactory
import com.example.spatialdj.p170bd28c.ui.loop.*
import com.pico.spatial.core.ecs.AttachmentPanelComponent
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.platform.*
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

@Composable
fun HomeStage() {
    val context = LocalContext.current
    val key = remember { UUID.randomUUID().toString() }
    val vm: LoopViewModel = viewModel(key = key,factory = remember { LoopViewModel.Factory(context) })
    val state = vm.state.collectAsStateWithLifecycle()
    val scene = remember { LoopScene() }
    val input = remember { PanelInput() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val navigator = LocalSpatialNavigator.current
    var sceneReady by remember { mutableStateOf(false) }
    var sceneError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(vm,scene,lifecycle) {
        val observer = LifecycleEventObserver { _,event ->
            if(event == Lifecycle.Event.ON_STOP) {
                input.reset(); scene.active(false,input.gate); scene.environmentActive(false); vm.foreground(false)
            }
            if(event == Lifecycle.Event.ON_RESUME) vm.foreground(true)
        }
        lifecycle.addObserver(observer)
        vm.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycle.removeObserver(observer)
            input.reset(); scene.active(false,input.gate)
            vm.close(); scene.close()
        }
    }
    LaunchedEffect(vm,navigator) {
        vm.state.map { it.exit }.distinctUntilChanged().collect { if(it) navigator.closeStage() }
    }
    LaunchedEffect(sceneReady) {
        if(sceneReady) while(true) {
            withFrameNanos {
                val latest = vm.state.value
                scene.height(latest.height)
                scene.playback(latest.status)
                scene.environmentActive(latest.ready && latest.error==null && !latest.exit &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                scene.active(latest.ready && !latest.exit &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),input.gate)
            }
        }
    }
    SpatialView(modifier = Modifier.fillMaxSize(),attachments = {
        AttachmentPanel("loop-controls",alignment = AttachmentPanelComponent.Alignment.CENTER) {
            val density = LocalDensity.current
            val converter = LocalPhysicalLengthConverter.current
            val panelModifier = Modifier.onSizeChanged { size ->
                with(density) {
                    input.gate.widthPixels = size.width.toFloat(); input.gate.heightPixels = size.height.toFloat()
                    input.gate.widthMeters = converter.dpToLength(size.width.toDp(),LengthUnit.Meters)
                    input.gate.heightMeters = converter.dpToLength(size.height.toDp(),LengthUnit.Meters)
                    scene.size(input.gate.widthMeters,input.gate.heightMeters)
                }
            }.pointerInput(input) {
                try { detectSpatialPointerEvent(context = context) { infos ->
                    for(info in infos) input.pointer(info)
                    true
                } } finally { input.reset() }
            }
            LoopPanel(state,input,panelModifier,sceneError,vm::action)
        }
    },initial = { content,attachments ->
        try {
            scene.create(content,checkNotNull(attachments.entity("loop-controls")))
            scene.bindInput(input)
            vm.bind(AndroidAudioOutputFactory(checkNotNull(scene.speakers)))
            sceneReady = true
        } catch(error: Exception) {
            Log.e("SpatialLoop","Scene creation failed",error); sceneError = "台体加载失败：${error.message}"
        }
    },update = { _,_ -> })
}
