package com.leestana.videoontv

import java.util.Locale

object PlaybackPosition {
    const val RESUME_THRESHOLD_MS = 5_000L

    fun canResume(positionMs: Long): Boolean = positionMs >= RESUME_THRESHOLD_MS

    fun format(positionMs: Long): String {
        val totalSeconds = positionMs.coerceAtLeast(0) / 1_000
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}
