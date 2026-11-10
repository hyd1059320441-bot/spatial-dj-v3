package com.example.spatialdj.p170bd28c.ui.loop

import android.util.Log
import com.example.spatialdj.p170bd28c.domain.model.LoopStatus
import com.example.spatialdj.p170bd28c.domain.model.SongPart
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import kotlin.math.*

/** Native lit room and four articulated humans. The owning LoopScene destroys entities before resources. */
class ClubRoom(parent: Entity) : AutoCloseable {
    private val resources = mutableListOf<Resource>()
    private val finishes = mutableMapOf<Long, PhysicallyBasedMaterial>()
    private val accents = mutableMapOf<Long, UnlitMaterial>()
    private fun <T: Resource> keep(value: T): T {
        value.toGlobal(); resources.add(value); return value
    }
    private val cube = keep(MeshResource.createBox(Vector3(1f,1f,1f)))
    private val round = keep(MeshResource.createWithMeshModel(ClubMesh.bevel(),name="ClubBevel44"))
    private val sphere = keep(MeshResource.createWithMeshModel(ClubMesh.sphere(),name="ClubSphere168"))
    private val cylinder = keep(MeshResource.createCylinder(1f,1f))
    private val shaft = keep(MeshResource.createWithMeshModel(ClubMesh.beam(),name="ClubHaze72"))
    private fun t(entity: Entity) = entity.components.get<TransformComponent>()!!
    private fun group(parent: Entity,name: String,at: Vector3 = Vector3.ZERO) = Entity().apply {
        setName(name); t(this).position=at; parent.addChild(this)
    }
    private fun color(hex: Long) = Color4.fromSRGBHex(ClubPalette.rgba(hex))
    private fun lit(hex: Long) = finishes.getOrPut(hex) {
        keep(PhysicallyBasedMaterial.create()).apply {
            setBaseColor(color(hex)); setMetallic(0f); setRoughness(.8f)
            setEmissiveColor(Color4(0f,0f,0f,1f))
            setCullingMode(MaterialCullingMode.BACK); setDepthTest(true); setDepthWrite(true)
        }
    }
    private fun accent(hex: Long) = accents.getOrPut(hex) {
        keep(UnlitMaterial.create()).apply { setBaseColor(color(hex)); setApplyToneMapping(false) }
    }
    private fun model(parent: Entity,name: String,mesh: MeshResource,size: Vector3,at: Vector3,
                      material: Material): Entity = ModelEntity(mesh,material).apply {
        setName(name); t(this).apply { position=at; scaleVector=size }; parent.addChild(this)
    }
    private fun box(parent: Entity,name: String,size: Vector3,at: Vector3,hex: Long,
                    rounded: Boolean = true) = model(parent,name,if(rounded) round else cube,size,at,lit(hex))
    private fun ball(parent: Entity,name: String,size: Vector3,at: Vector3,hex: Long) =
        model(parent,name,sphere,size,at,lit(hex))
    private fun stripe(parent: Entity,name: String,size: Vector3,at: Vector3,hex: Long) =
        model(parent,name,round,size,at,accent(hex))
    private val root = group(parent,"ClubRoom")
    private data class LegRig(val thigh: Entity,val shin: Entity,val knee: Entity,val shoe: Entity)
    private data class ArmRig(val shoulder: Entity,val elbow: Entity)
    private data class Dancer(val rig: Entity,val torso: Entity,val head: Entity,
                              val left: LegRig,val right: LegRig,val arms: List<ArmRig>)
    private val dancers = mutableListOf<Dancer>()
    private val wave = mutableListOf<Entity>()
    private val spots = mutableListOf<Pair<Entity,SpotLightComponent>>()
    private val shafts = mutableListOf<Pair<Entity,UnlitMaterial>>()
    var speakers: Pair<Entity,Entity>? = null
        private set
    private val clock = ClubBeatClock()
    private var status: LoopStatus? = null
    private var animationActive = false
    private var idleBeat = 0.0
    private var energy = 0.0
    private var frames = 0L
    private var closed = false
    private var lastBeat = 0.0

