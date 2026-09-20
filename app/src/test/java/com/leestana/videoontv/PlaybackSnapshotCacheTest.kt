package com.leestana.videoontv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PlaybackSnapshotCacheTest {
    @Test
    fun `network thread reads latest immutable player snapshot`() {
        val cache = PlaybackSnapshotCache()
        cache.update(12_345, true, 1.25f)

        val executor = Executors.newSingleThreadExecutor()
        val finished = CountDownLatch(1)
        var observed: PlaybackSnapshot? = null
        executor.execute {
            observed = cache.current()
            finished.countDown()
        }
        assertTrue(finished.await(2, TimeUnit.SECONDS))
        executor.shutdownNow()

        assertEquals(PlaybackSnapshot(12_345, true, 1.25f), observed)
    }

    @Test
    fun `negative player positions are clamped`() {
        val cache = PlaybackSnapshotCache()
        cache.update(-1, false, 1f)

        assertEquals(0, cache.current().positionMs)
    }
}
