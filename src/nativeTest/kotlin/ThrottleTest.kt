package srv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ThrottleTest {

    @Test
    fun parsesPresets() {
        assertEquals(Throttle(750_000, 300.milliseconds), parseThrottle("3G"))
    }

    @Test
    fun parsesRates() {
        assertEquals(Throttle(500, Duration.ZERO), parseThrottle("500"))
        assertEquals(Throttle(500_000, Duration.ZERO), parseThrottle("500k"))
        assertEquals(Throttle(1_500_000, Duration.ZERO), parseThrottle("1.5m"))
        assertEquals(Throttle(2_000_000_000, Duration.ZERO), parseThrottle("2G"))
    }

    @Test
    fun rejectsInvalidRates() {
        listOf("", "0", "k", "1.", ".5m", "1.2.3", "-1", "abc", "1x").forEach { text ->
            assertNull(parseThrottle(text), "'$text'")
        }
    }

    @Test
    fun describesItself() {
        assertEquals("750 kbit/s, 300ms latency", parseThrottle("3g").toString())
        assertEquals("1.5 Mbit/s", parseThrottle("1.5m").toString())
    }
}
