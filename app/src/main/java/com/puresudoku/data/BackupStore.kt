package com.puresudoku.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Keeps a copy of all app data in a folder the player picks (Documents, Downloads, an SD card…).
 * Files there belong to the player, not the app, so they survive uninstalling. Pick the same
 * folder after reinstalling and the backup is found and restored.
 */
class BackupStore(private val context: Context) {
    private val cfg = context.getSharedPreferences("pure_backup", Context.MODE_PRIVATE)

    var folder: Uri?
        get() = cfg.getString("folder", null)?.let(Uri::parse)
        set(v) { cfg.edit().putString("folder", v?.toString()).apply() }

    var lastBackupAt: Long
        get() = cfg.getLong("last", 0)
        private set(v) { cfg.edit().putLong("last", v).apply() }

    /** Name of the chosen folder, for display. */
    fun folderName(): String? = folder?.let { tree ->
        runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: "your backup folder"
    }

    private fun findFile(tree: Uri): Uri? {
        val parent = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        context.contentResolver.query(
            children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { c ->
            while (c.moveToNext()) if (c.getString(1) == FILE_NAME) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
        }
        return null
    }

    /** Writes [json] to the backup folder. Returns false if there's no folder or it can't be written. */
    fun write(json: String): Boolean {
        val tree = folder ?: return false
        return runCatching {
            val file = findFile(tree) ?: DocumentsContract.createDocument(
                context.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
                "application/json", FILE_NAME,
            ) ?: return false
            context.contentResolver.openOutputStream(file, "wt")?.use { it.write(json.toByteArray()) } ?: return false
            lastBackupAt = System.currentTimeMillis()
            true
        }.getOrDefault(false)
    }

    /** The backup in [tree] (or the chosen folder), if there is one. */
    fun readFromFolder(tree: Uri? = folder): String? = tree?.let { t ->
        runCatching { findFile(t)?.let { read(it) } }.getOrNull()
    }

    fun read(file: Uri): String? = runCatching {
        context.contentResolver.openInputStream(file)?.use { it.readBytes().decodeToString() }
    }.getOrNull()

    companion object {
        const val FILE_NAME = "pure-puzzles-backup.json"
    }
}
