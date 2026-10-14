package com.lifestream.player.data

import androidx.compose.ui.graphics.Color
import com.lifestream.player.R

/** The built-in library of games and collections the app is organised around. */
object Catalog {

    private fun c(hex: Long) = Color(hex)

    private fun num(vararg n: String, exclude: List<String> = emptyList(), notWith: Set<String> = emptySet()) =
        MatchSpec(numerals = n.toSet(), exclude = exclude, excludeNumerals = notWith)

    /** Other entries in the franchise that must not leak into the original VII. */
    private val notOriginalVII = listOf(
        "REMAKE", "REBIRTH", "INTERGRADE", "INTERMISSION", "CRISIS CORE", "ADVENT CHILDREN",
        "DIRGE", "BEFORE CRISIS", "EVER CRISIS", "FIRST SOLDIER",
    )

    val games: List<Game> = listOf(
        // Main series
        Game("ff1", "I", "Final Fantasy", 1987, "Nobuo Uematsu",
            "Final Fantasy I original soundtrack", c(0xFF1E3A8A), c(0xFF60A5FA),
            match = MatchSpec(
                numerals = setOf("I"),
                allowBare = true,
                exclude = listOf("PIANO", "DISTANT WORLDS", "TACTICS", "DISSIDIA", "ORCHESTRA",
                    "RECORD KEEPER", "BRAVE EXVIUS", "CRYSTAL CHRONICLES", "THEATRHYTHM", "ORIGINS"),
            ), art = R.drawable.art_ff1),
        Game("ff2", "II", "Final Fantasy II", 1988, "Nobuo Uematsu",
            "Final Fantasy II original soundtrack", c(0xFF7F1D1D), c(0xFFF87171), match = num("II"), art = R.drawable.art_ff2),
        Game("ff3", "III", "Final Fantasy III", 1990, "Nobuo Uematsu",
            "Final Fantasy III original soundtrack", c(0xFF14532D), c(0xFF4ADE80), match = num("III"), art = R.drawable.art_ff3),
        Game("ff4", "IV", "Final Fantasy IV", 1991, "Nobuo Uematsu",
            "Final Fantasy IV original soundtrack", c(0xFF312E81), c(0xFFA5B4FC), match = num("IV"), art = R.drawable.art_ff4),
        Game("ff5", "V", "Final Fantasy V", 1992, "Nobuo Uematsu",
            "Final Fantasy V original soundtrack", c(0xFF134E4A), c(0xFF5EEAD4), match = num("V"), art = R.drawable.art_ff5),
        Game("ff6", "VI", "Final Fantasy VI", 1994, "Nobuo Uematsu",
            "Final Fantasy VI original soundtrack", c(0xFF4A044E), c(0xFFE879F9), match = num("VI"), art = R.drawable.art_ff6),
        Game("ff7", "VII", "Final Fantasy VII", 1997, "Nobuo Uematsu",
            "Final Fantasy VII original soundtrack", c(0xFF052E16), c(0xFF6EF2B5),
            match = num("VII", exclude = notOriginalVII), art = R.drawable.art_ff7),
        Game("ff7r", "VII·R", "Final Fantasy VII Remake", 2020, "Hamauzu · Suzuki · Uematsu",
            "Final Fantasy VII Remake original soundtrack", c(0xFF022C22), c(0xFF34D399),
            match = MatchSpec(
                numerals = setOf("VII"),
                allOf = listOf("REMAKE"),
                exclude = listOf("REBIRTH", "INTERGRADE", "INTERMISSION"),
            ), art = R.drawable.art_ff7r),
        Game("ff7int", "INT", "VII Remake Intergrade: INTERmission", 2021, "Mitsuto Suzuki · various",
            "Final Fantasy VII Remake Intergrade original soundtrack", c(0xFF3B0764), c(0xFFD8B4FE),
            match = MatchSpec(numerals = setOf("VII"), anyOf = listOf("INTERGRADE", "INTERMISSION")), art = R.drawable.art_ff7int),
        Game("ff7rb", "VII·RB", "Final Fantasy VII Rebirth", 2024, "Mitsuto Suzuki · various",
            "Final Fantasy VII Rebirth original soundtrack", c(0xFF0F172A), c(0xFF67E8F9),
            match = MatchSpec(numerals = setOf("VII"), allOf = listOf("REBIRTH")), art = R.drawable.art_ff7rb),
        Game("ff8", "VIII", "Final Fantasy VIII", 1999, "Nobuo Uematsu",
            "Final Fantasy VIII original soundtrack", c(0xFF1F2937), c(0xFF93C5FD), match = num("VIII"), art = R.drawable.art_ff8),
        Game("ff9", "IX", "Final Fantasy IX", 2000, "Nobuo Uematsu",
            "Final Fantasy IX original soundtrack", c(0xFF422006), c(0xFFFCD34D), match = num("IX"), art = R.drawable.art_ff9),
        Game("ff10", "X", "Final Fantasy X", 2001, "Uematsu · Hamauzu · Nakano",
            "Final Fantasy X original soundtrack", c(0xFF0C4A6E), c(0xFF7DD3FC), match = num("X", notWith = setOf("X2")), art = R.drawable.art_ff10),
        Game("ff10-2", "X-2", "Final Fantasy X-2", 2003, "Noriko Matsueda · Takahito Eguchi",
            "Final Fantasy X-2 original soundtrack", c(0xFF500724), c(0xFFF9A8D4), match = num("X2", notWith = setOf("X")), art = R.drawable.art_ff10_2),
        Game("ff11", "XI", "Final Fantasy XI", 2002, "Uematsu · Mizuta · Tanioka",
            "Final Fantasy XI original soundtrack", c(0xFF1C1917), c(0xFFD6D3D1), match = num("XI"), art = R.drawable.art_ff11),
        Game("ff12", "XII", "Final Fantasy XII", 2006, "Hitoshi Sakimoto",
            "Final Fantasy XII original soundtrack", c(0xFF451A03), c(0xFFFDBA74), match = num("XII"), art = R.drawable.art_ff12),
        Game("ff13", "XIII", "Final Fantasy XIII", 2009, "Masashi Hamauzu",
            "Final Fantasy XIII original soundtrack", c(0xFF082F49), c(0xFFBAE6FD), match = num("XIII", notWith = setOf("XIII2")), art = R.drawable.art_ff13,
            // The Western release's theme song isn't on the Japanese soundtrack album.
            extras = listOf(ExtraSong("My Hands", "Leona Lewis"))),
        Game("ff13-2", "XIII-2", "Final Fantasy XIII-2", 2011, "Hamauzu · Mizuta · Suzuki",
            "Final Fantasy XIII-2 original soundtrack", c(0xFF1E0B3A), c(0xFFB9A2FF),
            match = num("XIII2", notWith = setOf("XIII")), art = R.drawable.art_ff13_2),
        Game("ff14", "XIV", "Final Fantasy XIV", 2013, "Masayoshi Soken",
            "Final Fantasy XIV original soundtrack", c(0xFF2E1065), c(0xFFC4B5FD), match = num("XIV"), art = R.drawable.art_ff14),
        Game("ff15", "XV", "Final Fantasy XV", 2016, "Yoko Shimomura",
            "Final Fantasy XV original soundtrack", c(0xFF0A0A0A), c(0xFFA3A3A3), match = num("XV"), art = R.drawable.art_ff15),
        Game("ff16", "XVI", "Final Fantasy XVI", 2023, "Masayoshi Soken",
            "Final Fantasy XVI original soundtrack", c(0xFF450A0A), c(0xFFFB923C), match = num("XVI"), art = R.drawable.art_ff16),

        // Spin-offs & more
        Game("fft", "T", "Final Fantasy Tactics", 1997, "Hitoshi Sakimoto · Masaharu Iwata",
            "Final Fantasy Tactics original soundtrack", c(0xFF3F2A14), c(0xFFE7C78A), Series.SPINOFF,
            match = MatchSpec(allOf = listOf("TACTICS"), exclude = listOf("ADVANCE", "A2")), art = R.drawable.art_fft),
        Game("ccr", "CC·R", "Crisis Core –Reunion–", 2022, "Takeharu Ishimoto",
            "Crisis Core Final Fantasy VII Reunion original soundtrack", c(0xFF172554), c(0xFF93C5FD), Series.SPINOFF,
            match = MatchSpec(allOf = listOf("CRISIS CORE", "REUNION")), art = R.drawable.art_ccr),
        Game("ct", "CT", "Chrono Trigger", 1995, "Yasunori Mitsuda · Nobuo Uematsu",
            "Chrono Trigger original soundtrack", c(0xFF1E3A5F), c(0xFFFFD166), Series.SPINOFF,
            match = MatchSpec(allOf = listOf("CHRONO", "TRIGGER"), requireFinalFantasy = false), art = R.drawable.art_ct),
        Game("cx", "CC", "Chrono Cross", 1999, "Yasunori Mitsuda",
            "Chrono Cross original soundtrack", c(0xFF083344), c(0xFF22D3EE), Series.SPINOFF,
            match = MatchSpec(allOf = listOf("CHRONO", "CROSS"), requireFinalFantasy = false), art = R.drawable.art_cx),
        Game("lo", "LO", "Lost Odyssey", 2007, "Nobuo Uematsu",
            "Lost Odyssey original soundtrack", c(0xFF292524), c(0xFFE7B76B), Series.SPINOFF,
            match = MatchSpec(allOf = listOf("LOST ODYSSEY"), requireFinalFantasy = false), art = R.drawable.art_lo),
    )

    fun game(id: String): Game? = games.firstOrNull { it.id == id }

    fun bySeries(series: Series): List<Game> = games.filter { it.series == series }
}
