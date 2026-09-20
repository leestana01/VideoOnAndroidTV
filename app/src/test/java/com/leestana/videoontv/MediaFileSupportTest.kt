package com.leestana.videoontv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFileSupportTest {
    @Test fun `accepts media mime types and common USB extensions`() {
        assertTrue(MediaFileSupport.isPlayable("movie.bin", "video/mp4"))
        assertTrue(MediaFileSupport.isPlayable("MOVIE.MKV", "application/octet-stream"))
        assertTrue(MediaFileSupport.isPlayable("concert.m2ts", null))
        assertTrue(MediaFileSupport.isPlayable("track.flac", null))
    }

    @Test fun `rejects unrelated files`() {
        assertFalse(MediaFileSupport.isPlayable("notes.txt", "text/plain"))
        assertFalse(MediaFileSupport.isPlayable(null, "application/octet-stream"))
    }
}
