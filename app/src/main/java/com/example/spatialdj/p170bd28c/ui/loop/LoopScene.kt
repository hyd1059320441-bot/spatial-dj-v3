package com.example.spatialdj.p170bd28c.ui.loop

import android.os.SystemClock
import android.util.Log
import com.example.spatialdj.p170bd28c.domain.model.LoopStatus
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import com.pico.spatial.tracking.hand.*
import com.pico.spatial.tracking.DataProvider

/** Console/input owner with a lifecycle-bound native club room. */
class LoopScene {
    private var content: SpatialViewContent? = null
    private var root: Entity? = null
    private var room: ClubRoom? = null
    private var mount: Entity? = null
    private var panel: Entity? = null
    private val legs = mutableListOf<Entity>()
    private val ornaments = mutableListOf<Entity>()
    private val resources = mutableListOf<Resource>()
    private val meshCache = mutableMapOf<Vector3,MeshResource>()
    private val materials = mutableMapOf<String,UnlitMaterial>()
    var speakers: Pair<Entity,Entity>? = null
        private set
    private var width = 1.1f
    private var panelHeight = .74f
    private var tableHeight = 1.1f
    private var body: Entity? = null
    private var outline: Entity? = null
    private val markers = mutableListOf<Pair<Entity,UnlitMaterial>>()
    private val markerColors = IntArray(10) { -1 }
    private var provider: HandTrackingProvider? = null
    private var started = false
    private data class HandSample(val left: MarkerPredictor.Point?,val right: MarkerPredictor.Point?,val received: Long,
        val priorLeft: MarkerPredictor.Point?,val priorRight: MarkerPredictor.Point?,val priorReceived: Long)
    private class HandStream { @Volatile var sample: HandSample?=null }
    private var stream: HandStream?=null
    private var listener: DataProvider.DataListener<HandTrackingData>?=null
    private var consumed: HandSample?=null
    private var input: PanelInput?=null
    private val predictors=Array(2) { MarkerPredictor() }
    private var lastLoggedSize = ""
    private fun t(entity: Entity) = entity.components.get<TransformComponent>()!!
    private fun group(parent: Entity?, name: String): Entity = Entity().apply { setName(name); parent?.addChild(this) }
    private fun <T: Resource> keep(value: T): T { value.toGlobal(); resources.add(value); return value }
    private fun pastel(name: String, hex: String) = materials.getOrPut(name) {
        keep(UnlitMaterial.create()).apply {
            setBaseColor(Color4.fromSRGBHex(hex)); setApplyToneMapping(false)
        }
    }
    private fun rounded(parent: Entity,name: String,size: Vector3,at: Vector3,mat: UnlitMaterial): Entity {
        val mesh=meshCache.getOrPut(size) {
            keep(MeshResource.createBox(BoxOption(size).setCornerRadius(
                if(size==Vector3(1f,1f,1f)) .03f else minOf(size.x,size.y,size.z)*.22f)))
        }
        return ModelEntity(mesh,mat).apply { setName(name); t(this).position=at; parent.addChild(this) }
    }
    private fun ball(parent: Entity,name: String,size: Vector3,at: Vector3,mat: UnlitMaterial): Entity {
        val mesh=meshCache.getOrPut(Vector3(-1f,-1f,-1f)) {
            keep(MeshResource.createWithMeshModel(ClubMesh.sphere(),name="ConsoleSphere168"))
        }
        return ModelEntity(mesh,mat).apply {
            setName(name); t(this).apply { position=at; scaleVector=size }; parent.addChild(this)
        }
    }
    fun create(view: SpatialViewContent, panel: Entity) {
        check(root == null)
        content = view; this.panel = panel
        val r = group(null,"LoopV3Root"); root = r; view.addEntity(r)
        room=ClubRoom(r)
        speakers=checkNotNull(room?.speakers)
        val m = group(r,"SlopedConsole"); mount = m
        m.addChild(panel); panel.setName("FingerOnlyLoopPanel")
        t(panel).apply { position = Vector3.ZERO; eulerAngles = EulerAngles(0f,0f,0f) }
        val dark=pastel("ink",ClubPalette.rgba(ClubPalette.Ink))
        val ivory=pastel("surface",ClubPalette.rgba(ClubPalette.Surface))
        val steel=pastel("steel",ClubPalette.rgba(ClubPalette.Steel))
        val mint=pastel("teal",ClubPalette.rgba(ClubPalette.Teal))
        val amber=pastel("amber",ClubPalette.rgba(ClubPalette.Amber))
        outline=rounded(m,"ConsoleOutline",Vector3(1f,1f,1f),Vector3(0f,0f,-.103f),dark)
        body=rounded(m,"ConsoleBody",Vector3(1f,1f,1f),Vector3(0f,0f,-.070f),steel)
        for (x in listOf(-.31f,.31f)) {
            legs.add(rounded(r,"ConsoleLeg${legs.size}",Vector3(.085f,1f,.12f),Vector3(x,0f,-.6f),dark))
            rounded(r,"ConsoleFoot${legs.size}",Vector3(.23f,.05f,.38f),Vector3(x,.025f,-.63f),dark)
        }
        val recordMesh=keep(MeshResource.createCylinder(1f,1f))
        repeat(2) { i ->
            val ornament=group(m,"ConsoleOrnament$i")
            ornaments.add(ornament)
            rounded(ornament,"OrnamentPlate$i",Vector3(.22f,.22f,.06f),Vector3(0f,0f,-.05f),if(i==0) steel else dark)
            if(i==0) {
                ModelEntity(recordMesh,dark).apply {
                    setName("VinylOrnament"); t(this).apply {
                        position=Vector3(0f,0f,-.013f); scaleVector=Vector3(.077f,.012f,.077f)
                        eulerAngles=EulerAngles(90f,0f,0f)
                    }; ornament.addChild(this)
                }
                ball(ornament,"VinylLabel",Vector3(.030f,.030f,.003f),Vector3(0f,0f,-.003f),mint)
                ball(ornament,"VinylSpindle",Vector3(.005f,.005f,.003f),Vector3(0f,0f,.001f),ivory)
            } else {
                listOf(.035f,.07f,.12f,.07f,.035f).forEachIndexed { bar,height ->
                    rounded(ornament,"ConsoleWave$bar",Vector3(.013f,height,.01f),
                        Vector3((bar-2)*.026f,0f,-.012f),if(bar==2) amber else mint)
                }
            }
        }
        repeat(2) { i ->
            val mat=keep(UnlitMaterial.create()).apply {
                setApplyToneMapping(false); setBaseColor(Color4.fromSRGBHex(ClubPalette.rgba(ClubPalette.Steel)))
            }
            val marker=ball(panel,"IndexFingertip$i",Vector3(.006f,.006f,.006f),Vector3.ZERO,mat)
            marker.enabled=false; markers.add(marker to mat)
        }
        placement()
        IndexMarkerSystem.attach(this)
        Log.i("SpatialLoop","SceneReady style=nightClub room=true dancers=4 female=2 lights=4 " +
            "monitors=0 ornaments=2 audioSources=roomPA panelCount=1 markers=2 indexOnly=true")
    }
    fun size(w: Float,h: Float) {
        if (w <= 0 || h <= 0) return
        width = w; panelHeight = h; placement()
        val key = "$w,$h"
        if (key != lastLoggedSize) {
            lastLoggedSize = key
            Log.i("SpatialLoop","PanelReady meters=$w,$h bodyBehind=.043 fingertipOnly=true tilt=-60")
        }
    }
    fun height(height: Float) {
        if (tableHeight == height) return
        tableHeight = height; placement()
        Log.i("SpatialLoop","HeightReady meters=$tableHeight")
    }
    private fun placement() {
        mount?.let { t(it).apply {
            position = Vector3(0f,tableHeight,-.63f)
            eulerAngles = EulerAngles(-60f,0f,0f)
        } }
        body?.let { t(it).scaleVector = Vector3(width+.055f,panelHeight+.055f,.054f) }
        outline?.let { t(it).scaleVector = Vector3(width+.065f,panelHeight+.065f,.096f) }
        val legHeight = (tableHeight-panelHeight*.25f-.05f).coerceAtLeast(.4f)
        legs.forEach { t(it).apply { position = Vector3(position.x,legHeight/2,-.63f); scaleVector = Vector3(1f,legHeight,1f) } }
        ornaments.forEachIndexed { i,entity ->
            t(entity).position=Vector3((if(i==0) -1 else 1)*(width/2+.14f),0f,0f)
        }
    }
    fun active(value: Boolean, gate: FingertipGate) {
        gate.active = value
        if (value && !started) {
            val hands = provider ?: HandTrackingProvider().also { provider = it }
            val mailbox=HandStream()
            stream=mailbox
            val callback=DataProvider.DataListener<HandTrackingData> { data ->
                // Copy only the two immutable position values on the provider worker.
                fun point(pose: HandPose?): MarkerPredictor.Point? {
                    if(pose==null || !validStagePoint(pose[HandJoint.Index.WRIST].position)) return null
                    val v=pose[HandJoint.Index.INDEX_TIP].position
                    return if(validStagePoint(v)) MarkerPredictor.Point(v.x,v.y,v.z) else null
                }
                val received=java.lang.System.nanoTime()
                val left=point(data.left); val right=point(data.right)
                val prior=mailbox.sample
                // Preserve the last two consecutive provider samples even if a scene frame skips callbacks.
                // Copy values, not a linked snapshot chain; memory remains bounded.
                mailbox.sample=HandSample(left,right,received,prior?.left,prior?.right,prior?.received ?: 0L)
            }
            listener=callback; hands.addListener(callback)
            val result = hands.start(); started = true
            Log.i("SpatialLoop","HandsReady result=$result input=IndexContact source=listener update=ECS predictionMaxMs=18 predictionMaxMeters=.012")
        } else if (!value && started) {
            listener?.let { provider?.removeListener(it) }; listener=null
            provider?.stop(); started = false; stream=null; consumed=null; predictors.forEach { it.reset() }; gate.reset()
            markers.forEach { it.first.enabled=false }
        }
    }
    fun bindInput(value: PanelInput) { input=value }
    fun playback(value: LoopStatus?) { room?.playback(value) }
    fun environmentActive(value: Boolean) { room?.active(value) }
    internal fun belongsTo(context: SceneUpdateContext) = root?.let { context.scene.findById(it.id)==it } == true
    internal fun updateFrame(context: SceneUpdateContext) {
        room?.update(context.deltaTime)
        updateMarkers(context)
    }
    internal fun updateMarkers(context: SceneUpdateContext) {
        val input=input ?: return
        val gate=input.gate
        val p = panel ?: return
        if (!started) return
        val now = SystemClock.uptimeMillis()
        val nanos=java.lang.System.nanoTime()
        val data = stream?.sample
        val tips = ArrayList<FingertipGate.Tip>(2)
        if(data!=null && data!==consumed && nanos-data.received<=50_000_000) {
            consumed=data
            for(marker in 0..1) {
                val raw=if(marker==0) data.left else data.right
                if(raw==null) { predictors[marker].reset(); continue }
                val prior=if(marker==0) data.priorLeft else data.priorRight
                if(prior!=null && data.priorReceived>0) predictors[marker].sample(prior,data.priorReceived)
                else predictors[marker].reset()
                predictors[marker].sample(raw,data.received)
                val point=p.convertPositionFrom(Vector3(raw.x,raw.y,raw.z),null)
                tips.add(FingertipGate.Tip(if(marker==0) 1 else 6,point.x,point.y,point.z))
            }
            // Contact receives the measured point, never the extrapolated display position.
            input.hands(now,tips)
        } else if(data==null || nanos-data.received>50_000_000) {
            predictors.forEach { it.reset() }
            // Expire real tracking without interrupting instrumentation's explicit contact samples.
            if(data!=null) input.hands(now,emptyList())
        }
        feedback(gate,nanos)
        input.feedback(now)
    }
    private fun validStagePoint(v: Vector3) = v.x.isFinite() && v.y.isFinite() && v.z.isFinite() &&
        (v.x!=0f || v.y!=0f || v.z!=0f)
    private fun feedback(gate: FingertipGate,nanos: Long) {
        val held=gate.heldTips()
        val p=panel ?: return
        markers.forEachIndexed { marker,(entity,mat) ->
            val id=if(marker==0) 1 else 6
            val stage=predictors[marker].position(nanos)
            val tip=stage?.let { p.convertPositionFrom(Vector3(it.x,it.y,it.z),null) }
            entity.enabled=tip!=null && tip.z>-.1f && tip.z<.3f && kotlin.math.abs(tip.x)<width/2+.15f &&
                kotlin.math.abs(tip.y)<panelHeight/2+.15f
            if(tip!=null) {
                t(entity).position=Vector3(tip.x,tip.y,tip.z)
                val mode=if(id in held) 2 else if(tip.z in 0f.. .06f) 1 else 0
                if(markerColors[id]!=mode) {
                    markerColors[id]=mode
                    mat.setBaseColor(Color4.fromSRGBHex(ClubPalette.rgba(when(mode) {
                        2->ClubPalette.Amber; 1->ClubPalette.Teal; else->ClubPalette.Steel
                    })))
                }
            }
        }
    }
    fun close() {
        IndexMarkerSystem.detach(this)
        listener?.let { provider?.removeListener(it) }; listener=null
        if (started) provider?.stop()
        started = false; stream=null; consumed=null; input=null; predictors.forEach { it.reset() }
        room?.close(); room=null
        root?.let { content?.removeEntity(it); if (it.valid) it.destroy() }
        root = null; mount = null; panel = null; body = null; outline=null; speakers = null; content = null
        resources.asReversed().forEach { it.close() }; resources.clear()
        legs.clear(); ornaments.clear(); markers.clear(); materials.clear(); meshCache.clear()
        Log.i("SpatialLoop","SceneDisposed")
    }
}

/** One public ECS frame hook for choreography and input; bound to the live scene identity. */
class IndexMarkerSystem : com.pico.spatial.core.ecs.System() {
    override fun update(context: SceneUpdateContext) {
        scenes.forEach { if(it.belongsTo(context)) it.updateFrame(context) }
    }
    companion object {
        private val scenes=linkedSetOf<LoopScene>()
        fun attach(scene: LoopScene) {
            if(scenes.isEmpty()) com.pico.spatial.core.ecs.System.register(IndexMarkerSystem::class.java)
            scenes.add(scene)
        }
        fun detach(scene: LoopScene) {
            scenes.remove(scene)
            if(scenes.isEmpty()) com.pico.spatial.core.ecs.System.unregister(IndexMarkerSystem::class.java)
        }
    }
}
