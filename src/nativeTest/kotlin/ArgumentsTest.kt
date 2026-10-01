package srv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ArgumentsTest {

    @Test
    fun servesTheCurrentDirectoryByDefault() {
        val localhost = BindAddress("127.0.0.1", listOf(127, 0, 0, 1))
        assertEquals(Command.Serve(Options(8000, localhost, null, null, Source.Directory("."))), parse())
    }

    @Test
    fun bindsToTheGivenAddress() {
        assertEquals("0.0.0.0", serve("-b", "0.0.0.0").bindAddress.text)
        assertEquals("::", serve("--bind", "::").bindAddress.text)
    }

    @Test
    fun parsesOptions() {
        val options = serve("-p", "9000", "--idle", "1h30m", "-t", "4g", "/")
        assertEquals(9000, options.port)
        assertEquals(90.minutes, options.idleTimeout)
        assertEquals(throttlePresets["4g"], options.throttle)
        assertEquals(Source.Directory("/"), options.source)
    }

    @Test
    fun readsPlainNumbersAsSeconds() {
        assertEquals(90.seconds, serve("-i", "90").idleTimeout)
    }

    @Test
    fun runsEverythingAfterExecAsTheCommand() {
        val source = assertIs<Source.Program>(serve("-c", "application/json", "-x", "sh", "-c", "echo -p").source)
        assertTrue(source.executable.endsWith("/sh"), source.executable)
        assertEquals(listOf("sh", "-c", "echo -p"), source.arguments)
        assertEquals("application/json", source.contentType)
    }

    @Test
    fun answersHelpAndVersion() {
        assertEquals(Command.Help, parse("-p", "9000", "--help"))
        assertEquals(Command.Version, parse("-V"))
    }

    @Test
    fun rejectsInvalidArguments() {
        assertInvalid("invalid port '0'", "-p", "0")
        assertInvalid("invalid address 'localhost'; give an IP address such as 127.0.0.1, 0.0.0.0 or ::", "-b", "localhost")
        assertInvalid("invalid duration '10x'", "-i", "10x")
        assertInvalid("invalid duration '0s'", "-i", "0s")
        assertInvalid("invalid throttle 'fast'", "-t", "fast")
        assertInvalid("option '-p' requires a value", "-p")
        assertInvalid("option '-x' requires a command", "-x")
        assertInvalid("unknown option '--bogus'", "--bogus")
        assertInvalid("-c only applies to commands (-x)", "-c", "text/html")
        assertInvalid("to choose a port, use -p 8080", "8080")
    }

    private fun parse(vararg arguments: String): Command = parseArguments(arrayOf(*arguments))

    private fun serve(vararg arguments: String): Options = assertIs<Command.Serve>(parse(*arguments)).options

    private fun assertInvalid(message: String, vararg arguments: String) {
        assertEquals(Command.Invalid(message), parse(*arguments), arguments.joinToString(" "))
    }
}
