package com.example.spatialdj.p170bd28c.ui.loop

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import com.pico.spatial.ui.foundation.gesture.SpatialPointerInfo

internal data class PanelTextMeasurement(val layout: TextLayoutResult, val rect: FingertipGate.Rect)

class PanelInput {
    val gate = FingertipGate()
    private data class Registered(val surface: FingertipGate.Surface, val callback: (Float)->Unit)
    private data class Pending(val owner: Any, val coords: LayoutCoordinates, val slider: Boolean, val callback: (Float)->Unit)
    private val controls = linkedMapOf<String,Registered>()
    private val owners = ControlOwners()
    private var editingEnabled = true
    private val pending = linkedMapOf<String,Pending>()
    private val flashes = mutableMapOf<String,Long>()
    private val textLayouts = linkedMapOf<String,TextLayoutResult>()
    private val textCoordinates = linkedMapOf<String,LayoutCoordinates>()
    private var origin: LayoutCoordinates? = null
    private var loggedLayout = ""
    private var epoch = 0
    private val feedbackStates = mutableMapOf<String,MutableState<Int>>()
    var hovered = emptySet<String>()
        private set
    var pressed = emptySet<String>()
        private set
    internal fun controlFeedback(id: String): State<Int> = feedbackStates.getOrPut(id) { mutableStateOf(0) }
    fun origin(coordinates: LayoutCoordinates) {
        origin = coordinates
        pending.toMap().forEach { (id,p) -> register(id,p.owner,p.coords,p.slider,p.callback) }
    }
    fun attach(id: String,owner: Any) {
        owners.claim(id,owner)
        controls.remove(id); pending.remove(id)
    }
    fun register(id: String, owner: Any, coords: LayoutCoordinates, slider: Boolean, callback: (Float)->Unit) {
        if(!owners.owns(id,owner)) return
        pending[id] = Pending(owner,coords,slider,callback)
        val panel = origin ?: return
        if (!coords.isAttached || !panel.isAttached) return
        val xy = panel.localPositionOf(coords,Offset.Zero)
        controls[id] = Registered(FingertipGate.Surface(id,
            FingertipGate.Rect(xy.x,xy.y,coords.size.width.toFloat(),coords.size.height.toFloat()),
            slider,insetPixels = 18f * gate.widthPixels/1000f),callback)
    }
    fun callback(id: String, owner: Any, callback: (Float)->Unit) {
        if(!owners.owns(id,owner)) return
        controls[id]?.let { controls[id] = it.copy(callback = callback) }
        pending[id]?.let { pending[id] = it.copy(callback = callback) }
    }
    fun unregister(id: String,owner: Any) {
        if(!owners.release(id,owner)) return
        controls.remove(id); pending.remove(id); flashes.remove(id)
        feedbackStates.remove(id)?.value=0
    }
    fun editing(value: Boolean) {
        if(editingEnabled==value) return
        editingEnabled=value; reset()
    }
    // Debug-only callers record actual Compose layout for device font/alignment regression.
    internal fun textLayout(id: String, result: TextLayoutResult) { textLayouts[id] = result }
    internal fun textPosition(id: String, coordinates: LayoutCoordinates) { textCoordinates[id] = coordinates }
    internal fun forgetText(id: String) { textLayouts.remove(id); textCoordinates.remove(id) }
    internal fun textMeasurements(): Map<String,PanelTextMeasurement> {
        val panel = origin ?: return emptyMap()
        if (!panel.isAttached) return emptyMap()
        return textCoordinates.mapNotNull { (id,coords) ->
            val layout = textLayouts[id] ?: return@mapNotNull null
            if (!coords.isAttached) return@mapNotNull null
            val xy = panel.localPositionOf(coords,Offset.Zero)
            id to PanelTextMeasurement(layout,FingertipGate.Rect(
                xy.x,xy.y,coords.size.width.toFloat(),coords.size.height.toFloat()))
        }.toMap()
    }
    private fun usable(id: String) = editingEnabled || id=="tools" || id=="exit"
    private fun surfaces() = controls.values.filter { usable(it.surface.id) }.map { it.surface }
    fun hands(now: Long, tips: List<FingertipGate.Tip>) {
        val hits = gate.hands(now,tips,surfaces())
        dispatch(hits,now)
        feedback(now)
    }
    fun pointer(info: SpatialPointerInfo): Boolean {
        // Native rays/Poke/pinch cannot activate or duplicate a fingertip strike.
        return true
    }
    private fun dispatch(hits: List<FingertipGate.Hit>, now: Long) {
        val generation=epoch
        for (hit in hits) {
            if(epoch!=generation) break
            val control = controls[hit.id] ?: continue
            if(!usable(hit.id)) continue
            if (!hit.up && (hit.down || control.surface.slider)) {
                control.callback(hit.value)
                if (hit.down) flashes[hit.id] = now+45
            }
        }
    }
    fun feedback(now: Long = SystemClock.uptimeMillis()) {
        dispatch(gate.expire(now),now)
        flashes.entries.removeAll { it.value < now }
        val sliders = gate.held().filter { controls[it]?.surface?.slider == true }
        pressed = flashes.keys + sliders
        hovered = gate.hover(now,surfaces())
        feedbackStates.forEach { (id,state) ->
            state.value=(if(id in hovered) 1 else 0) or (if(id in pressed) 2 else 0)
        }
        val signature = controls.keys.joinToString(",") + ":${gate.widthPixels},${gate.heightPixels}"
        if(controls.size >= 19 && signature != loggedLayout && gate.widthPixels > 0) {
            loggedLayout = signature
            val invalid = controls.filter { (_,control) ->
                val r = control.surface.rect
                r.width < 1 || r.height < 1 || r.x < -1 || r.y < -1 ||
                    r.x+r.width > gate.widthPixels+1 || r.y+r.height > gate.heightPixels+1
            }
            Log.i("SpatialLoop","ControlsReady count=${controls.size} pixels=${gate.widthPixels},${gate.heightPixels} outside=${invalid.keys}")
        }
    }
    fun reset() {
        epoch++; gate.reset(); pressed = emptySet(); hovered = emptySet(); flashes.clear()
        feedbackStates.values.forEach { it.value=0 }
    }
}
