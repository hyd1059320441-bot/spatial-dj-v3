package com.example.spatialdj.p170bd28c.domain.audio

import kotlin.math.abs

/** Stereo ambience, kick sidechain and master knee. No lookahead, I/O or frame allocations. */
class BounceMix(private val rate: Int) {
    private val delayLeft=FloatArray(maxOf(1,(rate*.75).toInt()))
    private val delayRight=FloatArray(delayLeft.size)
    private val roomLeft=FloatArray(maxOf(1,(rate*.047).toInt()))
    private val roomRight=FloatArray(maxOf(1,(rate*.061).toInt()))
    private var delaySize=1
    private var delayPos=0
    private var roomLPos=0
    private var roomRPos=0
    private var duckFrames=Int.MAX_VALUE
    private var filteredL=0f
    private var filteredR=0f
    var left=0f; private set
    var right=0f; private set
    fun reset(bpm: Int) {
        delayLeft.fill(0f); delayRight.fill(0f); roomLeft.fill(0f); roomRight.fill(0f)
        delaySize=(rate*60.0/bpm*.75).toInt().coerceIn(1,delayLeft.size)
        delayPos=0; roomLPos=0; roomRPos=0; duckFrames=Int.MAX_VALUE
        filteredL=0f; filteredR=0f; left=0f; right=0f
    }
    fun kick() { duckFrames=0 }
    fun duck(): Float {
        val progress=(duckFrames.toFloat()/maxOf(1,(rate*.22).toInt())).coerceIn(0f,1f)
        return .19f+.81f*progress*progress
    }
    fun process(dryL: Float,dryR: Float,sendL: Float,sendR: Float,master: Float) {
        val echoL=delayLeft[delayPos]; val echoR=delayRight[delayPos]
        filteredL+=.22f*(echoL-filteredL); filteredR+=.22f*(echoR-filteredR)
        delayLeft[delayPos]=sendL*.22f+filteredR*.32f
        delayRight[delayPos]=sendR*.22f+filteredL*.32f
        val roomL=roomLeft[roomLPos]; val roomR=roomRight[roomRPos]
        roomLeft[roomLPos]=sendL*.11f+roomR*.42f
        roomRight[roomRPos]=sendR*.11f+roomL*.42f
        val duck=duck()
        left=limit((dryL+(filteredL*.60f+roomL*.45f)*duck)*master*1.6f)
        right=limit((dryR+(filteredR*.60f+roomR*.45f)*duck)*master*1.6f)
        if(++delayPos==delaySize) delayPos=0
        if(++roomLPos==roomLeft.size) roomLPos=0
        if(++roomRPos==roomRight.size) roomRPos=0
        if(duckFrames<Int.MAX_VALUE) duckFrames++
    }
    private fun limit(x: Float): Float {
        val over=abs(x)-.72f
        if(over<=0f) return x
        val magnitude=.72f+.25f*over/(.25f+over)
        return if(x<0f) -magnitude else magnitude
    }
}
