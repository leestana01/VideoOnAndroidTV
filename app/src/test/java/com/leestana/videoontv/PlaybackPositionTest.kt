package com.leestana.videoontv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPositionTest {
    @Test fun `resume requires a meaningful saved position`() {
        assertFalse(PlaybackPosition.canResume(4_999))
        assertTrue(PlaybackPosition.canResume(5_000))
    }

    @Test fun `formats positions for remote resume prompt`() {
        assertEquals("0:00", PlaybackPosition.format(-1))
        assertEquals("2:05", PlaybackPosition.format(125_000))
        assertEquals("1:02:03", PlaybackPosition.format(3_723_000))
    }
}
