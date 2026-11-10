package com.example.spatialdj.p170bd28c.ui.loop

/** Disposing a prior Compose instance cannot unregister its same-ID replacement. */
internal class ControlOwners {
    private val owners=mutableMapOf<String,Any>()
    fun claim(id: String,owner: Any) { owners[id]=owner }
    fun owns(id: String,owner: Any) = owners[id]===owner
    fun release(id: String,owner: Any): Boolean {
        if(!owns(id,owner)) return false
        owners.remove(id)
        return true
    }
}
