package com.leestana.videoontv

import androidx.media3.common.C
import androidx.media3.common.Player

object SeekCapability {
    fun canSeek(
        media3Seekable: Boolean,
        localMedia: Boolean,
        positionMs: Long,
        durationMs: Long,
        playbackState: Int,
    ): Boolean {
        if (positionMs == C.TIME_UNSET || positionMs < 0) return false
        if (media3Seekable) return true
        return localMedia &&
            playbackState == Player.STATE_READY &&
            durationMs != C.TIME_UNSET &&
            durationMs > 0
    }
}
