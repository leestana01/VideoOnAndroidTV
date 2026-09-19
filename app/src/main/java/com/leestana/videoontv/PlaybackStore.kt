package com.leestana.videoontv

import android.content.Context
import android.net.Uri

class PlaybackStore(context: Context) {
    private val prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)

    fun load(uri: Uri): Long = prefs.getLong(uri.toString(), 0L)

    fun save(uri: Uri?, positionMs: Long) {
        if (uri != null && positionMs > 5_000) prefs.edit().putLong(uri.toString(), positionMs).apply()
    }
}

