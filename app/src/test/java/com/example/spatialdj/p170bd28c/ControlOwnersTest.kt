package com.example.spatialdj.p170bd28c

import com.example.spatialdj.p170bd28c.ui.loop.ControlOwners
import org.junit.Assert.*
import org.junit.Test

class ControlOwnersTest {
    @Test fun lateDisposalCannotRemoveReplacementOrReassignCallbacks() {
        val c=ControlOwners(); val old=Any(); val new=Any()
        c.claim("tools",old); c.claim("tools",new)
        assertFalse(c.release("tools",old))
        assertFalse(c.owns("tools",old))
        assertTrue(c.owns("tools",new))
        assertTrue(c.release("tools",new))
        assertFalse(c.owns("tools",new))
    }
    @Test fun repeatedPageReplacementPreservesIndependentControls() {
        val c=ControlOwners(); val stable=Any()
        c.claim("tools",stable)
        var prior=Any(); c.claim("undo",prior)
        repeat(100) {
            val next=Any(); c.claim("undo",next)
            assertFalse(c.release("undo",prior))
            assertTrue(c.owns("tools",stable))
            assertTrue(c.owns("undo",next))
            prior=next
        }
    }
}
