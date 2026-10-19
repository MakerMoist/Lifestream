package com.lifestream.player.data

import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Where LifeStream keeps its things on Windows:
 * - [data]: settings, playlists, history and the song index (%APPDATA%\LifeStream)
 * - [cache]: saved songs, artwork and other caches that may be cleared (%LOCALAPPDATA%\LifeStream)
 */
object AppDirs {
    lateinit var data: File
        private set
    lateinit var cache: File
        private set

    fun init(dataDir: File? = null, cacheDir: File? = null) {
        val home = File(System.getProperty("user.home"))
        data = dataDir ?: System.getenv("APPDATA")?.let { File(it, "LifeStream") } ?: File(home, ".lifestream")
        cache = cacheDir ?: System.getenv("LOCALAPPDATA")?.let { File(it, "LifeStream") } ?: File(home, ".lifestream/cache")
        data.mkdirs()
        cache.mkdirs()
    }
}

/**
 * A small key-value store saved as one JSON file, with the same calls the Android app's
 * SharedPreferences used (so the shared code reads the same). Changes are written to disk shortly
 * after (or straight away with [Editor.commit]), always to a temporary file first and then moved
 * into place, so a crash or power cut can't leave a half-written file.
 */
class Prefs private constructor(private val file: File) {
    private val values = HashMap<String, Any>()
    private var pending: ScheduledFuture<*>? = null

    init {
        runCatching {
            // A damaged or runaway file is ignored rather than read into memory.
            if (file.exists() && file.length() <= MAX_BYTES) {
                val o = JSONObject(file.readText())
                for (k in o.keys()) values[k] = o.get(k)
            }
        }
    }

    @Synchronized fun getString(key: String, def: String?): String? = values[key] as? String ?: def
    @Synchronized fun getBoolean(key: String, def: Boolean): Boolean = values[key] as? Boolean ?: def
    @Synchronized fun getInt(key: String, def: Int): Int = (values[key] as? Number)?.toInt() ?: def
    @Synchronized fun getLong(key: String, def: Long): Long = (values[key] as? Number)?.toLong() ?: def
    @Synchronized fun getFloat(key: String, def: Float): Float = (values[key] as? Number)?.toFloat() ?: def
    @Synchronized fun contains(key: String): Boolean = key in values

    fun edit() = Editor()

    inner class Editor {
        private val changes = LinkedHashMap<String, Any?>()
        private var clear = false
        fun putString(key: String, value: String?) = apply { changes[key] = value }
        fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        fun putInt(key: String, value: Int) = apply { changes[key] = value }
        fun putLong(key: String, value: Long) = apply { changes[key] = value }
        fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        fun remove(key: String) = apply { changes[key] = null }
        fun clear() = apply { clear = true }

        private fun merge() = synchronized(this@Prefs) {
            if (clear) values.clear()
            for ((k, v) in changes) if (v == null) values.remove(k) else values[k] = v
        }

        /** Saves in the background, a moment later (several changes are written together). */
        fun apply() {
            merge()
            scheduleWrite()
        }

        /** Saves now. */
        fun commit(): Boolean {
            merge()
            return writeNow()
        }
    }

    private fun scheduleWrite() = synchronized(this) {
        if (pending?.isDone == false) return
        pending = writer.schedule({ writeNow() }, 400, TimeUnit.MILLISECONDS)
    }

    private fun writeNow(): Boolean {
        val text = synchronized(this) { JSONObject(values as Map<*, *>).toString() }
        return writeAtomically(file, text)
    }

    companion object {
        private const val MAX_BYTES = 32L * 1024 * 1024
        private val writer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "prefs-writer").apply { isDaemon = true } }
        private val open = HashMap<String, Prefs>()

        /** The store named [name] (one file in the data folder), opened once. */
        @Synchronized
        fun named(name: String): Prefs = open.getOrPut(name) { Prefs(File(AppDirs.data, "$name.json")) }

        /** Writes everything still waiting to be saved (when the app closes). */
        fun flushAll() {
            val all = synchronized(this) { open.values.toList() }
            all.forEach { it.writeNow() }
        }
    }
}

/** Writes [text] to [file] through a temporary file, so the file is never left half-written. */
fun writeAtomically(file: File, text: String): Boolean = runCatching {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeText(text)
    java.nio.file.Files.move(
        tmp.toPath(), file.toPath(),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
    )
    true
}.getOrElse {
    runCatching {
        val tmp = File(file.parentFile, file.name + ".tmp")
        java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        true
    }.getOrDefault(false)
}

/** The same as [writeAtomically], for bytes. */
fun writeAtomicallyBytes(file: File, bytes: ByteArray): Boolean = runCatching {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeBytes(bytes)
    java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    true
}.getOrDefault(false)