    init {
        val ambient=keep(TextureResource("club/night-ambient.ktx"))
        root.components.set(StageEnvironmentLightingComponent(ImageBasedLightSource.Single(ambient),ClubLighting.AMBIENT_EV))
        box(root,"RoomFloor",Vector3(6.9f,.12f,7.3f),Vector3(0f,-.06f,-1.8f),ClubPalette.Floor,false)
        box(root,"RoomCeiling",Vector3(6.9f,.16f,7.3f),Vector3(0f,2.93f,-1.8f),ClubPalette.Ceiling,false)
        box(root,"RoomBackWall",Vector3(6.9f,2.85f,.12f),Vector3(0f,1.425f,-5.46f),ClubPalette.Wall,false)
        box(root,"RoomFrontWall",Vector3(6.9f,2.85f,.12f),Vector3(0f,1.425f,1.86f),ClubPalette.Wall,false)
        repeat(6) { i ->
            box(root,"CeilingBeam$i",Vector3(6.78f,.075f,.09f),Vector3(0f,2.805f,-4.9f+i*1.1f),
                ClubPalette.Trim,false)
        }
        for(side in listOf(-1,1)) {
            box(root,"CeilingRail$side",Vector3(.10f,.075f,7.0f),Vector3(side*2.85f,2.80f,-1.8f),
                ClubPalette.Trim,false)
            stripe(root,"CeilingInset$side",Vector3(.018f,.016f,6.8f),Vector3(side*2.80f,2.76f,-1.8f),
                if(side<0) ClubPalette.Teal else ClubPalette.Blue)
        }
        for(side in listOf(-1,1)) {
            box(root,"RoomSideWall$side",Vector3(.12f,2.85f,7.3f),Vector3(side*3.46f,1.425f,-1.8f),
                ClubPalette.Wall,false)
            stripe(root,"SideLight$side",Vector3(.025f,.018f,4.4f),Vector3(side*3.39f,2.32f,-2.9f),
                if(side<0) ClubPalette.Teal else ClubPalette.Amber)
            repeat(5) { i ->
                box(root,"AcousticPanel$side-$i",Vector3(.08f,1.25f,.48f),
                    Vector3(side*3.37f,1.58f,-4.7f+i*.88f),ClubPalette.Upholstery)
            }
            val booth=group(root,"LoungeBooth$side",Vector3(side*2.94f,0f,-2.85f))
            box(booth,"BoothBase$side",Vector3(.50f,.36f,1.8f),Vector3(0f,.18f,0f),ClubPalette.Floor)
            box(booth,"BoothSeat$side",Vector3(.53f,.13f,1.82f),Vector3(0f,.42f,0f),ClubPalette.Upholstery)
            box(booth,"BoothBack$side",Vector3(.11f,.66f,1.84f),Vector3(side*.20f,.76f,0f),ClubPalette.Trim)
            box(booth,"BoothEnd$side",Vector3(.52f,.24f,.10f),Vector3(0f,.55f,.88f),ClubPalette.Upholstery)
            val table=group(root,"LoungeTable$side",Vector3(side*2.43f,0f,-2.85f))
            model(table,"TableStem$side",cylinder,Vector3(.04f,.64f,.04f),Vector3(0f,.32f,0f),lit(ClubPalette.Trim))
            model(table,"TableTop$side",cylinder,Vector3(.22f,.035f,.22f),Vector3(0f,.655f,0f),lit(ClubPalette.Ink))
            stripe(root,"FloorEdge$side",Vector3(.014f,.008f,6.8f),Vector3(side*3.36f,.008f,-1.8f),ClubPalette.Blue)
        }
        entrance()
        val bar=group(root,"ClubBar",Vector3(3.02f,0f,.05f))
        box(bar,"BarCounter",Vector3(.48f,.94f,1.55f),Vector3(0f,.47f,0f),ClubPalette.Upholstery)
        box(bar,"BarTop",Vector3(.62f,.07f,1.65f),Vector3(-.05f,.975f,0f),ClubPalette.Ink)
        stripe(bar,"BarAccent",Vector3(.012f,.018f,1.48f),Vector3(-.25f,.77f,0f),ClubPalette.Amber)
        box(bar,"BarShelf",Vector3(.18f,.055f,1.30f),Vector3(.22f,1.75f,0f),ClubPalette.Trim)
        stripe(bar,"BarShelfBacklight",Vector3(.015f,.012f,1.20f),Vector3(.12f,1.77f,0f),ClubPalette.Amber)
        repeat(5) { i ->
            val hex=if(i%2==0) 0xFF24483FL else 0xFF5A4931L
            model(bar,"BarBottle$i",cylinder,Vector3(.032f,.16f,.032f),
                Vector3(.20f,1.86f,(i-2)*.21f),lit(hex))
            model(bar,"BarBottleNeck$i",cylinder,Vector3(.015f,.07f,.015f),
                Vector3(.20f,1.97f,(i-2)*.21f),lit(hex))
        }
        box(root,"DanceFloor",Vector3(4.15f,.08f,3.15f),Vector3(0f,.04f,-3.4f),ClubPalette.Floor)
        stripe(root,"DanceFloorFront",Vector3(4.10f,.01f,.015f),Vector3(0f,.081f,-1.83f),ClubPalette.Teal)
        stripe(root,"DanceFloorBack",Vector3(4.10f,.01f,.015f),Vector3(0f,.081f,-4.97f),ClubPalette.Blue)
        for(i in -2..2) box(root,"DanceFloorSeam$i",Vector3(.009f,.007f,3.08f),
            Vector3(i*.65f,.081f,-3.4f),ClubPalette.Upholstery,false)
        box(root,"WaveBackdrop",Vector3(2.9f,.88f,.08f),Vector3(0f,2.05f,-5.34f),ClubPalette.Floor)
        repeat(13) { i ->
            wave.add(stripe(root,"WallWave$i",Vector3(.085f,.32f,.03f),
                Vector3((i-6)*.19f,2.05f,-5.285f),if(i==6) ClubPalette.Amber else ClubPalette.Teal))
        }
        speakers=paStack(-1) to paStack(1)
        val key=group(root,"ClubKeyLight")
        t(key).eulerAngles=EulerAngles(-40f,45f,0f)
        key.components.set(DirectionalLightComponent(Color4(1f,.98f,.96f,1f),ClubLighting.FILL_LUX,false))
        // Opposing diagonal fills reach all four walls without a large overlapping local-light budget.
        val fill=group(root,"ClubSideFill0")
        t(fill).eulerAngles=EulerAngles(-40f,225f,0f)
        fill.components.set(DirectionalLightComponent(Color4.WHITE,ClubLighting.SIDE_FILL_LUX,false))
        listOf(-.35f,-2.65f,-4.65f).forEachIndexed { i,z ->
            box(root,"CeilingPanelHousing$i",Vector3(.82f,.045f,.34f),Vector3(0f,2.81f,z),ClubPalette.Trim)
            stripe(root,"CeilingDiffusePanel$i",Vector3(.72f,.025f,.25f),Vector3(0f,2.775f,z),0xFFF1DFC3L)
        }
        repeat(ClubLighting.origins.size) { i ->
            val hex=if(i==0) ClubPalette.Teal else ClubPalette.Blue
            val origin=ClubLighting.origins[i]
            box(root,"LightMount$i",Vector3(.065f,.14f,.065f),
                Vector3(origin.x.toFloat(),2.78f,origin.z.toFloat()),ClubPalette.Trim)
            val lamp=group(root,"DanceSpot$i",vector(origin))
            val fixture=box(lamp,"SpotHousing$i",Vector3(.22f,.20f,.30f),Vector3.ZERO,ClubPalette.Ink)
            stripe(fixture,"SpotLens$i",Vector3(.18f,.13f,.02f),Vector3(0f,0f,-.16f),hex)
            // Neutral illumination keeps white skin white; colored shafts/lenses provide the club accents.
            val component=SpotLightComponent(Color4(1f,.98f,.96f,1f),700f,ClubLighting.RADIUS,
                ClubLighting.INNER_ANGLE,ClubLighting.OUTER_ANGLE,false)
            lamp.components.set(component); spots.add(lamp to component)
            val haze=keep(UnlitMaterial.create(BlendingMode.ADD)).apply {
                setBaseColor(color(hex)); setOpacity(.04f); setApplyToneMapping(false)
                setCullingMode(MaterialCullingMode.NONE); setDepthTest(true); setDepthWrite(false)
            }
            val cone=model(lamp,"LightShaft$i",shaft,Vector3(.5f,.5f,2.8f),Vector3(0f,0f,-.16f),haze)
            shafts.add(cone to haze)
        }
        ClubLighting.positions.forEachIndexed { i,at -> dancers.add(dancer(i,vector(at))) }
        animate(0.0,0.0)
        Log.i("SpatialLoop","ClubReady room=6.8x7.2x2.85 dancers=4 female=2 styles=4 beatOffsets=0 " +
            "lights=4 directionalFills=2 movingSpots=2 shafts=2 fillLux=${ClubLighting.FILL_LUX} " +
            "sideFillLux=${ClubLighting.SIDE_FILL_LUX} ambientEV=${ClubLighting.AMBIENT_EV} " +
            "skin=white neutralSpots=true spotMode=danceFloorSweep entrance=true PAstacks=2 shadows=0")
    }

