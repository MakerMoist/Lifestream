package com.lifestream.player.data

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns Japanese song and artist names into English where it can, on Windows. (The Android app
 * uses Google's on-device translator, which isn't available here.) Nearly every song already has
 * an official English name (OfficialTitles); for the rest, well-known Final Fantasy names and
 * terms are swapped in from the glossary below. A name that would still be partly Japanese is
 * left as it is.
 */
object TitleTranslator {

    /** Hiragana, katakana (full and half width) and kanji. */
    private val japanese = Regex("[\\u3040-\\u30FF\\u31F0-\\u31FF\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uFF66-\\uFF9F]")

    private val cache = ConcurrentHashMap<String, String>()
    private val whitespace = Regex("\\s+")
    private val spaceBeforePunctuation = Regex("\\s+([~)\\]』」、。,!?])")

    fun hasJapanese(text: String) = japanese.containsMatchIn(text)

    /** Frees remembered translations. */
    fun trimCaches() = cache.clear()

    /** Nothing to let go of on Windows (kept so shared code reads the same). */
    fun release() = Unit

    /**
     * Official English names for Final Fantasy terms the general-purpose translator tends to
     * transliterate oddly. Applied first (longest match first); whatever Japanese remains is then
     * machine-translated around them.
     */
    private val glossary: List<Pair<String, String>> = listOf(
        // well-known song titles
        "片翼の天使" to "One-Winged Angel",
        "更に闘う者達" to "Those Who Fight Further",
        "闘う者達" to "Those Who Fight",
        "勝利のファンファーレ" to "Victory Fanfare",
        "エアリスのテーマ" to "Aerith's Theme",
        "ティファのテーマ" to "Tifa's Theme",
        "バレットのテーマ" to "Barret's Theme",
        "ザナルカンドにて" to "To Zanarkand",
        "ティーダのテーマ" to "Tidus's Theme",
        "ユウナのテーマ" to "Yuna's Theme",
        "ビッグブリッジの死闘" to "Battle at the Big Bridge",
        "妖星乱舞" to "Dancing Mad",
        "愛のテーマ" to "Theme of Love",
        "メインテーマ" to "Main Theme",
        "メイン・テーマ" to "Main Theme",
        "メイン･テーマ" to "Main Theme",
        "時の傷痕" to "Scars of Time",
        // series vocabulary
        "ファイナルファンタジー" to "Final Fantasy",
        "プレリュード" to "Prelude",
        "オープニング" to "Opening",
        "エンディング" to "Ending",
        "ファンファーレ" to "Fanfare",
        "ラストバトル" to "Final Battle",
        "バトル" to "Battle",
        "ボス" to "Boss",
        "チョコボ" to "Chocobo",
        "モーグリ" to "Moogle",
        "クリスタル" to "Crystal",
        "ライフストリーム" to "Lifestream",
        "召喚獣" to "Summon",
        "飛空艇" to "Airship",
        "魔晄炉" to "Mako Reactor",
        "魔晄" to "Mako",
        // VII
        "神羅カンパニー" to "Shinra Company", "神羅" to "Shinra", "ミッドガル" to "Midgar",
        "セフィロス" to "Sephiroth", "クラウド" to "Cloud", "ティファ" to "Tifa", "エアリス" to "Aerith",
        "バレット" to "Barret", "ユフィ" to "Yuffie", "ザックス" to "Zack", "ソルジャー" to "SOLDIER",
        "バスターソード" to "Buster Sword", "ジェノバ" to "Jenova", "ゴールドソーサー" to "Gold Saucer",
        "ザンガン" to "Zangan", "ウータイ" to "Wutai", "ジュノン" to "Junon", "コスモキャニオン" to "Cosmo Canyon",
        // X / X-2
        "ザナルカンド" to "Zanarkand", "ティーダ" to "Tidus", "ユウナ" to "Yuna", "ブリッツボール" to "Blitzball",
        "スピラ" to "Spira", "ビサイド" to "Besaid", "ルカ" to "Luca", "カモメ団" to "Gullwings",
        // VIII / IX
        "ガーデン" to "Garden", "スコール" to "Squall", "リノア" to "Rinoa", "アルティミシア" to "Ultimecia",
        "バラム" to "Balamb", "アレクサンドリア" to "Alexandria", "ジタン" to "Zidane", "ビビ" to "Vivi",
        "ガーネット" to "Garnet", "クジャ" to "Kuja",
        // I–VI
        "コーネリア" to "Cornelia", "バロン" to "Baron", "セシル" to "Cecil", "カイン" to "Kain",
        "ローザ" to "Rosa", "ゴルベーザ" to "Golbez", "ギルガメッシュ" to "Gilgamesh", "エクスデス" to "Exdeath",
        "ビッグブリッジ" to "Big Bridge", "ナルシェ" to "Narshe", "ケフカ" to "Kefka", "ティナ" to "Terra",
        "セリス" to "Celes", "マッシュ" to "Sabin", "エドガー" to "Edgar", "フィガロ" to "Figaro",
        // XII–XVI
        "イヴァリース" to "Ivalice", "ラバナスタ" to "Rabanastre", "アーシェ" to "Ashe", "ヴァン" to "Vaan",
        "ライトニング" to "Lightning", "コクーン" to "Cocoon", "グラン＝パルス" to "Gran Pulse",
        // Lightning Returns
        "ノウス＝パルトゥス" to "Nova Chrysalia", "ノウス=パルトゥス" to "Nova Chrysalia",
        "ルクセリオ" to "Luxerion", "ユスナーン" to "Yusnaan", "デッド･デューン" to "Dead Dunes",
        "デッド・デューン" to "Dead Dunes", "ウィルダネス" to "Wildlands", "ノエル" to "Noel",
        "エクレール･ファロン" to "Claire Farron", "エクレール・ファロン" to "Claire Farron",
        "ブーニベルゼ" to "Bhunivelze", "ルミナ" to "Lumina", "ホープ" to "Hope",
        "ノクティス" to "Noctis", "ルシス" to "Lucis", "インソムニア" to "Insomnia", "ハンマーヘッド" to "Hammerhead",
        "クライヴ" to "Clive", "ヴァリスゼア" to "Valisthea", "ドミナント" to "Dominant",
        "エオルゼア" to "Eorzea", "グリダニア" to "Gridania", "リムサ・ロミンサ" to "Limsa Lominsa",
        "ウルダハ" to "Ul'dah", "ヴァナ・ディール" to "Vana'diel", "サンドリア" to "San d'Oria", "ジュノ" to "Jeuno",
        // Tactics / Chrono / Lost Odyssey
        "ラムザ" to "Ramza", "クロノ・トリガー" to "Chrono Trigger", "クロノ･トリガー" to "Chrono Trigger",
        "クロノ・クロス" to "Chrono Cross", "クロノ" to "Crono", "ルッカ" to "Lucca", "カイム" to "Kaim",
    ).sortedByDescending { it.first.length }

