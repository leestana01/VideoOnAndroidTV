package com.leestana.videoontv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpRangeTest {
    @Test fun `parses bounded open and suffix ranges`() {
        assertEquals(HttpRange(10, 19), HttpRange.parse("bytes=10-19", 100))
        assertEquals(HttpRange(90, 99), HttpRange.parse("bytes=-10", 100))
        assertEquals(HttpRange(90, 99), HttpRange.parse("bytes=90-", 100))
    }

    @Test fun `rejects invalid ranges`() {
        assertNull(HttpRange.parse("bytes=100-120", 100))
        assertNull(HttpRange.parse("items=1-2", 100))
        assertNull(HttpRange.parse(null, 100))
    }
}

