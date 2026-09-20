package com.leestana.videoontv

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeekTargetTest {
    @Test fun `seeks relative to the current position`() {
        assertEquals(40_000L, SeekTarget.calculate(30_000, 120_000, 10_000, true))
        assertEquals(20_000L, SeekTarget.calculate(30_000, 120_000, -10_000, true))
    }

    @Test fun `clamps at media boundaries`() {
        assertEquals(0L, SeekTarget.calculate(4_000, 120_000, -10_000, true))
        assertEquals(120_000L, SeekTarget.calculate(118_000, 120_000, 10_000, true))
    }

    @Test fun `unknown duration still supports relative forward seeking`() {
        assertEquals(40_000L, SeekTarget.calculate(30_000, C.TIME_UNSET, 10_000, true))
        assertEquals(40_000L, SeekTarget.calculate(30_000, 0, 10_000, true))
        assertEquals(20_000L, SeekTarget.calculate(30_000, 0, -10_000, true))
    }

    @Test fun `does not reset when position is unavailable or media is not seekable`() {
        assertNull(SeekTarget.calculate(C.TIME_UNSET, C.TIME_UNSET, 10_000, true))
        assertNull(SeekTarget.calculate(30_000, 120_000, 10_000, false))
    }
}
