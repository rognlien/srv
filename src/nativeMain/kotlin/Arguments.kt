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
    val directory: String,
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
        ArgumentParser(args.toList()).parse()
    } catch (exception: ArgumentException) {
        Command.Invalid(exception.message.orEmpty())
    }

private class ArgumentParser(arguments: List<String>) {
    private val remaining = ArrayDeque(arguments)
    private var port = DEFAULT_PORT
    private var idleTimeout: Duration? = null
    private var throttle: Throttle? = null

    fun parse(): Command {
        var command: Command? = null
        while (command == null && nextIsOption()) {
            command = parseOption(remaining.removeFirst())
        }
        return command ?: Command.Serve(Options(port, idleTimeout, throttle, parseDirectory()))
    }

    private fun nextIsOption(): Boolean =
        remaining.firstOrNull()?.let { it.startsWith("-") && it != "--" } ?: false

    private fun parseOption(option: String): Command? = when (option) {
        "-h", "--help" -> Command.Help
        "-V", "--version" -> Command.Version
        else -> null.also { applyOption(option) }
    }

    private fun applyOption(option: String) {
        when (option) {
            "-p", "--port" -> port = parsePort(nextValue(option))
            "-i", "--idle" -> idleTimeout = parseDuration(nextValue(option))
            "-t", "--throttle" -> throttle = parseThrottleArgument(nextValue(option))
            else -> throw ArgumentException("unknown option '$option'")
        }
    }

    private fun nextValue(option: String): String =
        remaining.removeFirstOrNull() ?: throw ArgumentException("option '$option' requires a value")

    private fun parseDirectory(): String {
        val argument = remaining.firstOrNull()
        return when {
            argument == null -> "."
            remaining.size == 1 && fileInfo(argument)?.isDirectory == true -> argument
            argument.toIntOrNull() != null -> throw ArgumentException("to choose a port, use -p $argument")
            else -> throw ArgumentException("'$argument' is not a directory")
        }
    }
}

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
