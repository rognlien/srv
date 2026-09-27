package srv

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class Throttle(val bitsPerSecond: Long, val latency: Duration) {
    override fun toString(): String = listOfNotNull(
        formatRate(bitsPerSecond),
        latency.takeIf { it.isPositive() }?.let { "$it latency" },
    ).joinToString(", ")
}

val throttlePresets = mapOf(
    "56k" to Throttle(56_000, 150.milliseconds),
    "edge" to Throttle(240_000, 400.milliseconds),
    "3g" to Throttle(750_000, 300.milliseconds),
    "4g" to Throttle(9_000_000, 100.milliseconds),
)

private val rateMultipliers = mapOf("" to 1L, "k" to 1_000L, "m" to 1_000_000L, "g" to 1_000_000_000L)

private const val MAX_FRACTION_DIGITS = 9
private const val FRACTION_SCALE = 1_000_000_000L

fun parseThrottle(text: String): Throttle? {
    val normalized = text.lowercase()
    return throttlePresets[normalized] ?: parseRate(normalized)?.let { Throttle(it, Duration.ZERO) }
}

private fun parseRate(text: String): Long? {
    val suffix = text.takeLast(1).takeIf { it in rateMultipliers }.orEmpty()
    val rate = parseScaledDecimal(text.dropLast(suffix.length), rateMultipliers.getValue(suffix))
    return rate?.takeIf { it > 0 }
}

private fun parseScaledDecimal(number: String, multiplier: Long): Long? {
    val parts = number.split('.')
    val whole = parts[0]
    val fraction = parts.getOrElse(1) { "0" }
    val isValid = parts.size <= 2 &&
        whole.length in 1..MAX_FRACTION_DIGITS && whole.all { it.isDigit() } &&
        fraction.length in 1..MAX_FRACTION_DIGITS && fraction.all { it.isDigit() }
    return if (isValid) {
        whole.toLong() * multiplier + fraction.padEnd(MAX_FRACTION_DIGITS, '0').toLong() * multiplier / FRACTION_SCALE
    } else {
        null
    }
}

private fun formatRate(bitsPerSecond: Long): String = when {
    bitsPerSecond >= 1_000_000 -> "${bitsPerSecond / 1_000_000.0} Mbit/s"
    bitsPerSecond >= 1_000 -> "${bitsPerSecond / 1_000.0} kbit/s"
    else -> "$bitsPerSecond bit/s"
}.replace(".0 ", " ")
