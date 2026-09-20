package com.leestana.videoontv

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile

class MediaBrowserDialog(
    private val context: Context,
    private val onMediaSelected: (DocumentFile) -> Unit,
    private val onChooseStorage: () -> Unit,
) {
    private val path = mutableListOf<DocumentFile>()

    fun show(root: DocumentFile) {
        path.clear()
        path += root
        showCurrentDirectory()
    }

    private fun showCurrentDirectory() {
        val directory = path.last()
        val children = runCatching { directory.listFiles().toList() }.getOrElse {
            Toast.makeText(context, R.string.browser_unreadable, Toast.LENGTH_LONG).show()
            return
        }
        val folders = children.filter { it.isDirectory }.sortedBy { it.name?.lowercase() }
        val media = children.filter { it.isFile && MediaFileSupport.isPlayable(it.name, it.type) }
            .sortedBy { it.name?.lowercase() }
        val entries = buildList {
            if (path.size > 1) add(BrowserEntry.Up)
            addAll(folders.map(BrowserEntry::Folder))
            addAll(media.map(BrowserEntry::Media))
        }
        val labels = if (entries.isEmpty()) {
            arrayOf(context.getString(R.string.browser_empty))
        } else {
            entries.map { entry ->
                when (entry) {
                    BrowserEntry.Up -> context.getString(R.string.browser_up)
                    is BrowserEntry.Folder -> context.getString(
                        R.string.browser_folder,
                        entry.document.name ?: context.getString(R.string.browser_untitled),
                    )
                    is BrowserEntry.Media -> context.getString(
                        R.string.browser_media,
                        entry.document.name ?: context.getString(R.string.browser_untitled),
                    )
                }
            }.toTypedArray()
        }

        AlertDialog.Builder(context)
            .setTitle(directory.name ?: context.getString(R.string.browser_choose_storage))
            .setItems(labels) { _, index ->
                if (entries.isEmpty()) return@setItems
                when (val entry = entries[index]) {
                    BrowserEntry.Up -> {
                        path.removeAt(path.lastIndex)
                        showCurrentDirectory()
                    }
                    is BrowserEntry.Folder -> {
                        path += entry.document
                        showCurrentDirectory()
                    }
                    is BrowserEntry.Media -> onMediaSelected(entry.document)
                }
            }
            .setNeutralButton(R.string.browser_choose_storage) { _, _ -> onChooseStorage() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private sealed interface BrowserEntry {
        data object Up : BrowserEntry
        data class Folder(val document: DocumentFile) : BrowserEntry
        data class Media(val document: DocumentFile) : BrowserEntry
    }
}
