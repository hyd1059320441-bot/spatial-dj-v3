package com.example.spatialdj.p170bd28c.ui.loop

internal object ClubCast {
    data class Look(val female: Boolean,val skin: Long,val hair: Long,val top: Long)
    const val WHITE_SKIN = 0xFFF9F6F0L
    val people = listOf(
        Look(true,WHITE_SKIN,0xFF352D2BL,ClubPalette.Teal),
        Look(false,WHITE_SKIN,0xFF41342CL,ClubPalette.Amber),
        Look(true,WHITE_SKIN,0xFF634237L,ClubPalette.Blue),
        Look(false,WHITE_SKIN,0xFF302D2BL,ClubPalette.Orange),
    )
}