    private fun entrance() {
        val door=group(root,"ClubEntrance",Vector3(-3.385f,0f,-.20f))
        box(door,"DoorFrame",Vector3(.07f,2.23f,1.02f),Vector3(0f,1.115f,0f),ClubPalette.Trim,false)
        box(door,"DoorLeaf",Vector3(.04f,2.08f,.89f),Vector3(.045f,1.04f,0f),ClubPalette.Floor,false)
        box(door,"DoorWindowFrame",Vector3(.025f,.60f,.20f),Vector3(.075f,1.45f,0f),ClubPalette.Trim)
        stripe(door,"DoorGlass",Vector3(.012f,.55f,.145f),Vector3(.092f,1.45f,0f),0xFF203C47L)
        box(door,"DoorHandle",Vector3(.065f,.14f,.035f),Vector3(.11f,1.04f,.31f),ClubPalette.Amber)
        stripe(door,"ExitSignBacking",Vector3(.03f,.18f,.46f),Vector3(.08f,2.35f,0f),0xFF1C6C5AL)
        // A lit exit-door pictogram and arrow, readable without another UI panel.
        stripe(door,"ExitDoorPictogram",Vector3(.02f,.12f,.06f),Vector3(.103f,2.35f,-.14f),ClubPalette.Surface)
        stripe(door,"ExitArrow",Vector3(.02f,.02f,.17f),Vector3(.103f,2.35f,.045f),ClubPalette.Surface)
        for(sign in listOf(-1,1)) {
            val edge=stripe(door,"ExitArrowTip$sign",Vector3(.02f,.06f,.02f),
                Vector3(.103f,2.35f+sign*.022f,.13f),ClubPalette.Surface)
            t(edge).eulerAngles=EulerAngles(sign*45f,0f,0f)
        }
    }
    private fun paStack(side: Int): Entity {
        val stack=group(root,"PAStack$side",Vector3(side*2.65f,0f,-4.8f))
        t(stack).eulerAngles=EulerAngles(0f,-side*26f,0f)
        box(stack,"PASubwoofer$side",Vector3(.60f,.60f,.55f),Vector3(0f,.30f,0f),ClubPalette.Floor)
        box(stack,"PASubGrille$side",Vector3(.52f,.50f,.022f),Vector3(0f,.31f,.282f),ClubPalette.Upholstery)
        box(stack,"PAMount$side",Vector3(.085f,.26f,.085f),Vector3(0f,.73f,0f),ClubPalette.Trim)
        val cabinet=group(stack,"PAMain$side",Vector3(0f,1.25f,0f))
        t(cabinet).eulerAngles=EulerAngles(5f,0f,0f)
        box(cabinet,"PACabinet$side",Vector3(.38f,.78f,.34f),Vector3.ZERO,ClubPalette.Floor)
        box(cabinet,"PAGrille$side",Vector3(.32f,.70f,.018f),Vector3(0f,0f,.177f),ClubPalette.Upholstery)
        box(cabinet,"PAHorn$side",Vector3(.23f,.15f,.015f),Vector3(0f,.20f,.190f),ClubPalette.Trim)
        val driver=model(cabinet,"PAWoofer$side",cylinder,Vector3(.12f,.014f,.12f),
            Vector3(0f,-.12f,.195f),lit(ClubPalette.Ink))
        t(driver).eulerAngles=EulerAngles(90f,0f,0f)
        stripe(cabinet,"PAStatus$side",Vector3(.025f,.008f,.01f),Vector3(.115f,-.31f,.196f),ClubPalette.Teal)
        return cabinet
    }

