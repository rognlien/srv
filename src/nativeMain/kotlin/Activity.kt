@file:OptIn(ExperimentalAtomicApi::class)

package srv

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class Activity {
    private val start = TimeSource.Monotonic.markNow()
    private val activeConnections = AtomicInt(0)
    private val lastActivity = AtomicLong(0)

    fun connectionStarted() {
        activeConnections.incrementAndFetch()
        touch()
    }

    fun connectionFinished() {
        touch()
        activeConnections.decrementAndFetch()
    }

    fun idleTime(): Duration =
        if (activeConnections.load() > 0) Duration.ZERO else (now() - lastActivity.load()).milliseconds

    private fun touch() = lastActivity.store(now())

    private fun now(): Long = start.elapsedNow().inWholeMilliseconds
}
