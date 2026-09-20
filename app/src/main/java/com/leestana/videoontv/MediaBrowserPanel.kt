package com.leestana.videoontv

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile

/** Full-screen, remote-first browser shown inside Vela after Android grants a storage root. */
class MediaBrowserPanel(
    private val context: Context,
    private val panel: View,
    private val title: TextView,
    private val pathLabel: TextView,
    private val entriesView: LinearLayout,
    private val onMediaSelected: (DocumentFile) -> Unit,
    private val onChooseStorage: () -> Unit,
) {
    private val path = mutableListOf<DocumentFile>()

    val isVisible: Boolean get() = panel.visibility == View.VISIBLE

    init {
        panel.findViewById<TextView>(R.id.browser_close).setOnClickListener { hide() }
        panel.findViewById<TextView>(R.id.browser_choose_storage).setOnClickListener { onChooseStorage() }
    }

    fun show(root: DocumentFile) {
        path.clear()
        path += root
        panel.visibility = View.VISIBLE
        render()
    }

    fun hide() {
        panel.visibility = View.GONE
    }

    /** @return true when back was consumed by folder navigation or closing the browser. */
    fun navigateBack(): Boolean {
        if (!isVisible) return false
        if (path.size > 1) {
            path.removeAt(path.lastIndex)
            render()
        } else {
            hide()
        }
        return true
    }

    private fun render() {
        val directory = path.last()
        val children = runCatching { directory.listFiles().toList() }.getOrElse {
            Toast.makeText(context, R.string.browser_unreadable, Toast.LENGTH_LONG).show()
            return
        }
        val folders = children.filter { it.isDirectory }.sortedBy { it.name?.lowercase() }
        val media = children.filter { it.isFile && MediaFileSupport.isPlayable(it.name, it.type) }
            .sortedBy { it.name?.lowercase() }

        title.text = directory.name ?: context.getString(R.string.browser_storage)
        pathLabel.text = path.joinToString("  /  ") {
            it.name ?: context.getString(R.string.browser_storage)
        }
        entriesView.removeAllViews()

        if (path.size > 1) {
            addEntry(context.getString(R.string.browser_up), R.string.browser_up_description) {
                path.removeAt(path.lastIndex)
                render()
            }
        }
        folders.forEach { folder ->
            addEntry(
                context.getString(R.string.browser_folder, folder.name ?: context.getString(R.string.browser_untitled)),
                R.string.browser_folder_description,
            ) {
                path += folder
                render()
            }
        }
        media.forEach { document ->
            addEntry(
                context.getString(R.string.browser_media, document.name ?: context.getString(R.string.browser_untitled)),
                R.string.browser_media_description,
            ) { onMediaSelected(document) }
        }
        if (entriesView.childCount == 0) {
            entriesView.addView(TextView(context).apply {
                text = context.getString(R.string.browser_empty)
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 18f
                setPadding(22, 28, 22, 28)
            })
        } else {
            entriesView.getChildAt(0).requestFocus()
        }
    }

    private fun addEntry(label: String, descriptionRes: Int, action: () -> Unit) {
        entriesView.addView(TextView(context).apply {
            text = label
            contentDescription = context.getString(descriptionRes, label)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 20f
            background = AppCompatResources.getDrawable(context, R.drawable.browser_entry_background)
            isFocusable = true
            isClickable = true
            minHeight = (64 * resources.displayMetrics.density).toInt()
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = 10 })
    }
}
