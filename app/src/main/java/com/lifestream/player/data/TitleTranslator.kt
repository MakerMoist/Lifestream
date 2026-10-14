package com.lifestream.player.data

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

    private val translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.JAPANESE)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
        )
    }

    @Volatile private var modelReady = false
    @Volatile private var lastFailureAt = 0L

    fun hasJapanese(text: String) = japanese.containsMatchIn(text)

    /** Starts the model download in the background so titles are translated from the first search. */
    fun prefetch() {
        Thread({ runCatching { ensureModel() } }, "title-translator").start()
    }

    fun toEnglish(text: String): String {
        if (text.isBlank() || !hasJapanese(text)) return text
        cache[text]?.let { return it }
        if (!ensureModel()) return text
        val translated = try {
            Tasks.await(translator.translate(text), 5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            return text
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
        val words = raw.trim().split(Regex("\\s+"))
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