    private fun dancer(i: Int,at: Vector3): Dancer {
        val rig=group(root,"Dancer$i",at)
        t(rig).eulerAngles=EulerAngles(0f,listOf(12f,6f,-8f,-16f)[i],0f)
        val look=ClubCast.people[i]
        val shirt=look.top; val skin=look.skin; val hair=look.hair
        val shoulderWidth=if(look.female) .195f else .235f
        val torso=group(rig,"Dancer${i}Torso")
        box(torso,"Dancer${i}Jacket",Vector3(if(look.female) .32f else .40f,.40f,
            if(look.female) .20f else .23f),Vector3(0f,.22f,0f),shirt)
        box(torso,"Dancer${i}Waist",Vector3(if(look.female) .275f else .32f,.10f,.21f),
            Vector3(0f,.01f,0f),ClubPalette.Ink)
        box(torso,"Dancer${i}Collar",Vector3(.13f,.065f,.16f),Vector3(0f,.44f,0f),ClubPalette.Floor)
        box(torso,"Dancer${i}ShirtBadge",Vector3(.12f,.065f,.015f),Vector3(0f,.28f,.125f),ClubPalette.Surface)
        if(look.female) {
            box(torso,"Dancer${i}JacketHem",Vector3(.29f,.055f,.207f),Vector3(0f,.07f,0f),ClubPalette.Trim)
        } else {
            box(torso,"Dancer${i}Zipper",Vector3(.014f,.30f,.016f),Vector3(0f,.21f,.132f),ClubPalette.Ink)
        }
        val head=group(torso,"Dancer${i}Head",Vector3(0f,.49f,0f))
        ball(head,"Dancer${i}Face",Vector3(if(look.female) .14f else .155f,.18f,.14f),Vector3(0f,.14f,0f),skin)
        for(side in listOf(-1,1)) {
            ball(head,"Dancer${i}Ear$side",Vector3(.022f,.038f,.025f),
                Vector3(side*if(look.female) .14f else .152f,.14f,0f),skin)
            ball(head,"Dancer${i}Eye$side",Vector3(.013f,.018f,.010f),Vector3(side*.052f,.17f,.131f),ClubPalette.Ink)
            box(head,"Dancer${i}Brow$side",Vector3(.034f,.009f,.011f),Vector3(side*.052f,.205f,.125f),hair)
            if(look.female) {
                box(head,"Dancer${i}Earring$side",Vector3(.014f,.032f,.014f),
                    Vector3(side*.143f,.088f,0f),ClubPalette.Amber)
                box(head,"Dancer${i}Eyelash$side",Vector3(.029f,.007f,.009f),
                    Vector3(side*.055f,.183f,.142f),hair)
            }
        }
        ball(head,"Dancer${i}Nose",Vector3(.021f,.025f,.029f),Vector3(0f,.13f,.14f),skin)
        box(head,"Dancer${i}Smile",Vector3(.044f,.011f,.010f),Vector3(0f,.079f,.128f),
            if(look.female) 0xFF9E6262L else ClubPalette.Ink)
        ball(head,"Dancer${i}Hair",Vector3(.16f,.078f,.145f),Vector3(0f,.285f,-.015f),hair)
        if(i==1) {
            ball(head,"Dancer${i}Cap",Vector3(.165f,.08f,.155f),Vector3(0f,.305f,-.01f),shirt)
            box(head,"Dancer${i}CapVisor",Vector3(.25f,.025f,.15f),Vector3(0f,.28f,.105f),shirt)
        } else {
            for(j in 0..2) box(head,"Dancer${i}Fringe$j",Vector3(.055f,.072f,.052f),
                Vector3((j-1)*.066f,.265f,.102f),hair)
        }
        if(look.female) {
            if(i==0) {
                ball(head,"Dancer${i}Ponytail",Vector3(.06f,.185f,.065f),Vector3(0f,.11f,-.16f),hair)
                box(head,"Dancer${i}HairTie",Vector3(.065f,.027f,.04f),Vector3(0f,.28f,-.135f),shirt)
            } else {
                for(side in listOf(-1,1)) {
                    ball(head,"Dancer${i}LongHair$side",Vector3(.055f,.23f,.078f),
                        Vector3(side*.13f,.055f,-.056f),hair)
                }
            }
        }
        fun leg(side: Int): LegRig {
            val thigh=box(rig,"Dancer${i}Thigh$side",Vector3(.12f,.36f,.14f),Vector3.ZERO,ClubPalette.Ink)
            val shin=box(rig,"Dancer${i}Shin$side",Vector3(.105f,.36f,.12f),Vector3.ZERO,ClubPalette.Ink)
            val knee=ball(rig,"Dancer${i}Knee$side",Vector3(.059f,.059f,.067f),Vector3.ZERO,ClubPalette.Ink)
            val shoe=group(rig,"Dancer${i}Shoe$side")
            box(shoe,"Dancer${i}Sole$side",Vector3(.15f,.04f,.25f),Vector3(0f,-.08f,.045f),ClubPalette.Surface)
            box(shoe,"Dancer${i}ShoeUpper$side",Vector3(.135f,.065f,.23f),Vector3(0f,-.028f,.043f),shirt)
            return LegRig(thigh,shin,knee,shoe)
        }
        val arms=listOf(-1,1).map { side ->
            val shoulder=group(torso,"Dancer${i}Shoulder$side",Vector3(side*shoulderWidth,.39f,0f))
            ball(shoulder,"Dancer${i}ShoulderJoint$side",Vector3(.064f,.064f,.064f),Vector3.ZERO,shirt)
            box(shoulder,"Dancer${i}UpperArm$side",Vector3(if(look.female) .085f else .105f,.27f,.10f),
                Vector3(0f,-.135f,0f),shirt)
            val elbow=group(shoulder,"Dancer${i}Elbow$side",Vector3(0f,-.27f,0f))
            ball(elbow,"Dancer${i}ElbowJoint$side",Vector3(.057f,.057f,.057f),Vector3.ZERO,skin)
            box(elbow,"Dancer${i}Forearm$side",Vector3(.08f,.25f,.085f),Vector3(0f,-.125f,0f),skin)
            ball(elbow,"Dancer${i}Hand$side",Vector3(.049f,.068f,.040f),Vector3(0f,-.26f,0f),skin)
            ArmRig(shoulder,elbow)
        }
        return Dancer(rig,torso,head,leg(-1),leg(1),arms)
    }

