package com.example.spatialdj.p170bd28c.ui.loop

import kotlin.math.*

/** Pure choreography in metres. Feet are in rig space; hips do not rotate the IK chains. */
object ClubMotion {
    data class V(val x: Double, val y: Double, val z: Double) {
        operator fun plus(v: V) = V(x+v.x, y+v.y, z+v.z)
        operator fun minus(v: V) = V(x-v.x, y-v.y, z-v.z)
        operator fun times(s: Double) = V(x*s, y*s, z*s)
        fun dot(v: V) = x*v.x+y*v.y+z*v.z
        fun length() = sqrt(dot(this))
        fun unit() = this * (1.0/length())
    }
    data class Leg(val hip: V, val knee: V, val ankle: V)
    data class Arm(val shoulderX: Float, val shoulderZ: Float, val elbowX: Float)
    data class Pose(val hips: V, val left: Leg, val right: Leg,
                    val torsoX: Float, val torsoZ: Float, val headY: Float,
                    val leftArm: Arm, val rightArm: Arm,
                    val torsoY: Float = 0f, val headX: Float = 0f)
    enum class Style { PocketGroove, StepTouch, HouseStep, ShoulderGroove }
    fun style(character: Int) = Style.entries[character.mod(4)]
    const val THIGH = .36
    const val SHIN = .36
    const val ANKLE = .10
    private fun phase(beat: Double, length: Double) = ((beat % length)+length)%length

    /** Reach-clamped two-bone solver, bending forward (+Z), including vertical targets. */
    fun leg(hip: V, target: V, thigh: Double = THIGH, shin: Double = SHIN): Leg {
        require(thigh>0 && shin>0)
        val delta=target-hip
        val raw=delta.length()
        val direction=if(raw<1e-9) V(0.0,-1.0,0.0) else delta*(1/raw)
        val distance=raw.coerceIn(abs(thigh-shin)+1e-6,thigh+shin-1e-6)
        val along=(thigh*thigh-shin*shin+distance*distance)/(2*distance)
        val preferred=V(0.0,0.0,1.0)
        var bend=preferred-direction*preferred.dot(direction)
        if(bend.length()<1e-6) bend=V(1.0,0.0,0.0)-direction*direction.x
        val knee=hip+direction*along+bend.unit()*sqrt(max(0.0,thigh*thigh-along*along))
        return Leg(hip,knee,hip+direction*distance)
    }

    /** Foot changes in the last half-beat and is already grounded on every integer beat. */
    fun foot(beat: Double, side: Int, energy: Double, character: Int = 0): V {
        require(side == -1 || side == 1)
        val p=phase(beat,8.0); val n=floor(p).toInt(); val f=p-n
        val e=energy.coerceIn(0.0,1.0)
        val kind=style(character)
        fun key(index: Int): V {
            val k=index.mod(8)
            return when(kind) {
                Style.PocketGroove -> V(side*.13,ANKLE,0.0)
                Style.StepTouch -> {
                    val outer=if(side==-1) k.mod(4) in 0..1 else k.mod(4) in 1..2
                    V(side*(.13+if(outer) .085*e else 0.0),ANKLE,0.0)
                }
                Style.HouseStep -> {
                    val pattern=if(side==-1) doubleArrayOf(-.06,-.06,.07,.07,0.0,0.0,-.04,-.04)
                        else doubleArrayOf(0.0,.07,.07,-.06,-.06,0.0,0.0,.04)
                    V(side*.13,ANKLE,pattern[k]*e)
                }
                Style.ShoulderGroove -> V(side*.145,ANKLE,
                    (if((k+if(side==1) 1 else 0).mod(4)<2) -.018 else .018)*e)
            }
        }
        val a=key(n); val b=key(n+1)
        val moves=(b-a).length()>1e-7
        val toeTap=!moves && n.mod(2)==if(side==-1) 0 else 1
        val u=((f-.5)/.5).coerceIn(0.0,1.0)
        val smooth=u*u*(3-2*u)
        val lift=when {
            moves -> (if(kind==Style.HouseStep) .032 else .022)*e*sin(PI*u)
            toeTap -> .011*e*sin(PI*u)
            else -> 0.0
        }
        return a+(b-a)*smooth+V(0.0,lift,0.0)
    }

    fun pose(beat: Double, energy: Double, character: Int): Pose {
        val e=energy.coerceIn(0.0,1.0)
        val b=phase(beat,8.0)
        val hit=((1+cos(b*2*PI))/2).pow(2) // Down on the kick, not the offbeat.
        val kind=style(character)
        val sway=sin(b*PI/2)
        val hips=when(kind) {
            Style.PocketGroove -> V(.035*e*sway,.749-.019*e*hit,.009*e*sin(b*PI))
            Style.StepTouch -> V(.044*e*sin(b*PI/2-PI/4),.749-.016*e*hit,0.0)
            Style.HouseStep -> V(.022*e*sin(b*PI),.749-.022*e*hit,.018*e*cos(b*PI/2))
            Style.ShoulderGroove -> V(.016*e*sway,.749-.012*e*hit,.006*e*sin(b*PI))
        }
        val hipSpacing=if(kind==Style.ShoulderGroove) .145 else .13
        fun limb(side: Int)=leg(hips+V(side*hipSpacing,0.0,0.0),foot(b,side,e,character))
        fun arm(side: Int): Arm = when(kind) {
            Style.PocketGroove -> Arm((-10-7*e*hit).toFloat(),(side*(10+5*e*sway)).toFloat(),
                (-58-9*e*sin(b*PI)).toFloat())
            Style.StepTouch -> Arm((-9+9*e*cos(b*PI/2)*side).toFloat(),(side*(13+5*e*hit)).toFloat(),
                (-36-8*e*hit).toFloat())
            Style.HouseStep -> Arm((-18+12*e*sin(b*PI)*side).toFloat(),(side*(9+7*e*hit)).toFloat(),
                (-64+12*e*cos(b*PI)*side).toFloat())
            Style.ShoulderGroove -> Arm((-10-9*e*sin(b*PI/2+side*PI/2)).toFloat(),
                (side*(15+9*e*cos(b*PI/2))).toFloat(),(-72+10*e*sin(b*PI/2)*side).toFloat())
        }
        val lean=when(kind) { Style.HouseStep -> -5-3*e*hit; else -> -2-2*e*hit }
        val roll=when(kind) { Style.ShoulderGroove -> 5*e*sin(b*PI/2); else -> 3*e*sway }
        val twist=when(kind) { Style.PocketGroove -> 7*e*sway; Style.ShoulderGroove -> 8*e*cos(b*PI/2); else -> 3*e*sway }
        return Pose(hips,limb(-1),limb(1),lean.toFloat(),roll.toFloat(),(3*e*sway).toFloat(),
            arm(-1),arm(1),twist.toFloat(),(3+5*e*hit).toFloat())
    }
}

/** A new audio sample snapshot is accepted once; extrapolation cannot drift past 50ms. */
class ClubBeatClock {
    private var lastBeat = 0.0
    private var received = 0L
    private var bpm = 110
    private var running = false
    fun sample(beat: Double, tempo: Int, playing: Boolean, nanos: Long) {
        lastBeat=beat; bpm=tempo; running=playing; received=nanos
    }
    fun beat(nanos: Long): Double = lastBeat+if(running)
        (nanos-received).coerceIn(0,50_000_000)/1e9*bpm/60 else 0.0
}
