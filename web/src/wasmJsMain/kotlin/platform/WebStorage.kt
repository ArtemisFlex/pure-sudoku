// Platform layer (browser): saves, records and settings in the browser's local storage.
package com.puresudoku.platform

import com.puresudoku.data.BackupHost
import com.puresudoku.data.KeyValueStore
import kotlinx.browser.window

/**
 * [KeyValueStore] over localStorage. Each value is stored as text with a one-letter type tag
 * ("s:", "i:", "l:", "b:", "f:") so numbers and flags come back as the same types.
 */
class WebStore(private val prefix: String) : KeyValueStore {
    private val ls get() = window.localStorage

    private fun raw(key: String): String? = runCatching { ls.getItem(prefix + key) }.getOrNull()
    private fun decode(v: String): Any? = when (v.take(2)) {
        "s:" -> v.drop(2)
        "i:" -> v.drop(2).toIntOrNull()
        "l:" -> v.drop(2).toLongOrNull()
        "b:" -> v.drop(2) == "true"
        "f:" -> v.drop(2).toFloatOrNull()
        else -> null
    }

    override fun getString(key: String, fallback: String?) = (raw(key)?.let(::decode) as? String) ?: fallback
    override fun getInt(key: String, fallback: Int) = (raw(key)?.let(::decode) as? Int) ?: fallback
    override fun getLong(key: String, fallback: Long) = when (val v = raw(key)?.let(::decode)) { is Long -> v; is Int -> v.toLong(); else -> fallback }
    override fun getBoolean(key: String, fallback: Boolean) = (raw(key)?.let(::decode) as? Boolean) ?: fallback

    override val all: Map<String, *>
        get() = buildMap {
            for (i in 0 until ls.length) {
                val k = ls.key(i) ?: continue
                if (!k.startsWith(prefix)) continue
                raw(k.removePrefix(prefix))?.let(::decode)?.let { put(k.removePrefix(prefix), it) }
            }
        }

    override fun edit(): KeyValueStore.Editor = Editor()

    private inner class Editor : KeyValueStore.Editor {
        private val puts = LinkedHashMap<String, String?>()
        private var clearAll = false
        override fun putString(key: String, value: String?) = apply { puts[key] = value?.let { "s:$it" } }
        override fun putInt(key: String, value: Int) = apply { puts[key] = "i:$value" }
        override fun putLong(key: String, value: Long) = apply { puts[key] = "l:$value" }
        override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = "b:$value" }
        override fun putFloat(key: String, value: Float) = apply { puts[key] = "f:$value" }
        override fun remove(key: String) = apply { puts[key] = null }
        override fun clear() = apply { clearAll = true; puts.clear() }
        override fun apply() { commit() }
        override fun commit(): Boolean = runCatching {
            if (clearAll) all.keys.forEach { ls.removeItem(prefix + it) }
            for ((k, v) in puts) if (v == null) ls.removeItem(prefix + k) else ls.setItem(prefix + k, v)
            true
        }.getOrElse { println("Storage: $it"); false }
    }
}

/** In the browser a backup is a file the player downloads and loads again; there's no folder to write to. */
class WebBackupHost : BackupHost {
    override val usesFolder = false
    override val hasFolder = false
    override fun folderName(): String? = null
    override val lastBackupAt = 0L
    override fun write(json: String) = false
    override fun useFolder(id: String): String? = null
    override fun readFile(id: String): String? = null
}
