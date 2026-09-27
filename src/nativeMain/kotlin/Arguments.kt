package srv

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val DEFAULT_PORT = 8000

private val durationUnits = mapOf(
    's' to DurationUnit.SECONDS,
    'm' to DurationUnit.MINUTES,
    'h' to DurationUnit.HOURS,
)

data class Options(
    val port: Int,
    val idleTimeout: Duration?,
    val throttle: Throttle?,
)

sealed interface Command {
    data class Serve(val options: Options) : Command
    data object Help : Command
    data object Version : Command
    data class Invalid(val message: String) : Command
}

private class ArgumentException(message: String) : Exception(message)

fun parseArguments(args: Array<String>): Command =
    try {
        parseCommand(args.toList())
    } catch (exception: ArgumentException) {
        Command.Invalid(exception.message.orEmpty())
    }

private fun parseCommand(args: List<String>): Command = when {
    args.any { it == "-h" || it == "--help" } -> Command.Help
    args.any { it == "-V" || it == "--version" } -> Command.Version
    else -> Command.Serve(parseOptions(args))
}

private fun parseOptions(args: List<String>): Options {
    var port: Int? = null
    var idleTimeout: Duration? = null
    var throttle: Throttle? = null
    val iterator = args.iterator()
    while (iterator.hasNext()) {
        val argument = iterator.next()
        when {
            argument == "-i" || argument == "--idle" -> idleTimeout = parseDuration(requireValue(argument, iterator))
            argument == "-t" || argument == "--throttle" -> throttle = parseThrottleArgument(requireValue(argument, iterator))
            argument.startsWith("-") -> throw ArgumentException("unknown option '$argument'")
            port != null -> throw ArgumentException("too many arguments")
            else -> port = parsePort(argument)
        }
    }
    return Options(port ?: DEFAULT_PORT, idleTimeout, throttle)
}

private fun requireValue(option: String, iterator: Iterator<String>): String =
    if (iterator.hasNext()) iterator.next() else throw ArgumentException("option '$option' requires a value")

private fun parsePort(argument: String): Int =
    argument.toIntOrNull()?.takeIf { it in 1..65535 } ?: throw ArgumentException("invalid port '$argument'")

private fun parseDuration(argument: String): Duration {
    val duration = argument.toLongOrNull()?.seconds ?: parseDurationWithUnits(argument.replace(" ", ""))
    return duration?.takeIf { it.isPositive() } ?: throw ArgumentException("invalid duration '$argument'")
}

private fun parseDurationWithUnits(text: String): Duration? {
    var total = Duration.ZERO
    var digits = ""
    var isValid = text.isNotEmpty()
    for (character in text) {
        val unit = durationUnits[character]
        val amount = digits.toLongOrNull()
        when {
            character.isDigit() -> digits += character
            unit != null && amount != null -> total += amount.toDuration(unit).also { digits = "" }
            else -> isValid = false
        }
    }
    return total.takeIf { isValid && digits.isEmpty() }
}

private fun parseThrottleArgument(argument: String): Throttle =
    parseThrottle(argument) ?: throw ArgumentException("invalid throttle '$argument'")
