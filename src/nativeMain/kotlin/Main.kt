@file:OptIn(ExperimentalForeignApi::class)

package srv

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.chdir
import platform.posix.fputs
import platform.posix.stderr
import kotlin.system.exitProcess

private val HELP = """
    |Usage: srv [options] [directory]
    |       srv [options] -c command [argument...]
    |
    |Serve a directory (default: the current directory) or the output of a
    |command over HTTP.
    |
    |Options:
    |  -p, --port PORT      Port to listen on (default: 8000)
    |  -i, --idle DURATION  Stop after DURATION without requests,
    |                       e.g. 90 (seconds), 30s, 10m or 1h
    |  -t, --throttle RATE  Limit bandwidth to simulate a slow connection.
    |                       A preset (56k, edge, 3g, 4g) also adds latency,
    |                       or give bits per second, e.g. 500k, 2m or 1.5m
    |  -c, --command ...    Run the command for each request and serve its
    |                       output. Everything after -c belongs to the command.
    |  --content-type TYPE  Content type of the command output
    |                       (default: text/plain; charset=utf-8)
    |  -h, --help           Show this help and exit
    |  -V, --version        Show the version and exit
    |
    |See 'man srv' for more information.
    """.trimMargin()

fun main(args: Array<String>) {
    when (val command = parseArguments(args)) {
        is Command.Serve -> serve(command.options)
        Command.Help -> println(HELP)
        Command.Version -> println("srv $VERSION")
        is Command.Invalid -> fail("${command.message}\nTry 'srv --help' for more information.", 2)
    }
}

private fun serve(options: Options) {
    val source = options.source
    if (source is Source.Directory && chdir(source.path) != 0) {
        fail("unable to enter directory '${source.path}'", 1)
    }
    try {
        HttpServer(options).start()
    } catch (exception: ServerException) {
        fail(exception.message.orEmpty(), 1)
    }
}

private fun fail(message: String, exitCode: Int): Nothing {
    fputs("srv: $message\n", stderr)
    exitProcess(exitCode)
}
