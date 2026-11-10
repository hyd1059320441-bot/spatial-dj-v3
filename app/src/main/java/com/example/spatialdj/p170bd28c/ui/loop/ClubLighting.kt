package com.example.spatialdj.p170bd28c.ui.loop

import kotlin.math.*

/** Shared moving heads sweep the dance floor; general lighting keeps all people visible. */
internal object ClubLighting {
    const val FILL_LUX = 800f
    const val SIDE_FILL_LUX = 800f
    const val AMBIENT_EV = 4f
    const val INNER_ANGLE = 14f
    const val OUTER_ANGLE = 22f
    const val RADIUS = 4.6f
    val positions = listOf(ClubMotion.V(-1.15,.08,-2.65),ClubMotion.V(-.53,.08,-3.77),
        ClubMotion.V(.60,.08,-3.66),ClubMotion.V(1.18,.08,-2.82))
    val origins = listOf(ClubMotion.V(-1.60,2.62,-3.4),ClubMotion.V(1.60,2.62,-3.4))
    fun target(spot: Int,beat: Double,energy: Double): ClubMotion.V {
        val amplitude=.30+.70*energy.coerceIn(0.0,1.0)
        val phase=beat*PI/8+spot*PI
        return ClubMotion.V(1.25*amplitude*sin(phase),.08,-3.4+.70*amplitude*cos(phase))
    }
}
