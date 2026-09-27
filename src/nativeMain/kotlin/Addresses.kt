package srv

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
