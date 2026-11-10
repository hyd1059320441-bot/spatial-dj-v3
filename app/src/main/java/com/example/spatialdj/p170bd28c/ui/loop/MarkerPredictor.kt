package com.example.spatialdj.p170bd28c.ui.loop

import kotlin.math.sqrt

/** Display-only extrapolation. Never feed the returned point to FingertipGate. */
class MarkerPredictor {
    data class Point(val x: Float,val y: Float,val z: Float)
    private var latest: Point?=null
    private var time=Long.MIN_VALUE
    private var vx=0f; private var vy=0f; private var vz=0f
    private var predictable=false
    fun reset() { latest=null; time=Long.MIN_VALUE; vx=0f; vy=0f; vz=0f; predictable=false }
    fun sample(point: Point,receivedNanos: Long) {
        if(!point.x.isFinite() || !point.y.isFinite() || !point.z.isFinite()) { reset(); return }
        if(receivedNanos<=time) return
        val before=latest
        val dt=(receivedNanos-time)/1e9
        if(before==null || dt !in .004.. .045) {
            predictable=false; vx=0f; vy=0f; vz=0f
        } else {
            val dx=point.x-before.x; val dy=point.y-before.y; val dz=point.z-before.z
            val distance=sqrt(dx*dx+dy*dy+dz*dz)
            val nx=(dx/dt).toFloat(); val ny=(dy/dt).toFloat(); val nz=(dz/dt).toFloat()
            val reversal=nx*vx+ny*vy+nz*vz < 0f
            predictable=distance<=.07f && distance/dt<=2.5 && !reversal
            if(distance>.07f || distance/dt>2.5) { vx=0f; vy=0f; vz=0f }
            else { vx=nx; vy=ny; vz=nz }
        }
        latest=point; time=receivedNanos
    }
    fun position(nowNanos: Long): Point? {
        val point=latest ?: return null
        val age=(nowNanos-time)/1e9
        if(age<0 || age>.050) return null
        if(!predictable || age>.025) return point
        // Covers a short tracking/display pipeline interval; never exceed 18 ms or 12 mm.
        val horizon=(age+.010).coerceAtMost(.018).toFloat()
        var dx=vx*horizon; var dy=vy*horizon; var dz=vz*horizon
        val distance=sqrt(dx*dx+dy*dy+dz*dz)
        if(distance>.012f) { val scale=.012f/distance; dx*=scale; dy*=scale; dz*=scale }
        return Point(point.x+dx,point.y+dy,point.z+dz)
    }
}
