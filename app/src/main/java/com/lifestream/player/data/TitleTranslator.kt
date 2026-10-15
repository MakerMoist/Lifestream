package com.lifestream.player.data

import com.google.mlkit.nl.translate.Translator
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Turns Japanese track, album and artist names into English, on the device (Google ML Kit's
 * offline Japanese→English model, a one-time ~30 MB download). Text without Japanese characters
 * is returned untouched, and if translation isn't available the original is kept.
 *
 * [toEnglish] blocks, so only call it off the main thread (the repository does).
 */
object TitleTranslator {

    /** Hiragana, katakana (full and half width) and kanji. */
    private val japanese = Regex("[\\u3040-\\u30FF\\u31F0-\\u31FF\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uFF66-\\uFF9F]")

    private val cache = ConcurrentHashMap<String, String>()
    private val whitespace = Regex("\\s+")
    private val spaceBeforePunctuation = Regex("\\s+([~)\\]』」、。,!?])")

    /**
     * Created when a title needs translating and closed again by [release]: with official English
     * names covering most songs it's rarely needed, so its model shouldn't sit in memory.
     */
    private var client: Translator? = null

    private val translator: Translator
        @Synchronized get() = client ?: Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.JAPANESE)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
        ).also { client = it }

    /** Frees the translation model's memory; it's reloaded (from disk) if needed again. */
    @Synchronized
    fun release() {
        client?.close()
        client = null
    }

    @Volatile private var modelReady = false
    @Volatile private var lastFailureAt = 0L

    fun hasJapanese(text: String) = japanese.containsMatchIn(text)

    /** Frees remembered translations (titles are stored translated, so this is rarely needed again). */
    fun trimCaches() = cache.clear()

    /** Starts the model download in the background so titles are translated from the first search. */
    fun prefetch() {
        Thread({ runCatching { ensureModel() } }, "title-translator").start()
    }

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
        "ノクティス" to "Noctis", "ルシス" to "Lucis", "インソムニア" to "Insomnia", "ハンマーヘッド" to "Hammerhead",
        "クライヴ" to "Clive", "ヴァリスゼア" to "Valisthea", "ドミナント" to "Dominant",
        "エオルゼア" to "Eorzea", "グリダニア" to "Gridania", "リムサ・ロミンサ" to "Limsa Lominsa",
        "ウルダハ" to "Ul'dah", "ヴァナ・ディール" to "Vana'diel", "サンドリア" to "San d'Oria", "ジュノ" to "Jeuno",
        // Tactics / Chrono / Lost Odyssey
        "ラムザ" to "Ramza", "クロノ・トリガー" to "Chrono Trigger", "クロノ･トリガー" to "Chrono Trigger",
        "クロノ・クロス" to "Chrono Cross", "クロノ" to "Crono", "ルッカ" to "Lucca", "カイム" to "Kaim",
    ).sortedByDescending { it.first.length }

    private fun applyGlossary(text: String): String {
        var t = text
        for ((jp, en) in glossary) {
            if (t.contains(jp)) t = t.replace(jp, " $en ")
        }
        return t.replace(whitespace, " ").replace(spaceBeforePunctuation, "$1").trim()
    }

    fun toEnglish(text: String): String {
        if (text.isBlank() || !hasJapanese(text)) return text
        // Titles are short; anything huge isn't worth (or safe) sending through translation.
        if (text.length > 300) return text
        cache[text]?.let { return it }
        if (cache.size > 20_000) cache.clear()
        val prepared = applyGlossary(text)
        if (!hasJapanese(prepared)) return tidy(prepared).also { cache[text] = it }
        if (!ensureModel()) return prepared
        val translated = try {
            Tasks.await(translator.translate(prepared), 5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            return prepared
        }
        val result = tidy(translated).ifBlank { text }
        cache[text] = result
        return result
    }

    @Synchronized
    private fun ensureModel(): Boolean {
        if (modelReady) return true
        // After a failure (offline, no Play services…) don't retry on every title.
        if (System.currentTimeMillis() - lastFailureAt < 5 * 60_000) return false
        return try {
            Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().build()), 120, TimeUnit.SECONDS)
            modelReady = true
            true
        } catch (e: Exception) {
            lastFailureAt = System.currentTimeMillis()
            false
        }
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
