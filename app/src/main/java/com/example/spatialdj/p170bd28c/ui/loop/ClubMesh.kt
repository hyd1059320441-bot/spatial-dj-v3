package com.example.spatialdj.p170bd28c.ui.loop

import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.math.Vector3
import kotlin.math.*

/** Explicit geometry budgets: 44-triangle bevel and 168-triangle smooth sphere. */
internal object ClubMesh {
    private fun vector(v: ClubMotion.V) = Vector3(v.x.toFloat(),v.y.toFloat(),v.z.toFloat())
    private fun cross(a: ClubMotion.V,b: ClubMotion.V) =
        ClubMotion.V(a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x)
    fun bevel(): MeshModel {
        val vertices=mutableListOf<Vector3>(); val normals=mutableListOf<Vector3>()
        val indices=mutableListOf<Int>()
        fun polygon(points: List<ClubMotion.V>,normal: ClubMotion.V) {
            val n=normal.unit()
            val center=points.reduce { a,b -> a+b }*(1.0/points.size)
            val u=(points[0]-center).unit(); val v=cross(n,u)
            val sorted=points.sortedBy { atan2((it-center).dot(v),(it-center).dot(u)) }
            val start=vertices.size
            sorted.forEach { vertices.add(vector(it)); normals.add(vector(n)) }
            for(i in 1 until sorted.lastIndex) indices.addAll(listOf(start,start+i,start+i+1))
        }
        fun point(axis: Int,main: Double,u: Double,v: Double): ClubMotion.V {
            val p=DoubleArray(3); p[axis]=main; p[(axis+1)%3]=u; p[(axis+2)%3]=v
            return ClubMotion.V(p[0],p[1],p[2])
        }
        val a=.38
        for(axis in 0..2) for(s in listOf(-1,1)) {
            polygon(listOf(point(axis,s*.5,-a,-a),point(axis,s*.5,a,-a),
                point(axis,s*.5,a,a),point(axis,s*.5,-a,a)),point(axis,s.toDouble(),0.0,0.0))
        }
        for(axis in 0..2) for(s in listOf(-1,1)) for(t in listOf(-1,1)) {
            polygon(listOf(point(axis,-a,s*.5,t*a),point(axis,a,s*.5,t*a),
                point(axis,a,s*a,t*.5),point(axis,-a,s*a,t*.5)),
                point(axis,0.0,s.toDouble(),t.toDouble()))
        }
        for(x in listOf(-1,1)) for(y in listOf(-1,1)) for(z in listOf(-1,1)) {
            polygon(listOf(ClubMotion.V(x*.5,y*a,z*a),ClubMotion.V(x*a,y*.5,z*a),
                ClubMotion.V(x*a,y*a,z*.5)),ClubMotion.V(x.toDouble(),y.toDouble(),z.toDouble()))
        }
        return MeshModel(vertices,indices,normals)
    }
    fun sphere(): MeshModel {
        val vertices=mutableListOf<Vector3>(); val normals=mutableListOf<Vector3>()
        val indices=mutableListOf<Int>(); val sides=12; val rings=8
        fun add(x: Double,y: Double,z: Double) {
            val v=Vector3(x.toFloat(),y.toFloat(),z.toFloat()); vertices.add(v); normals.add(v)
        }
        add(0.0,1.0,0.0)
        for(r in 1 until rings) for(i in 0 until sides) {
            val p=PI*r/rings; val a=2*PI*i/sides
            add(sin(p)*cos(a),cos(p),sin(p)*sin(a))
        }
        val bottom=vertices.size; add(0.0,-1.0,0.0)
        for(i in 0 until sides) {
            val next=(i+1)%sides
            indices.addAll(listOf(0,1+next,1+i))
            for(r in 0 until rings-2) {
                val a=1+r*sides+i; val b=1+r*sides+next
                indices.addAll(listOf(a,b,a+sides,b,b+sides,a+sides))
            }
            indices.addAll(listOf(bottom,1+(rings-2)*sides+i,1+(rings-2)*sides+next))
        }
        return MeshModel(vertices,indices,normals)
    }
    /** Open 12-sided shaft along -Z. No opaque cap; alpha fades out toward the floor. */
    fun beam(): MeshModel {
        val vertices=mutableListOf<Vector3>()
        val indices=mutableListOf<Int>()
        val colors=mutableListOf<com.pico.spatial.core.math.Color4>()
        val rings=listOf(Triple(.0,.025, .25f),Triple(.12,.14,1f),
            Triple(.72,.78,.8f),Triple(1.0,1.0,.0f))
        for((z,r,alpha) in rings) for(i in 0 until 12) {
            val angle=2*PI*i/12
            vertices.add(Vector3((r*cos(angle)).toFloat(),(r*sin(angle)).toFloat(),-z.toFloat()))
            colors.add(com.pico.spatial.core.math.Color4(1f,1f,1f,alpha))
        }
        for(ring in 0 until 3) for(i in 0 until 12) {
            val a=ring*12+i; val b=ring*12+(i+1)%12
            indices.addAll(listOf(a,a+12,b,b,a+12,b+12))
        }
        return MeshModel(vertices,indices,colors=colors)
    }
}
