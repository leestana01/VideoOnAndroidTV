package com.leestana.videoontv

import androidx.media3.common.C
import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekCapabilityTest {
    @Test fun `uses Media3 seekability when available`() {
        assertTrue(SeekCapability.canSeek(true, false, 12_000, C.TIME_UNSET, Player.STATE_BUFFERING))
    }

    @Test fun `allows ready local media with known duration when provider flag is missing`() {
        assertTrue(SeekCapability.canSeek(false, true, 12_000, 120_000, Player.STATE_READY))
    }

    @Test fun `rejects remote unknown and unprepared media`() {
        assertFalse(SeekCapability.canSeek(false, false, 12_000, 120_000, Player.STATE_READY))
        assertFalse(SeekCapability.canSeek(false, true, 12_000, C.TIME_UNSET, Player.STATE_READY))
        assertFalse(SeekCapability.canSeek(false, true, 12_000, 120_000, Player.STATE_BUFFERING))
        assertFalse(SeekCapability.canSeek(true, true, C.TIME_UNSET, 120_000, Player.STATE_READY))
    }
}
