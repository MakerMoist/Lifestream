package com.lifestream.player.ui.theme

import androidx.compose.ui.graphics.Color

/** A colour theme. Every theme is dark so card art and text stay readable. */
data class Palette(
    val id: String,
    val name: String,
    /** The game it's inspired by, shown under the name in Settings. */
    val inspiredBy: String,
    val night: Color,   // background
    val deep: Color,    // sheets, cards
    val panel: Color,   // raised surfaces
    val primary: Color, // buttons, highlights
    val accent: Color,  // "now playing", progress
    val gold: Color,    // section labels, notes
    val text: Color,
    val mist: Color,    // secondary text
)

object Palettes {
    private fun c(hex: Long) = Color(hex)

    val all: List<Palette> = listOf(
        Palette("lifestream", "Lifestream", "The original look",
            c(0xFF0A0E1C), c(0xFF111733), c(0xFF182046), c(0xFF8AD8FF), c(0xFF6EF2B5), c(0xFFE8C46A),
            c(0xFFE6EAF7), c(0xFFB8C2E0)),
        Palette("midgar", "Midgar", "Final Fantasy VII",
            c(0xFF070B09), c(0xFF0E1612), c(0xFF17231D), c(0xFF5CF2A0), c(0xFF9DFFD0), c(0xFFE0703F),
            c(0xFFE3EDE7), c(0xFFA9BDB2)),
        Palette("narshe", "Narshe", "Final Fantasy VI",
            c(0xFF0F0712), c(0xFF1A0D20), c(0xFF271430), c(0xFFE68AE6), c(0xFF9FE3FF), c(0xFFF0C77A),
            c(0xFFF2E8F4), c(0xFFC2AECA)),
        Palette("balamb", "Balamb", "Final Fantasy VIII",
            c(0xFF0B0F14), c(0xFF141B24), c(0xFF1E2834), c(0xFF9CC8FF), c(0xFFE3ECF5), c(0xFFE8E0C8),
            c(0xFFE6ECF2), c(0xFFA8B4C2)),
        Palette("alexandria", "Alexandria", "Final Fantasy IX",
            c(0xFF140A10), c(0xFF21121B), c(0xFF301A28), c(0xFFF2C46A), c(0xFFFF9FB0), c(0xFFE6A97A),
            c(0xFFF4E9EE), c(0xFFC9AFBD)),
        Palette("zanarkand", "Zanarkand", "Final Fantasy X",
            c(0xFF070E1F), c(0xFF0D1A33), c(0xFF15284A), c(0xFFFFB070), c(0xFF7FE6FF), c(0xFFFFD58A),
            c(0xFFEAF1FA), c(0xFFAFC2DA)),
        Palette("ivalice", "Ivalice", "Final Fantasy XII",
            c(0xFF120D07), c(0xFF1E160C), c(0xFF2C2113), c(0xFFE8B25A), c(0xFF5FD3C6), c(0xFFF2D79A),
            c(0xFFF2EADC), c(0xFFC4B49A)),
        Palette("cocoon", "Cocoon", "Final Fantasy XIII",
            c(0xFF08111A), c(0xFF0F1D2A), c(0xFF182C3D), c(0xFF9FF0FF), c(0xFFFF8FB8), c(0xFFDDEFFF),
            c(0xFFE8F5FA), c(0xFFA9C3D1)),
        Palette("eorzea", "Eorzea", "Final Fantasy XIV",
            c(0xFF0B0716), c(0xFF150E26), c(0xFF211838), c(0xFFB9A2FF), c(0xFFFF6A5A), c(0xFFE2C47A),
            c(0xFFECE6F7), c(0xFFB8ADCE)),
        Palette("lucis", "Lucis", "Final Fantasy XV",
            c(0xFF08090B), c(0xFF111317), c(0xFF1B1E24), c(0xFFD6DCE6), c(0xFF7FC8FF), c(0xFFCBB27A),
            c(0xFFECEEF2), c(0xFFA7ADB8)),
        Palette("valisthea", "Valisthea", "Final Fantasy XVI",
            c(0xFF0E0807), c(0xFF1A0F0C), c(0xFF281713), c(0xFFFF8A4C), c(0xFF8FD3FF), c(0xFFE8C27A),
            c(0xFFF3E9E4), c(0xFFC3ADA4)),
    )

    val default: Palette get() = all.first()

    fun byId(id: String?): Palette = all.firstOrNull { it.id == id } ?: default
}
