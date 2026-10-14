package com.lifestream.player.data

import androidx.compose.ui.graphics.Color

/** The built-in library of games and collections the app is organised around. */
object Catalog {

    private fun c(hex: Long) = Color(hex)

    val games: List<Game> = listOf(
        Game("ff1", "I", "Final Fantasy", 1987, "Nobuo Uematsu",
            "Final Fantasy I original soundtrack", c(0xFF1E3A8A), c(0xFF60A5FA)),
        Game("ff2", "II", "Final Fantasy II", 1988, "Nobuo Uematsu",
            "Final Fantasy II original soundtrack", c(0xFF7F1D1D), c(0xFFF87171)),
        Game("ff3", "III", "Final Fantasy III", 1990, "Nobuo Uematsu",
            "Final Fantasy III original soundtrack", c(0xFF14532D), c(0xFF4ADE80)),
        Game("ff4", "IV", "Final Fantasy IV", 1991, "Nobuo Uematsu",
            "Final Fantasy IV original soundtrack", c(0xFF312E81), c(0xFFA5B4FC)),
        Game("ff5", "V", "Final Fantasy V", 1992, "Nobuo Uematsu",
            "Final Fantasy V original soundtrack", c(0xFF134E4A), c(0xFF5EEAD4)),
        Game("ff6", "VI", "Final Fantasy VI", 1994, "Nobuo Uematsu",
            "Final Fantasy VI original soundtrack", c(0xFF4A044E), c(0xFFE879F9)),
        Game("ff7", "VII", "Final Fantasy VII", 1997, "Nobuo Uematsu",
            "Final Fantasy VII original soundtrack", c(0xFF052E16), c(0xFF6EF2B5)),
        Game("ff8", "VIII", "Final Fantasy VIII", 1999, "Nobuo Uematsu",
            "Final Fantasy VIII original soundtrack", c(0xFF1F2937), c(0xFF93C5FD)),
        Game("ff9", "IX", "Final Fantasy IX", 2000, "Nobuo Uematsu",
            "Final Fantasy IX original soundtrack", c(0xFF422006), c(0xFFFCD34D)),
        Game("ff10", "X", "Final Fantasy X", 2001, "Uematsu · Hamauzu · Nakano",
            "Final Fantasy X original soundtrack", c(0xFF0C4A6E), c(0xFF7DD3FC)),
        Game("ff10-2", "X-2", "Final Fantasy X-2", 2003, "Noriko Matsueda · Takahito Eguchi",
            "Final Fantasy X-2 original soundtrack", c(0xFF500724), c(0xFFF9A8D4)),
        Game("ff11", "XI", "Final Fantasy XI", 2002, "Uematsu · Mizuta · Tanioka",
            "Final Fantasy XI original soundtrack", c(0xFF1C1917), c(0xFFD6D3D1)),
        Game("ff12", "XII", "Final Fantasy XII", 2006, "Hitoshi Sakimoto",
            "Final Fantasy XII original soundtrack", c(0xFF451A03), c(0xFFFDBA74)),
        Game("ff13", "XIII", "Final Fantasy XIII", 2009, "Masashi Hamauzu",
            "Final Fantasy XIII original soundtrack", c(0xFF082F49), c(0xFFBAE6FD)),
        Game("ff14", "XIV", "Final Fantasy XIV", 2013, "Masayoshi Soken",
            "Final Fantasy XIV soundtrack", c(0xFF2E1065), c(0xFFC4B5FD)),
        Game("ff15", "XV", "Final Fantasy XV", 2016, "Yoko Shimomura",
            "Final Fantasy XV original soundtrack", c(0xFF0A0A0A), c(0xFFA3A3A3)),
        Game("ff16", "XVI", "Final Fantasy XVI", 2023, "Masayoshi Soken",
            "Final Fantasy XVI original soundtrack", c(0xFF450A0A), c(0xFFFB923C)),
        // VII Remake series
        Game("ff7r", "VII·R", "Final Fantasy VII Remake", 2020, "Hamauzu · Suzuki · Uematsu",
            "Final Fantasy VII Remake original soundtrack", c(0xFF022C22), c(0xFF34D399), Series.REMAKE),
        Game("ff7int", "INT", "VII Remake Intergrade: INTERmission", 2021, "Mitsuto Suzuki · various",
            "Final Fantasy VII Remake Intergrade INTERmission soundtrack", c(0xFF3B0764), c(0xFFD8B4FE), Series.REMAKE),
        Game("ff7rb", "VII·RB", "Final Fantasy VII Rebirth", 2024, "Mitsuto Suzuki · various",
            "Final Fantasy VII Rebirth original soundtrack", c(0xFF0F172A), c(0xFF67E8F9), Series.REMAKE),

        // Spin-offs & collections
        Game("fft", "T", "Final Fantasy Tactics", 1997, "Hitoshi Sakimoto · Masaharu Iwata",
            "Final Fantasy Tactics original soundtrack", c(0xFF3F2A14), c(0xFFE7C78A), Series.SPINOFF),
        Game("cc", "CC", "Crisis Core", 2007, "Takeharu Ishimoto",
            "Crisis Core Final Fantasy VII soundtrack", c(0xFF1E1B4B), c(0xFF818CF8), Series.SPINOFF),
        Game("ccr", "CC·R", "Crisis Core –Reunion–", 2022, "Takeharu Ishimoto",
            "Crisis Core Final Fantasy VII Reunion original soundtrack", c(0xFF172554), c(0xFF93C5FD), Series.SPINOFF),
        Game("distant", "♪", "Distant Worlds (Orchestral)", 2007, "Arnie Roth · Nobuo Uematsu",
            "Distant Worlds music from Final Fantasy", c(0xFF172554), c(0xFFE8C46A), Series.SPINOFF),
        Game("piano", "♫", "Piano Collections", 1992, "Various arrangers",
            "Final Fantasy piano collections", c(0xFF18181B), c(0xFFF4F4F5), Series.SPINOFF),
    )

    fun game(id: String): Game? = games.firstOrNull { it.id == id }

    fun bySeries(series: Series): List<Game> = games.filter { it.series == series }
}
