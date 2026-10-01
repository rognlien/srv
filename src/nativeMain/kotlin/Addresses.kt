@file:OptIn(ExperimentalForeignApi::class)

package srv

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import platform.posix.AF_INET
import platform.posix.AF_INET6

data class BindAddress(val text: String, val bytes: List<Int>) {
    val isIpv6: Boolean get() = bytes.size == 16

    val urlHost: String
        get() = when {
            bytes.all { it == 0 } || text == "127.0.0.1" -> "localhost"
            isIpv6 -> "[$text]"
            else -> text
        }
}

fun parseBindAddress(text: String): BindAddress? =
    (addressBytes(AF_INET, text, 4) ?: addressBytes(AF_INET6, text, 16))?.let { BindAddress(text, it) }

private fun addressBytes(family: Int, text: String, size: Int): List<Int>? = memScoped {
    val buffer = allocArray<UByteVar>(size)
    if (parseAddressText(family, text, buffer) == 1) List(size) { buffer[it].toInt() } else null
}

private val ipv4MappedPrefix = List(10) { 0 } + listOf(0xFF, 0xFF)

fun formatAddress(bytes: List<Int>): String =
    if (bytes.take(12) == ipv4MappedPrefix) {
        bytes.drop(12).joinToString(".")
    } else {
        formatIpv6(bytes.chunked(2) { (high, low) -> (high shl 8) or low })
    }

private fun formatIpv6(groups: List<Int>): String {
    val zeroRun = longestZeroRun(groups)
    return if (zeroRun == null) {
        groups.joinToString(":") { it.toString(16) }
    } else {
        val before = groups.subList(0, zeroRun.first).joinToString(":") { it.toString(16) }
        val after = groups.subList(zeroRun.last + 1, groups.size).joinToString(":") { it.toString(16) }
        "$before::$after"
    }
}

private fun longestZeroRun(groups: List<Int>): IntRange? {
    var longest: IntRange? = null
    var runStart: Int? = null
    for (index in 0..groups.size) {
        val isZero = index < groups.size && groups[index] == 0
        if (isZero && runStart == null) {
            runStart = index
        }
        if (!isZero && runStart != null) {
            val run = runStart until index
            if (run.count() >= 2 && run.count() > (longest?.count() ?: 0)) {
                longest = run
            }
            runStart = null
        }
    }
    return longest
}
