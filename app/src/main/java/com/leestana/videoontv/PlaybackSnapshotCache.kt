package com.leestana.videoontv

import java.util.concurrent.atomic.AtomicReference

data class PlaybackSnapshot(val positionMs: Long, val playing: Boolean, val speed: Float)

class PlaybackSnapshotCache {
    private val snapshot = AtomicReference(PlaybackSnapshot(0, false, 1f))

    fun update(positionMs: Long, playing: Boolean, speed: Float) {
        snapshot.set(PlaybackSnapshot(positionMs.coerceAtLeast(0), playing, speed))
    }

    fun current(): PlaybackSnapshot = snapshot.get()
}