    fun playback(value: LoopStatus?) {
        if(value === status) return
        status=value
        clock.sample(value?.elapsedBeats ?: 0.0,value?.bpm ?: 110,value?.running==true,
            java.lang.System.nanoTime())
    }
    fun active(value: Boolean) { animationActive=value }
    fun update(deltaTime: Float) {
        if(closed || !animationActive) return
        val dt=if(deltaTime.isFinite()) deltaTime.coerceIn(0f,.05f).toDouble() else 0.0
        idleBeat+=dt*.7
        val s=status
        val playing=s?.running==true
        val section=s?.auto?.section
        val target=when {
            !playing || s!!.countIn>0 -> .06
            section?.contains("Intro",true)==true || s.part==SongPart.Intro -> .38
            section?.contains("Break",true)==true || s.part==SongPart.Breakdown -> .25
            section?.contains("Build",true)==true || s.part==SongPart.BuildUp -> .68
            section?.contains("Outro",true)==true || s.part==SongPart.Outro -> .32
            else -> .95
        }
        energy+=(target-energy)*min(1.0,dt*5)
        val beat=if(playing) clock.beat(java.lang.System.nanoTime()) else idleBeat
        lastBeat=beat
        animate(beat,energy)
        frames++
    }
    private fun vector(v: ClubMotion.V) = Vector3(v.x.toFloat(),v.y.toFloat(),v.z.toFloat())
    private fun bone(entity: Entity,from: ClubMotion.V,to: ClubMotion.V) {
        val d=(to-from).unit()
        val qx=-d.z; val qz=d.x; val qw=1-d.y
        val norm=sqrt(qx*qx+qz*qz+qw*qw)
        t(entity).apply {
            position=vector((from+to)*.5)
            quaternion=if(norm<1e-6) Quat(1f,0f,0f,0f)
                else Quat((qx/norm).toFloat(),0f,(qz/norm).toFloat(),(qw/norm).toFloat())
        }
    }
    private fun animate(beat: Double,amount: Double) {
        dancers.forEachIndexed { i,dancer ->
            val pose=ClubMotion.pose(beat,amount,i)
            t(dancer.torso).apply {
                position=vector(pose.hips); eulerAngles=EulerAngles(pose.torsoX,pose.torsoY,pose.torsoZ)
            }
            t(dancer.head).eulerAngles=EulerAngles(pose.headX,pose.headY,0f)
            listOf(dancer.left to pose.left,dancer.right to pose.right).forEach { (rig,leg) ->
                bone(rig.thigh,leg.hip,leg.knee); bone(rig.shin,leg.knee,leg.ankle)
                t(rig.knee).position=vector(leg.knee); t(rig.shoe).position=vector(leg.ankle)
            }
            listOf(pose.leftArm,pose.rightArm).forEachIndexed { side,arm ->
                t(dancer.arms[side].shoulder).eulerAngles=EulerAngles(arm.shoulderX,0f,arm.shoulderZ)
                t(dancer.arms[side].elbow).eulerAngles=EulerAngles(arm.elbowX,0f,0f)
            }
        }
        val pulse=((1+cos(beat*2*PI))/2).pow(2)
        spots.forEachIndexed { i,(entity,light) ->
            val origin=ClubLighting.origins[i]
            val direction=ClubLighting.target(i,beat,amount)-origin
            val d=direction.unit()
            val norm=sqrt(d.y*d.y+d.x*d.x+(1-d.z).pow(2))
            t(entity).quaternion=Quat((d.y/norm).toFloat(),(-d.x/norm).toFloat(),0f,((1-d.z)/norm).toFloat())
            light.intensity=(400+amount*(250+200*pulse)).toFloat()
            val distance=(direction.length()-.16).toFloat()
            val radius=distance*.26f
            t(shafts[i].first).scaleVector=Vector3(radius,radius,distance)
            shafts[i].second.setOpacity((.023+.018*amount*(.5+.5*pulse)).toFloat())
        }
        wave.forEachIndexed { i,entity ->
            val h=(.10+.19*(1+cos((i-6)*.48))+
                amount*.12*(.5+.5*sin(beat*PI-i*.6))).toFloat()
            t(entity).scaleVector=Vector3(.085f,h,.03f)
        }
    }
    override fun close() {
        if(closed) return
        closed=true; animationActive=false; status=null; speakers=null
        if(root.valid) root.destroy()
        resources.asReversed().forEach { it.close() }; resources.clear()
        dancers.clear(); spots.clear(); shafts.clear(); wave.clear(); finishes.clear(); accents.clear()
        Log.i("SpatialLoop","ClubDisposed frames=$frames")
    }
}
