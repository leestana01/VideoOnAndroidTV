package com.leestana.videoontv

import androidx.media3.common.C

object SeekTarget {
    fun calculate(positionMs: Long, durationMs: Long, deltaMs: Long, isSeekable: Boolean): Long? {
        if (!isSeekable || positionMs == C.TIME_UNSET || positionMs < 0) return null
        val upperBound = if (durationMs <= 0) Long.MAX_VALUE else durationMs
        val target = if (deltaMs >= 0) {
            positionMs.coerceAtMost(Long.MAX_VALUE - deltaMs).plus(deltaMs)
        } else {
            val magnitude = if (deltaMs == Long.MIN_VALUE) Long.MAX_VALUE else -deltaMs
            if (positionMs <= magnitude) 0 else positionMs - magnitude
        }
        return target.coerceAtMost(upperBound)
    }
}
