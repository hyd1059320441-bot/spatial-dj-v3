package com.example.spatialdj.p170bd28c.ui.loop

/** One sRGB palette for Compose controls, room finishes and clothing. */
object ClubPalette {
    const val Ink = 0xFF24313DL
    const val Surface = 0xFFF2F4F0L
    const val Muted = 0xFF667580L
    const val Teal = 0xFF76D3C8L
    const val Blue = 0xFF8EADDBL
    const val Amber = 0xFFF0C36BL
    const val Orange = 0xFFECA87FL
    const val Steel = 0xFFA8BACBL
    const val Recess = 0xFFDDE4E3L
    const val Track = 0xFFBAC6C8L
    const val Error = 0xFFA8432FL
    const val Wall = 0xFF788793L
    const val Floor = 0xFF596B78L
    const val Ceiling = 0xFF627581L
    const val Upholstery = 0xFF55636FL
    const val Trim = 0xFF72838FL
    val lanes = listOf(Orange, Amber, Teal, Blue, Steel)

    // Spatial's hex input is RGBA; Compose's Long color input is ARGB.
    fun rgba(color: Long): String = "0x%06xff".format(color and 0xFFFFFF)
}
