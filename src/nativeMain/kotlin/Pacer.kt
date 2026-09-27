@file:OptIn(ExperimentalAtomicApi::class, ExperimentalForeignApi::class)

package srv

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.nanosleep
import platform.posix.timespec
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource

private const val CHUNKS_PER_SECOND = 20
private const val MAX_CHUNK_SIZE = 64 * 1024L
private const val NANOSECONDS_PER_SECOND = 1_000_000_000L
private const val CATCH_UP_NANOSECONDS = NANOSECONDS_PER_SECOND / CHUNKS_PER_SECOND

class Pacer(private val throttle: Throttle) {
    private val start = TimeSource.Monotonic.markNow()
    private val nextFreeSlot = AtomicLong(0)

    val chunkSize: Int = (throttle.bitsPerSecond / 8 / CHUNKS_PER_SECOND).coerceIn(1, MAX_CHUNK_SIZE).toInt()

    fun delayResponse() = sleepFor(throttle.latency)

    fun awaitTurn(byteCount: Int) {
        val cost = byteCount * 8L * NANOSECONDS_PER_SECOND / throttle.bitsPerSecond
        val slotEnd = reserveSlot(cost)
        sleepFor((slotEnd - now()).nanoseconds)
    }

    private fun reserveSlot(cost: Long): Long {
        var reserved: Long? = null
        while (reserved == null) {
            val current = nextFreeSlot.load()
            val candidate = maxOf(current, now() - CATCH_UP_NANOSECONDS) + cost
            if (nextFreeSlot.compareAndSet(current, candidate)) {
                reserved = candidate
            }
        }
        return reserved
    }

    private fun now(): Long = start.elapsedNow().inWholeNanoseconds
}

private fun sleepFor(duration: Duration) {
    if (duration.isPositive()) {
        memScoped {
            val time = alloc<timespec>().apply {
                tv_sec = duration.inWholeSeconds.convert()
                tv_nsec = (duration.inWholeNanoseconds % NANOSECONDS_PER_SECOND).convert()
            }
            nanosleep(time.ptr, null)
        }
    }
}
