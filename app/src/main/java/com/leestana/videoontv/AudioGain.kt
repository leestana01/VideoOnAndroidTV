package com.leestana.videoontv

import kotlin.math.log10

object AudioGain {
    const val MIN_PERCENT = 0
    const val MAX_PERCENT = 400

    fun clamp(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    fun playerVolume(percent: Int): Float = (clamp(percent).coerceAtMost(100) / 100f)

    fun gainMillibels(percent: Int): Int {
        val multiplier = clamp(percent) / 100.0
        if (multiplier <= 1.0) return 0
        return (20.0 * log10(multiplier) * 100.0).toInt()
    }
}

