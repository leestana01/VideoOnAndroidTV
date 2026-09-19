package com.leestana.videoontv

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioGainTest {
    @Test fun `gain clamps and maps safely`() {
        assertEquals(0f, AudioGain.playerVolume(-5))
        assertEquals(1f, AudioGain.playerVolume(400))
        assertEquals(0, AudioGain.gainMillibels(100))
        assertEquals(1204, AudioGain.gainMillibels(400))
        assertEquals(AudioGain.MAX_PERCENT, AudioGain.clamp(999))
    }
}

