package com.lifestream.player.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Lets English spellings find Japanese song names: kana is turned into its Latin-letter reading
 * ("ミドガルズオルム" → "midogaruzuorumu"), and both sides are compared by their consonant
 * "sound skeleton", so "midgardsormr" matches it. Kanji has no single reading, so only kana is
 * converted (the official English names cover most songs anyway).
 */
object Romaji {

    private val digraphs = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo", "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho", "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo", "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo", "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo", "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo", "てぃ" to "ti", "でぃ" to "di", "とぅ" to "tu",
        "どぅ" to "du", "ふぁ" to "fa", "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo", "うぃ" to "wi",
        "うぇ" to "we", "うぉ" to "wo", "ゔぁ" to "va", "ゔぃ" to "vi", "ゔぇ" to "ve", "ゔぉ" to "vo",
        "しぇ" to "she", "じぇ" to "je", "ちぇ" to "che", "つぁ" to "tsa",
    )

    private val singles = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'を' to "o", 'ん' to "n", 'ゔ' to "vu",
        'ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o", 'ゃ' to "ya", 'ゅ' to "yu", 'ょ' to "yo",
    )

    /** Katakana → hiragana, so one table covers both. */
    private fun toHiragana(c: Char): Char = if (c in 'ァ'..'ヶ') (c - 0x60) else c

    /** The Latin-letter reading of any kana in [text]; other characters are kept as they are. */
    fun of(text: String): String {
        val src = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC).map(::toHiragana)
        val out = StringBuilder()
        var i = 0
        var doubleNext = false
        while (i < src.size) {
            val c = src[i]
            val pair = if (i + 1 < src.size) "$c${src[i + 1]}" else null
            val piece = pair?.let { digraphs[it] }?.also { i++ } ?: singles[c]
            when {
                c == 'っ' -> doubleNext = true
                c == 'ー' -> out.lastOrNull()?.takeIf { it in "aeiou" }?.let(out::append)
                piece != null -> {
                    if (doubleNext && piece[0] !in "aeiou") out.append(piece[0])
                    doubleNext = false
                    out.append(piece)
                }
                else -> {
                    doubleNext = false
                    out.append(c)
                }
            }
            i++
        }
        return out.toString()
    }

    /**
     * Consonant skeleton of Latin text: vowels and h dropped, similar sounds merged (l/r, v/b,
     * c/q/k, z/s, j/g), repeats collapsed. "midgardsormr" → "mdgrdsrmr".
     */
    fun skeleton(latin: String): String {
        val out = StringBuilder()
        // English spellings Japanese renders differently: "Sephiroth" → セフィロス (sefirosu).
        val text = latin.lowercase().replace("ph", "f").replace("th", "s").replace("ck", "k")
        for (ch in text) {
            val c = when (ch) {
                'l' -> 'r'; 'v' -> 'b'; 'c', 'q' -> 'k'; 'z' -> 's'; 'j' -> 'g'; 'x' -> 'k'
                else -> ch
            }
            if (c !in 'a'..'z' || c in "aeiouhy" || c == 'w') continue
            if (out.lastOrNull() != c) out.append(c)
        }
        return out.toString()
    }

    private val skeletons = ConcurrentHashMap<String, String>()

    fun trimCaches() = skeletons.clear()

    /** Skeleton of a Japanese title's kana reading (cached; titles repeat across searches). */
    fun kanaSkeleton(japanese: String): String = skeletons.getOrPut(japanese) {
        if (skeletons.size > 20_000) skeletons.clear()
        skeleton(of(japanese))
    }

    /**
     * True when an English-letter [query] sounds like [japanese]'s kana reading: their skeletons
     * are within a typo or two (a quarter of the length), anywhere in the title.
     */
    fun soundsLike(query: String, japanese: String): Boolean {
        val q = skeleton(query.filter { it.isLetter() && it.code < 128 })
        if (q.length < 5) return false // too short to compare by sound without false matches
        val t = kanaSkeleton(japanese)
        if (t.length < 3) return false
        return Matching.withinEdits(q, t, maxOf(1, q.length / 4))
    }
}
