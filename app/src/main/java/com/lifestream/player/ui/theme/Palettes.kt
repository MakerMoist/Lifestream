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

    /**
     * Every theme is dark, with text, buttons and highlights checked for strong contrast
     * (body text at least 12:1 on the background, accents at least 7:1) and three clearly
     * different highlight colours, so nothing blends together.
     */
    val all: List<Palette> = listOf(
        // glowing blue-green Lifestream, materia gold
        Palette("lifestream", "Lifestream", "The original look",
            c(0xFF060A16), c(0xFF0E1530), c(0xFF18224A), c(0xFF7DD6FF), c(0xFF5CF2B0), c(0xFFF5C86A),
            c(0xFFEEF3FF), c(0xFFB9C4E4)),
        // mako green, reactor-fire orange, pale steel-yellow
        Palette("midgar", "Midgar", "Final Fantasy VII",
            c(0xFF050807), c(0xFF0C1612), c(0xFF15241D), c(0xFF4CF59E), c(0xFFFF8F3F), c(0xFFE4E8A0),
            c(0xFFEAF4EE), c(0xFFA9C2B6)),
        // opera magenta, snowfield ice, esper gold
        Palette("narshe", "Narshe", "Final Fantasy VI",
            c(0xFF0D0814), c(0xFF1A1029), c(0xFF281A3E), c(0xFFF7A1EC), c(0xFF8FE3FF), c(0xFFFFD27C),
            c(0xFFF6EEFA), c(0xFFC6B4D2)),
        // SeeD blue, Griever red, Garden ivory
        Palette("balamb", "Balamb", "Final Fantasy VIII",
            c(0xFF05090F), c(0xFF0D1622), c(0xFF172435), c(0xFF8CC8FF), c(0xFFFF6E7F), c(0xFFE9E0C6),
            c(0xFFECF2F8), c(0xFFAEBCCB)),
        // storybook gold, rose, Gaia green
        Palette("alexandria", "Alexandria", "Final Fantasy IX",
            c(0xFF120810), c(0xFF22101E), c(0xFF33192D), c(0xFFFFCB5C), c(0xFFFF8FAE), c(0xFFB7E4A0),
            c(0xFFFBEFF4), c(0xFFD2B6C6)),
        // sunset orange, pyrefly cyan, dream violet
        Palette("zanarkand", "Zanarkand", "Final Fantasy X",
            c(0xFF04101A), c(0xFF0B1B2B), c(0xFF13283F), c(0xFFFFA25C), c(0xFF6FE8FF), c(0xFFE3C6FF),
            c(0xFFEAF4FA), c(0xFFAFC5D6)),
        // sandstone gold, Mist teal, magicite lavender
        Palette("ivalice", "Ivalice", "Final Fantasy XII",
            c(0xFF110C07), c(0xFF1E160D), c(0xFF2D2215), c(0xFFF0C25E), c(0xFF4ED6C8), c(0xFFCDB8FF),
            c(0xFFF7F0E4), c(0xFFCDBFA8)),
        // crystal cyan, Serah pink, Etro violet
        Palette("cocoon", "Cocoon", "Final Fantasy XIII",
            c(0xFF060B13), c(0xFF0D1724), c(0xFF152338), c(0xFF8CF2FF), c(0xFFFF7FB8), c(0xFFD8C4FF),
            c(0xFFEEF6FF), c(0xFFB3C3D8)),
        // aether violet, Meteor red, Crystarium gold
        Palette("eorzea", "Eorzea", "Final Fantasy XIV",
            c(0xFF0B0915), c(0xFF151129), c(0xFF211B3C), c(0xFFB9A3FF), c(0xFFFF6A5C), c(0xFFF0CF7A),
            c(0xFFF2EFFF), c(0xFFBFB8DA)),
        // royal silver, crystal blue, Lucian gold
        Palette("lucis", "Lucis", "Final Fantasy XV",
            c(0xFF050506), c(0xFF101114), c(0xFF1B1D23), c(0xFFE2E7F0), c(0xFF5FAEFF), c(0xFFD9B76A),
            c(0xFFF4F6FA), c(0xFFB8BDC8)),
        // Phoenix fire, Shiva ice, Garuda wind
        Palette("valisthea", "Valisthea", "Final Fantasy XVI",
            c(0xFF0F0806), c(0xFF1D110C), c(0xFF2C1A12), c(0xFFFF8E48), c(0xFF92DBFF), c(0xFFBDF07E),
            c(0xFFFBF1EC), c(0xFFD4BDB0)),
    )

    val default: Palette get() = all.first()

    fun byId(id: String?): Palette = all.firstOrNull { it.id == id } ?: default
}
