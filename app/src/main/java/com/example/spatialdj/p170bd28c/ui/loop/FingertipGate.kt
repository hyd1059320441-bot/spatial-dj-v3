package com.example.spatialdj.p170bd28c.ui.loop

import kotlin.math.*

/** Contact of tracked fingertip spheres with measured panel faces, in panel-local meters.
 * No native pointer, pinch, aim, palm or pose recognition participates in this path. */
class FingertipGate {
    data class Tip(val id: Int, val x: Float, val y: Float, val z: Float)
    data class Rect(val x: Float, val y: Float, val width: Float, val height: Float) {
        fun contains(px: Float, py: Float, mx: Float = 0f, my: Float = mx) =
            px >= x-mx-.01f && px <= x+width+mx+.01f && py >= y-my-.01f && py <= y+height+my+.01f
    }
    data class Surface(val id: String, val rect: Rect, val slider: Boolean = false, val insetPixels: Float = 18f)
    data class Hit(val id: String, val value: Float, val down: Boolean, val up: Boolean = false)
    private val captures = mutableMapOf<Int,Surface>()
    private val blocked = mutableSetOf<Int>()
    // A continuous, shallow lift may rearm a different button. Tracking loss,
    // underside contact and slider ownership always require full retraction.
    private val blockedFaces = mutableMapOf<Int,Surface>()
    private var previous = emptyMap<Int,Tip>()
    private var tips = emptyList<Tip>()
    private var sampleTime = Long.MIN_VALUE
    var widthMeters = 0f
    var heightMeters = 0f
    var widthPixels = 0f
    var heightPixels = 0f
    var active = false
    private fun measured() = listOf(widthMeters,heightMeters,widthPixels,heightPixels).all { it.isFinite() && it>0 }
    private fun fresh(now: Long) = sampleTime != Long.MIN_VALUE && now>=sampleTime && now-sampleTime<=100
    private fun px(t: Tip) = (t.x/widthMeters+.5f)*widthPixels
    private fun py(t: Tip) = (.5f-t.y/heightMeters)*heightPixels
    private fun value(s: Surface, x: Float) =
        ((x-s.rect.x-s.insetPixels)/max(1f,s.rect.width-2*s.insetPixels)).coerceIn(0f,1f)
    private fun release(id: Int) = captures.remove(id)?.let { Hit(it.id,0f,false,true) }
    private fun block(id: Int,face: Surface? = null) {
        blocked.add(id)
        if(face==null) blockedFaces.remove(id) else blockedFaces[id]=face
    }

    fun hands(now: Long, sample: List<Tip>, surfaces: List<Surface>): List<Hit> {
        val hits = ArrayList<Hit>()
        if (!active || !measured()) return reset()
        val current = sample.filter { it.id in INDEX_IDS && it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }
            .distinctBy { it.id }.associateBy { it.id }
        val old = if (fresh(now)) previous else emptyMap()
        blockedFaces.keys.toList().filter { it !in current || it !in old }.forEach { blockedFaces.remove(it) }
        for (id in captures.keys.toList()) {
            val t = current[id]
            val s = captures.getValue(id)
            val present = surfaces.any { it == s }
            val inRow = t != null && s.rect.contains(px(t),py(t),
                (if(s.slider) .06f else .008f)/widthMeters*widthPixels,
                .008f/heightMeters*heightPixels)
            if (t==null || !present || !inRow || t.z>RELEASE_Z || t.z<BACK_LIMIT) {
                release(id)?.let(hits::add)
                block(id,if(t!=null && present && !inRow && !s.slider && id in old && t.z>=0f) s else null)
            } else if (s.slider) hits.add(Hit(s.id,value(s,px(t)),false))
        }
        // Loss of a tip releases it, but reacquisition while inside a key must not strike it.
        blocked.removeAll { id ->
            val t=current[id]
            val face=blockedFaces[id]
            val ready=t!=null && (t.z>RELEASE_Z || (face!=null && id in old &&
                t.z>=CROSS_CONTROL_REARM_Z && !face.rect.contains(px(t),py(t),
                    .008f/widthMeters*widthPixels,.008f/heightMeters*heightPixels)))
            if(ready) blockedFaces.remove(id)
            ready
        }
        for ((id,t) in current) {
            if (id in captures || id in blocked) continue
            val before = old[id]
            if (before == null && t.z<0f) { block(id); continue }
            val direct = t.z in -.009f..CONTACT_Z &&
                (before == null || before.z >= -.009f)
            var contact = t
            val travel = before?.let { sqrt((t.x-it.x).pow(2)+(t.y-it.y).pow(2)+(t.z-it.z).pow(2)) }
            val swept = before != null && before.z>CONTACT_Z && t.z<=CONTACT_Z &&
                travel!!<=.18f && now-sampleTime<=100
            if (swept) {
                val a = (before!!.z-CONTACT_Z)/(before.z-t.z)
                contact = Tip(id,before.x+(t.x-before.x)*a,before.y+(t.y-before.y)*a,CONTACT_Z)
            }
            if (!direct && !swept) continue
            val s = surfaces.firstOrNull { it.rect.contains(px(contact),py(contact)) } ?: continue
            if (s.slider && captures.values.any { it.id==s.id }) { block(id); continue }
            captures[id] = s
            hits.add(Hit(s.id,value(s,px(contact)),true))
            if (t.z<BACK_LIMIT || !s.rect.contains(px(t),py(t),
                    .008f/widthMeters*widthPixels,.008f/heightMeters*heightPixels)) {
                release(id)?.let(hits::add); block(id)
            }
        }
        previous = current; tips = current.values.toList(); sampleTime = now
        return hits
    }
    fun expire(now: Long): List<Hit> {
        if (active && measured() && fresh(now)) return emptyList()
        val hits = captures.keys.toList().mapNotNull { block(it); release(it) }
        blockedFaces.clear()
        tips = emptyList(); previous = emptyMap()
        return hits
    }
    fun hover(now: Long, surfaces: List<Surface>): Set<String> {
        if (!active || !measured() || !fresh(now)) return emptySet()
        return surfaces.filter { s -> tips.any { it.z in -.009f.. .06f && s.rect.contains(px(it),py(it)) } }
            .mapTo(mutableSetOf()) { it.id }
    }
    fun held() = captures.values.mapTo(mutableSetOf()) { it.id }
    fun heldTips() = captures.keys.toSet()
    fun tracked(now: Long) = if(active && fresh(now)) tips else emptyList()
    fun reset(): List<Hit> {
        val heldFaces=captures.toMap()
        val hits = captures.keys.toList().mapNotNull { release(it) }
        heldFaces.forEach { (id,face) -> block(id,face.takeUnless { it.slider }) }
        previous.filterValues { it.z<=RELEASE_Z }.keys.forEach { id ->
            if(id !in blocked) block(id)
        }
        blocked.removeAll { id ->
            val ready=previous[id]?.z?.let { it>RELEASE_Z }==true
            if(ready) blockedFaces.remove(id)
            ready
        }
        // Preserve sample freshness for a continuous move after a page change.
        // expire() removes this permission on tracking loss, stale input or pause.
        tips = emptyList()
        return hits
    }
    companion object {
        val INDEX_IDS = setOf(1,6)
        const val CONTACT_Z = .012f
        const val RELEASE_Z = .020f
        const val CROSS_CONTROL_REARM_Z = .016f
        const val BACK_LIMIT = -.032f
    }
}
