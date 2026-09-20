package com.leestana.videoontv

import androidx.media3.common.C

object SeekTarget {
    fun calculate(positionMs: Long, durationMs: Long, deltaMs: Long, isSeekable: Boolean): Long? {
        if (!isSeekable || positionMs == C.TIME_UNSET || positionMs < 0) return null
        val upperBound = if (durationMs == C.TIME_UNSET || durationMs < 0) Long.MAX_VALUE else durationMs
        return if (deltaMs >= 0) {
            positionMs.coerceAtMost(Long.MAX_VALUE - deltaMs).plus(deltaMs).coerceAtMost(upperBound)
        } else {
            positionMs.plus(deltaMs).coerceAtLeast(0)
        }
    }
}
