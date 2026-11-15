// Platform layer (Android): storage, backups and the app's view model holder.
package com.puresudoku.platform

import android.content.Context
import android.content.SharedPreferences
import com.puresudoku.data.BackupHost
import com.puresudoku.data.BackupStore
import com.puresudoku.data.KeyValueStore

/** The phone's SharedPreferences, as the shared [KeyValueStore]. */
class AndroidStore(private val sp: SharedPreferences) : KeyValueStore {
    constructor(context: Context, name: String) : this(context.getSharedPreferences(name, Context.MODE_PRIVATE))

    override fun getString(key: String, fallback: String?) = sp.getString(key, fallback)
    override fun getInt(key: String, fallback: Int) = sp.getInt(key, fallback)
    override fun getLong(key: String, fallback: Long) = sp.getLong(key, fallback)
    override fun getBoolean(key: String, fallback: Boolean) = sp.getBoolean(key, fallback)
    override val all: Map<String, *> get() = sp.all
    override fun edit(): KeyValueStore.Editor = AndroidEditor(sp.edit())

    private class AndroidEditor(private val e: SharedPreferences.Editor) : KeyValueStore.Editor {
        override fun putString(key: String, value: String?) = apply { e.putString(key, value) }
        override fun putInt(key: String, value: Int) = apply { e.putInt(key, value) }
        override fun putLong(key: String, value: Long) = apply { e.putLong(key, value) }
        override fun putBoolean(key: String, value: Boolean) = apply { e.putBoolean(key, value) }
        override fun putFloat(key: String, value: Float) = apply { e.putFloat(key, value) }
        override fun remove(key: String) = apply { e.remove(key) }
        override fun clear() = apply { e.clear() }
        override fun apply() = e.apply()
        override fun commit() = e.commit()
    }
}

/** Backups to a folder the player picks with the system picker; ids are content URIs. */
class AndroidBackupHost(private val store: BackupStore) : BackupHost {
    override val usesFolder = true
    override val hasFolder get() = store.folder != null
    override fun folderName() = store.folderName()
    override val lastBackupAt get() = store.lastBackupAt
    override fun write(json: String) = store.write(json)
    override fun useFolder(id: String): String? {
        val uri = android.net.Uri.parse(id)
        store.folder = uri
        return store.readFromFolder(uri)
    }
    override fun readFile(id: String) = store.read(android.net.Uri.parse(id))
}

/** Keeps the shared [com.puresudoku.ui.AppViewModel] alive across rotation, as an Android ViewModel. */
class AndroidAppViewModel(app: android.app.Application) : androidx.lifecycle.AndroidViewModel(app) {
    val vm = com.puresudoku.ui.AppViewModel(
        AndroidStore(app, "pure_sudoku"),
        AndroidBackupHost(BackupStore(app)),
    )
    override fun onCleared() { vm.close(); super.onCleared() }
}
