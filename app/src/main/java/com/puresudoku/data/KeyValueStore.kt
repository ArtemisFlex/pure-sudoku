package com.puresudoku.data

/**
 * Where the app keeps saves, records and settings: Android's SharedPreferences on the phone,
 * browser storage on the web. The calls mirror SharedPreferences so the repository reads the same.
 */
interface KeyValueStore {
    fun getString(key: String, fallback: String?): String?
    fun getInt(key: String, fallback: Int): Int
    fun getLong(key: String, fallback: Long): Long
    fun getBoolean(key: String, fallback: Boolean): Boolean
    /** Every stored value: String, Int, Long, Boolean or Float. */
    val all: Map<String, *>
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putFloat(key: String, value: Float): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }
}

/**
 * A copy of all app data kept outside the app. On the phone that's a folder the player picks,
 * which survives uninstalling; in the browser it's a file the player downloads.
 */
interface BackupHost {
    /** True where backups go to a chosen folder automatically; false where the player saves a file. */
    val usesFolder: Boolean
    val hasFolder: Boolean
    fun folderName(): String?
    val lastBackupAt: Long
    /** Writes the backup to the chosen folder (may be called off the main thread). */
    fun write(json: String): Boolean
    /** Remembers folder [id] and returns any backup already in it. */
    fun useFolder(id: String): String?
    /** Reads the backup file [id] the player picked. */
    fun readFile(id: String): String?
}
