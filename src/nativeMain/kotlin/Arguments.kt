package srv

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val DEFAULT_PORT = 8000
private const val DEFAULT_BIND_ADDRESS = "127.0.0.1"

private val durationUnits = mapOf(
    's' to DurationUnit.SECONDS,
    'm' to DurationUnit.MINUTES,
    'h' to DurationUnit.HOURS,
)

private const val DEFAULT_CONTENT_TYPE = "text/plain; charset=utf-8"

sealed interface Source {
    data class Directory(val path: String) : Source
    data class Program(val executable: String, val arguments: List<String>, val contentType: String) : Source
}

data class Options(
    val port: Int,
    val bindAddress: BindAddress,
    val idleTimeout: Duration?,
    val throttle: Throttle?,
    val source: Source,
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
    private var bindAddress = parseBindAddressArgument(DEFAULT_BIND_ADDRESS)
    private var idleTimeout: Duration? = null
    private var throttle: Throttle? = null
    private var contentType: String? = null
    private var programArguments: List<String>? = null

    fun parse(): Command {
        var command: Command? = null
        while (command == null && nextIsOption()) {
            command = parseOption(remaining.removeFirst())
        }
        return command ?: Command.Serve(Options(port, bindAddress, idleTimeout, throttle, parseSource()))
    }

    private fun nextIsOption(): Boolean = remaining.firstOrNull()?.startsWith("-") ?: false

    private fun parseOption(option: String): Command? = when (option) {
        "-h", "--help" -> Command.Help
        "-V", "--version" -> Command.Version
        else -> null.also { applyOption(option) }
    }

    private fun applyOption(option: String) {
        when (option) {
            "-p", "--port" -> port = parsePort(nextValue(option))
            "-b", "--bind" -> bindAddress = parseBindAddressArgument(nextValue(option))
            "-i", "--idle" -> idleTimeout = parseDuration(nextValue(option))
            "-t", "--throttle" -> throttle = parseThrottleArgument(nextValue(option))
            "-c", "--content-type" -> contentType = parseContentType(nextValue(option))
            "-x", "--exec" -> programArguments = remaining.toList().also { remaining.clear() }
            else -> throw ArgumentException("unknown option '$option'")
        }
    }

    private fun nextValue(option: String): String =
        remaining.removeFirstOrNull() ?: throw ArgumentException("option '$option' requires a value")

    private fun parseSource(): Source {
        val arguments = programArguments
        val source = if (arguments == null) parseDirectory() else parseProgram(arguments)
        if (source is Source.Directory && contentType != null) {
            throw ArgumentException("-c only applies to commands (-x)")
        }
        return source
    }

    private fun parseDirectory(): Source.Directory {
        val argument = remaining.firstOrNull()
        return when {
            argument == null -> Source.Directory(".")
            fileInfo(argument)?.isDirectory == true && remaining.size == 1 -> Source.Directory(argument)
            findExecutable(argument) != null ->
                throw ArgumentException("'$argument' is not a directory; to run it as a command, use -x ${remaining.joinToString(" ")}")
            remaining.size > 1 -> throw ArgumentException("too many arguments")
            argument.toIntOrNull() != null -> throw ArgumentException("to choose a port, use -p $argument")
            else -> throw ArgumentException("'$argument' is not a directory")
        }
    }

    private fun parseProgram(arguments: List<String>): Source.Program {
        val name = arguments.firstOrNull() ?: throw ArgumentException("option '-x' requires a command")
        val executable = findExecutable(name) ?: throw ArgumentException("command not found: $name")
        return Source.Program(executable, arguments, contentType ?: DEFAULT_CONTENT_TYPE)
    }
}

private fun parsePort(argument: String): Int =
    argument.toIntOrNull()?.takeIf { it in 1..65535 } ?: throw ArgumentException("invalid port '$argument'")

private fun parseBindAddressArgument(argument: String): BindAddress =
    parseBindAddress(argument)
        ?: throw ArgumentException("invalid address '$argument'; give an IP address such as 127.0.0.1, 0.0.0.0 or ::")

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

private fun parseContentType(argument: String): String =
    argument.takeIf { "/" in it }
        ?: throw ArgumentException("-c expects a content type such as application/json, got '$argument'")

private fun parseThrottleArgument(argument: String): Throttle =
    parseThrottle(argument) ?: throw ArgumentException("invalid throttle '$argument'")