    private val katakanaOnly = Regex("^[\\u30A0-\\u30FF\\uFF65-\\uFF9F]+$")

    /**
     * Short katakana names only count as whole words: "ビビ" (Vivi) is a name, but it's also the
     * start of "ビビッド" (vivid), and "ヴァン" (Vaan) starts "ヴァンパイア" (vampire). Those must
     * not be split, so a short name next to more katakana is left for the translator.
     */
    private val glossaryRules: List<Pair<Regex?, String>> = glossary.map { (jp, en) ->
        val needsBoundary = jp.length <= 3 && katakanaOnly.matches(jp)
        val rule = if (needsBoundary) {
            // Katakana letters and the long-vowel mark (not the "・" between names).
            val letter = "[\\u30A1-\\u30FA\\u30FC\\uFF66-\\uFF9F]"
            Regex("(?<!$letter)" + Regex.escape(jp) + "(?!$letter)")
        } else {
            null
        }
        rule to en
    }

    internal fun applyGlossary(text: String): String {
        var t = text
        for ((i, entry) in glossaryRules.withIndex()) {
            val jp = glossary[i].first
            if (!t.contains(jp)) continue
            val (rule, en) = entry
            t = if (rule == null) t.replace(jp, " $en ") else t.replace(rule, Regex.escapeReplacement(" $en "))
        }
        return t.replace(whitespace, " ").replace(spaceBeforePunctuation, "$1").trim()
    }

    fun toEnglish(text: String): String {
        if (text.isBlank() || !hasJapanese(text)) return text
        if (text.length > 300) return text
        cache[text]?.let { return it }
        if (cache.size > 20_000) cache.clear()
        val prepared = applyGlossary(text)
        val result = if (hasJapanese(prepared)) text else tidy(prepared)
        cache[text] = result
        return result
    }

    private val smallWords = setOf(
        "a", "an", "and", "as", "at", "but", "by", "for", "from", "in", "into", "of", "on", "or", "the", "to", "with",
    )

    /** Song-title casing: "one-winged angel" → "One-Winged Angel"; keeps existing capitals (VII, ~, etc.). */
    private fun tidy(raw: String): String {
        val words = raw.trim().split(whitespace)
        return words.mapIndexed { i, word ->
            word.split("-").joinToString("-") { part ->
                val lower = part.lowercase(Locale.ROOT)
                when {
                    part.isEmpty() -> part
                    part.any { it.isUpperCase() } && part != part.replaceFirstChar { it.uppercase() } -> part
                    i > 0 && lower in smallWords -> lower
                    else -> part.replaceFirstChar { it.titlecase(Locale.ROOT) }
                }
            }
        }.joinToString(" ")
    }
}
