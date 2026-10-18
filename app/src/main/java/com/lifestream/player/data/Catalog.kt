package com.lifestream.player.data

import androidx.compose.ui.graphics.Color
import com.lifestream.player.R

/** The built-in library of games and collections the app is organised around. */
object Catalog {

    private fun c(hex: Long) = Color(hex)

    private fun num(vararg n: String, exclude: List<String> = emptyList(), notWith: Set<String> = emptySet()) =
        MatchSpec(numerals = n.toSet(), exclude = exclude, excludeNumerals = notWith)

    private const val X2_CREDIT = "Noriko Matsueda & Takahito Eguchi"

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
                exclude = listOf("PIANO", "DISTANT WORLDS", "WORLD OF", "TACTICS", "DISSIDIA", "ORCHESTRA",
                    "RECORD KEEPER", "BRAVE EXVIUS", "CRYSTAL CHRONICLES", "THEATRHYTHM", "ORIGINS"),
            ), art = R.drawable.art_ff1),
        Game("ff2", "II", "Final Fantasy II", 1988, "Nobuo Uematsu",
            "Final Fantasy II original soundtrack", c(0xFF7F1D1D), c(0xFFF87171), match = num("II"), art = R.drawable.art_ff2),
        Game("ff3", "III", "Final Fantasy III", 1990, "Nobuo Uematsu",
            "Final Fantasy III original soundtrack", c(0xFF14532D), c(0xFF4ADE80), match = num("III"), art = R.drawable.art_ff3),
        Game("ff4", "IV", "Final Fantasy IV", 1991, "Nobuo Uematsu",
            "Final Fantasy IV original soundtrack", c(0xFF312E81), c(0xFFA5B4FC), match = num("IV"), art = R.drawable.art_ff4),
        Game("ff5", "V", "Final Fantasy V", 1992, "Nobuo Uematsu",
            "Final Fantasy V original soundtrack", c(0xFF134E4A), c(0xFF5EEAD4), match = num("V"), art = R.drawable.art_ff5,
            // YouTube only has small (480x360) frames for this soundtrack's songs, which look
            // blurry when shown large; the album's own cover is sharp.
            coverArt = "https://yt3.googleusercontent.com/Oz0XyLPOF78ND3kUtozAGDOUTlkDQT-hcuuu2hXYoeRubap3fTDAylrVoxLzc7EBtH4ltassBQ6xJQY-=w544-h544-l90-rj"),
        Game("ff6", "VI", "Final Fantasy VI", 1994, "Nobuo Uematsu",
            "Final Fantasy VI original soundtrack", c(0xFF4A044E), c(0xFFE879F9), match = num("VI"), art = R.drawable.art_ff6,
            // YouTube only has small (480x360) frames for this soundtrack's songs, which look
            // blurry when shown large; the album's own cover is sharp.
            coverArt = "https://yt3.googleusercontent.com/yrm6nocGlOsaKN6w2xdmncbfuFHVdlUP4DvJfg0Tpivky3XhYVLYsg8fB__eqjaS_z9J_AN7WMi8JMsYhw=w544-h544-l90-rj"),
        Game("ccr", "CC·R", "Crisis Core: Reunion", 2022, "Takeharu Ishimoto",
            "Crisis Core Final Fantasy VII Reunion original soundtrack", c(0xFF172554), c(0xFF93C5FD),
            match = MatchSpec(allOf = listOf("CRISIS CORE", "REUNION")), art = R.drawable.art_ccr,
            aliases = listOf("CCR", "CCFF7", "CCFFVII", "CCFF7R"),
            // Reunion's soundtrack isn't on YouTube Music; its songs show Square Enix's official
            // Crisis Core soundtrack cover (the game's logo) instead of the uploader's thumbnails.
            coverArt = "https://yt3.googleusercontent.com/NUroKdpFTlpUapk0E2XKyYWogJie3XlaXlalTiEBLmhLdZUEWu1ZWz2bebbfIeX1_AKjq3BR3rcasEoW=w544-h544-s-l90-rj"),
        Game("ff7", "VII", "Final Fantasy VII", 1997, "Nobuo Uematsu",
            "Final Fantasy VII original soundtrack", c(0xFF052E16), c(0xFF6EF2B5),
            match = num("VII", exclude = notOriginalVII), art = R.drawable.art_ff7),
        Game("fft", "T", "Final Fantasy: Tactics", 1997, "Hitoshi Sakimoto · Masaharu Iwata",
            "Final Fantasy Tactics original soundtrack", c(0xFF3F2A14), c(0xFFE7C78A),
            match = MatchSpec(allOf = listOf("TACTICS"), exclude = listOf("ADVANCE", "A2")), art = R.drawable.art_fft,
            aliases = listOf("FFT"),
        ),
        // Includes Intergrade's INTERmission episode (DLC): its soundtrack is part of Remake's.
        Game("ff7r", "VII·R", "Final Fantasy VII: Remake", 2020, "Hamauzu · Suzuki · Uematsu",
            "Final Fantasy VII Remake original soundtrack", c(0xFF022C22), c(0xFF34D399),
            match = MatchSpec(
                numerals = setOf("VII"),
                allOf = listOf("REMAKE"),
                exclude = listOf("REBIRTH"),
            ), art = R.drawable.art_ff7r,
            aliases = listOf("FF7R", "FFVIIR"),
            albumQueries = listOf("Final Fantasy VII Remake Intergrade original soundtrack")),
        Game("ff7rb", "VII·RB", "Final Fantasy VII: Rebirth", 2024, "Mitsuto Suzuki · various",
            "Final Fantasy VII Rebirth original soundtrack", c(0xFF0F172A), c(0xFF67E8F9),
            match = MatchSpec(numerals = setOf("VII"), allOf = listOf("REBIRTH")), art = R.drawable.art_ff7rb,
            aliases = listOf("FF7RB", "FFVIIRB"),
        ),
        Game("ff8", "VIII", "Final Fantasy VIII", 1999, "Nobuo Uematsu",
            "Final Fantasy VIII original soundtrack", c(0xFF1F2937), c(0xFF93C5FD), match = num("VIII"), art = R.drawable.art_ff8,
            // Licensed vocal theme, left off the streaming album.
            extras = listOf(ExtraSong("Eyes on Me", "Nobuo Uematsu", "vuB5NSuF2U8", 342))),
        Game("ff9", "IX", "Final Fantasy IX", 2000, "Nobuo Uematsu",
            "Final Fantasy IX original soundtrack", c(0xFF422006), c(0xFFFCD34D), match = num("IX"), art = R.drawable.art_ff9),
        Game("ff10", "X", "Final Fantasy X", 2001, "Uematsu · Hamauzu · Nakano",
            "Final Fantasy X original soundtrack", c(0xFF0C4A6E), c(0xFF7DD3FC), match = num("X", notWith = setOf("X2")), art = R.drawable.art_ff10),
        Game("ff10-2", "X-2", "Final Fantasy X-2", 2003, "Noriko Matsueda · Takahito Eguchi",
            "Final Fantasy X-2 original soundtrack", c(0xFF500724), c(0xFFF9A8D4), match = num("X2", notWith = setOf("X")), art = R.drawable.art_ff10_2,
            extras = listOf(
                ExtraSong("real Emotion", "Kumi Koda", "ec6gB4QH5jU", 240),
                // The English version sung by Jade (Sweetbox) for the Western release. Square Enix
                // has no upload of it, so this one song comes from a fan upload of the full track.
                ExtraSong("1000 Words (English Version)", "Jade Valerie", "r4voJjkJevs", 361, albumArt = true),
                // The songs added by FINAL FANTASY X-2 International + Last Mission Original
                // Soundtrack (its new mixes of 1000 Words and real Emotion are the same songs, so
                // they're left out). Not on YouTube Music as an album; these are uploads of it.
                ExtraSong("Last Mission No. 1", X2_CREDIT, "wQbLGGQ53yg", 326, albumArt = true),
                ExtraSong("Last Mission No. 2", X2_CREDIT, "uBxaDw0MNhM", 348, albumArt = true),
                ExtraSong("Last Mission No. 3", X2_CREDIT, "U82kO_Sqbsg", 272, albumArt = true),
                ExtraSong("Flash Over", X2_CREDIT, "FCcOqCAsYEQ", 325, albumArt = true),
                ExtraSong("To You", X2_CREDIT, "cKcMUoSp0SQ", 299, albumArt = true),
                ExtraSong("Wind Crest ~The Three Trails~", X2_CREDIT, "RRyMcJcV-nQ", 148, albumArt = true),
                ExtraSong("Creature Create", X2_CREDIT, "s2xywlpPaBc", 226, albumArt = true),
            )),
        Game("ff11", "XI", "Final Fantasy XI", 2002, "Uematsu · Mizuta · Tanioka",
            "Final Fantasy XI original soundtrack", c(0xFF1C1917), c(0xFFD6D3D1), match = num("XI"), art = R.drawable.art_ff11,
            // The original game, each expansion (Rise of the Zilart, Chains of Promathia, Treasures
            // of Aht Urhgan, Wings of the Goddess, Seekers of Adoulin), the PLUS albums, and the two
            // "Gifts from Vana'diel" albums with the later Rhapsodies of Vana'diel and Voracious
            // Resurgence music.
            albumQueries = listOf(
                "Final Fantasy XI Rise of the Zilart original soundtrack",
                "Final Fantasy XI Chains of Promathia original soundtrack",
                "Final Fantasy XI Treasures of Aht Urhgan original soundtrack",
                "Final Fantasy XI Wings of the Goddess original soundtrack",
                "Final Fantasy XI Seekers of Adoulin original soundtrack",
                "Final Fantasy XI Gifts from Vana'diel Songs of Rebirth soundtrack",
                "Final Fantasy XI Gifts from Vana'diel Prime Memories soundtrack",
            ),
            maxAlbums = 12,
            // Songs like "Conflict: March of the Hero" and "Conflict: You Want to Live Forever?" are
            // different songs, so the whole name identifies each.
            fullSongTitles = true,
            // From later add-on soundtracks (also on the PLUS album; kept in case it isn't found).
            extras = listOf(
                ExtraSong("Melodies Errant", "Naoshi Mizuta", "n4-yiZpK81g", 205),
                ExtraSong("Shinryu", "Naoshi Mizuta", "x_XQmblQl3Q", 352),
            )),
        Game("ff12", "XII", "Final Fantasy XII", 2006, "Hitoshi Sakimoto",
            "Final Fantasy XII original soundtrack", c(0xFF451A03), c(0xFFFDBA74), match = num("XII"), art = R.drawable.art_ff12,
            // The licensed theme song, on the soundtrack CD but left off the streaming album.
            extras = listOf(ExtraSong("Kiss Me Good-Bye", "Angela Aki", albumArt = true))),
        Game("ff13", "XIII", "Final Fantasy XIII", 2009, "Masashi Hamauzu",
            "Final Fantasy XIII original soundtrack", c(0xFF082F49), c(0xFFBAE6FD), match = num("XIII", exclude = listOf("LIGHTNING RETURNS"), notWith = setOf("XIII2")), art = R.drawable.art_ff13,
            // The Western release's theme song isn't on the Japanese soundtrack album.
            extras = listOf(ExtraSong("My Hands", "Leona Lewis", albumArt = true))),
        Game("ff13-2", "XIII-2", "Final Fantasy XIII-2", 2011, "Hamauzu · Mizuta · Suzuki",
            "Final Fantasy XIII-2 original soundtrack", c(0xFF1E0B3A), c(0xFFB9A2FF),
            match = num("XIII2", notWith = setOf("XIII")), art = R.drawable.art_ff13_2),
        // The third XIII game; its Original Soundtrack PLUS (extra tracks) is included.
        Game("lr", "LR", "Final Fantasy XIII: Lightning Returns", 2013, "Hamauzu · Mizuta · Suzuki",
            "Lightning Returns Final Fantasy XIII original soundtrack", c(0xFF2A0E2E), c(0xFFF5A3D0),
            match = MatchSpec(numerals = setOf("XIII"), allOf = listOf("LIGHTNING RETURNS")), art = R.drawable.art_lr,
            aliases = listOf("LR", "LRFF13", "LRFFXIII"),
            albumQueries = listOf("Lightning Returns Final Fantasy XIII original soundtrack plus")),
        Game("ff14", "XIV", "Final Fantasy XIV", 2013, "Masayoshi Soken",
            "Final Fantasy XIV original soundtrack", c(0xFF2E1065), c(0xFFC4B5FD), match = num("XIV"), art = R.drawable.art_ff14,
            // Each expansion and each run of patches has its own official album.
            // (A Realm Reborn and Before Meteor for 2.x, Heavensward and The Far Edge of Fate for 3.x,
            // Stormblood, Shadowbringers and Death Unto Dawn for 5.x, Endwalker and Growing Light for
            // 6.x, Dawntrail; each named so none depends on turning up in a general search.)
            albumQueries = listOf(
                "A Realm Reborn Final Fantasy XIV original soundtrack",
                "Before Meteor Final Fantasy XIV original soundtrack",
                "Before the Fall Final Fantasy XIV original soundtrack",
                "Heavensward Final Fantasy XIV original soundtrack",
                "The Far Edge of Fate Final Fantasy XIV original soundtrack",
                "Stormblood Final Fantasy XIV original soundtrack",
                "Shadowbringers Final Fantasy XIV original soundtrack",
                "Death Unto Dawn Final Fantasy XIV original soundtrack",
                "Endwalker Final Fantasy XIV original soundtrack",
                "Growing Light Final Fantasy XIV original soundtrack",
                "Dawntrail Final Fantasy XIV original soundtrack",
            ),
            releaseOrder = listOf(
                "A Realm Reborn", "Before Meteor", "Heavensward", "Before the Fall", "Far Edge of Fate",
                "Stormblood", "Shadowbringers", "Death Unto Dawn", "Endwalker", "Growing Light", "Dawntrail",
            ),
            maxAlbums = 16),
        Game("ff15", "XV", "Final Fantasy XV", 2016, "Yoko Shimomura",
            "Final Fantasy XV original soundtrack", c(0xFF0A0A0A), c(0xFFA3A3A3), match = num("XV"), art = R.drawable.art_ff15,
            // Volume 2 (two albums) has the DLC: Episodes Gladiolus, Prompto, Ignis and Ardyn,
            // Comrades and the Royal Edition.
            albumQueries = listOf("Final Fantasy XV original soundtrack volume 2")),
        Game("ff16", "XVI", "Final Fantasy XVI", 2023, "Masayoshi Soken",
            "Final Fantasy XVI original soundtrack", c(0xFF450A0A), c(0xFFFB923C), match = num("XVI"), art = R.drawable.art_ff16),

        // Other Games
        // The game's soundtrack and its Verso's Drafts expansion soundtrack; not the bonus album
        // ("Nos vies en Lumière (Bonus Edition)") or the pre-release trailer EPs ("Reveal Edition",
        // "Gustave Edition", "Cast Reveal Edition").
        Game("coe33", "E33", "Clair Obscur: Expedition 33", 2025, "Lorien Testard",
            "Clair Obscur Expedition 33 original soundtrack", c(0xFF1F1611), c(0xFFE9C46A), Series.SPINOFF,
            match = MatchSpec(
                allOf = listOf("CLAIR OBSCUR"),
                exclude = listOf("EDITION", "BONUS", "NOS VIES", "TRAILER", "REVEAL"),
                requireFinalFantasy = false,
            ), art = R.drawable.art_coe33, fullSongTitles = true,
            aliases = listOf("E33", "COE33", "CO33", "CO E33"),
            albumQueries = listOf("Clair Obscur Expedition 33 Verso's Drafts original soundtrack")),
        Game("lo", "LO", "Lost Odyssey", 2007, "Nobuo Uematsu",
            "Lost Odyssey original soundtrack", c(0xFF292524), c(0xFFE7B76B), Series.SPINOFF,
            match = MatchSpec(allOf = listOf("LOST ODYSSEY"), requireFinalFantasy = false), art = R.drawable.art_lo,
            aliases = listOf("LO"),
        ),
        Game("ct", "CT", "Chrono Trigger", 1995, "Yasunori Mitsuda · Nobuo Uematsu",
            "Chrono Trigger original soundtrack", c(0xFF1E3A5F), c(0xFFFFD166), Series.SPINOFF,
            match = MatchSpec(allOf = listOf("CHRONO", "TRIGGER"), requireFinalFantasy = false), art = R.drawable.art_ct,
            aliases = listOf("CT"),
            // YouTube only has small (480x360) frames for this soundtrack's songs, which look
            // blurry when shown large; the album's own cover is sharp.
            coverArt = "https://yt3.googleusercontent.com/Nt784WTZYI6WHUjn6UGg_IbTIzfHeNBN_Lj17t7YvP9lK1WL5L3ZH_uWna9th7EGAvr5HTTxYNAm1Fs=w544-h544-s-l90-rj",
        ),
        Game("cx", "CC", "Chrono Cross", 1999, "Yasunori Mitsuda",
            "Chrono Cross original soundtrack", c(0xFF083344), c(0xFF22D3EE), Series.SPINOFF,
            match = MatchSpec(allOf = listOf("CHRONO", "CROSS"), requireFinalFantasy = false), art = R.drawable.art_cx,
            aliases = listOf("CC"),
        ),
    )

    /** Soundtracks that were folded into another (saved songs and playlists still use the old id). */
    private val merged = mapOf("ff7int" to "ff7r")

    // Looked up for every song while searching and sorting, so by key rather than a scan.
    private val byId: Map<String, Game> by lazy { games.associateBy { it.id } }

    fun game(id: String): Game? = byId[id] ?: merged[id]?.let { byId[it] }

    /** The current id for a soundtrack id that may have been merged into another. */
    fun currentId(id: String?): String? = id?.let { merged[it] ?: it }

    fun bySeries(series: Series): List<Game> = games.filter { it.series == series }
}
