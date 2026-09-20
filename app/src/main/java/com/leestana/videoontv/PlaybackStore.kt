package com.leestana.videoontv

import android.content.Context
import android.net.Uri

class PlaybackStore(context: Context) {
    private val prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)

    fun load(uri: Uri): Long = prefs.getLong(uri.toString(), 0L)

    fun clear(uri: Uri) {
        prefs.edit().remove(uri.toString()).apply()
    }

    fun save(uri: Uri?, positionMs: Long) {
        if (uri != null && PlaybackPosition.canResume(positionMs)) prefs.edit().putLong(uri.toString(), positionMs).apply()
    }

    fun saveStorageRoot(uri: Uri) {
        prefs.edit().putString(STORAGE_ROOT, uri.toString()).apply()
    }

    fun loadStorageRoot(): Uri? = prefs.getString(STORAGE_ROOT, null)?.let(Uri::parse)

    companion object {
        private const val STORAGE_ROOT = "storage_root"
    }
}
