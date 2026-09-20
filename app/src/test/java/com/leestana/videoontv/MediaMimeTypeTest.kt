package com.leestana.videoontv

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaMimeTypeTest {
    @Test fun `keeps a useful provider MIME type`() {
        assertEquals("video/mp4", MediaMimeType.resolve("video/mp4", "movie.bin"))
    }

    @Test fun `recovers MIME type from USB file extension`() {
        assertEquals("video/mp4", MediaMimeType.resolve("application/octet-stream", "movie.MP4"))
        assertEquals("video/x-matroska", MediaMimeType.resolve(null, "movie.mkv"))
        assertEquals("audio/mpeg", MediaMimeType.resolve(null, "track.mp3"))
    }
}
