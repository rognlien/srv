@file:OptIn(ExperimentalForeignApi::class)

package srv

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.SOL_SOCKET
import platform.posix.SO_RCVTIMEO
import platform.posix.close
import platform.posix.stderr
import platform.posix.pollfd
import platform.posix.poll
import platform.posix.fputs
import platform.posix.POLLIN
import platform.posix.localtime_r
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.strftime
import platform.posix.time
import platform.posix.time_tVar
import platform.posix.tm
import platform.posix.timeval
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val MAX_HEAD_SIZE = 8192
private const val RECEIVE_TIMEOUT_SECONDS = 10
private val HEAD_TIMEOUT = 10.seconds
private const val HEAD_TERMINATOR = "\r\n\r\n"
private const val OUTPUT_CHUNK_SIZE = 64 * 1024
private const val DISCARD_BUFFER_SIZE = 1024

private enum class Event { OUTPUT, DISCONNECT, NONE }

class Connection(
    private val socket: Int,
    private val clientAddress: String,
    private val activity: Activity,
    private val pacer: Pacer?,
    private val handler: (Request, String) -> Response,
) {
    private var failed = false

    fun handle() {
        setReceiveTimeout()
        val head = readHead()
        if (head.isNotEmpty()) {
            respond(parseRequest(head))
        }
        close(socket)
        activity.connectionFinished()
    }

    private fun respond(request: Request?) {
        val response = if (request == null) errorResponse(400, "Bad request") else handler(request, clientAddress)
        pacer?.delayResponse()
        sendHead(response)
        if (request?.method != "HEAD") {
            sendBody(response.body)
        }
        log(request?.requestLine ?: "-", response.status)
    }

    private fun setReceiveTimeout() = memScoped {
        val timeout = alloc<timeval>().apply {
            tv_sec = RECEIVE_TIMEOUT_SECONDS.convert()
            tv_usec = 0
        }
        setsockopt(socket, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert())
    }

    private fun readHead(): String {
        val deadline = TimeSource.Monotonic.markNow() + HEAD_TIMEOUT
        val buffer = ByteArray(MAX_HEAD_SIZE)
        var total = 0
        var complete = false
        while (!complete && total < buffer.size) {
            val count = if (awaitInput(deadline)) receive(buffer, total) else 0
            total += maxOf(count, 0)
            complete = count <= 0 || buffer.decodeToString(0, total).contains(HEAD_TERMINATOR)
        }
        return buffer.decodeToString(0, total)
    }

    private fun awaitInput(deadline: TimeMark): Boolean = memScoped {
        val descriptor = alloc<pollfd>().apply {
            fd = socket
            events = POLLIN.convert()
            revents = 0
        }
        val remaining = (-deadline.elapsedNow()).inWholeMilliseconds.coerceAtLeast(0)
        poll(descriptor.ptr, 1u, remaining.toInt()) > 0
    }

    private fun receive(buffer: ByteArray, offset: Int): Int =
        buffer.usePinned { recv(socket, it.addressOf(offset), (buffer.size - offset).convert(), 0) }.toInt()

    private fun sendHead(response: Response) {
        val head = buildString {
            append("HTTP/1.1 ${response.status} ${reasonPhrase(response.status)}\r\n")
            append("Server: srv\r\n")
            append("Connection: close\r\n")
            response.body.length?.let { append("Content-Length: $it\r\n") }
            response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
            append("\r\n")
        }
        val bytes = head.encodeToByteArray()
        sendBytes(bytes, bytes.size)
    }

    private fun sendBody(body: Body) = when (body) {
        is Body.Bytes -> sendBytes(body.content, body.content.size)
        is Body.File -> sendFileBody(body)
        is Body.Process -> streamProcess(body.process)
        Body.Omitted -> Unit
    }

    private fun streamProcess(process: ChildProcess) {
        val buffer = ByteArray(OUTPUT_CHUNK_SIZE)
        var isFinished = false
        while (!isFinished && !failed) {
            when (waitForEvent(process.output)) {
                Event.OUTPUT -> {
                    val count = readChunk(process.output, buffer)
                    isFinished = count <= 0 || !sendBytes(buffer, count)
                }
                Event.DISCONNECT -> failed = true
                Event.NONE -> Unit
            }
        }
        val status = process.finish(terminate = failed)
        if (!failed && status != 0) {
            fputs("srv: command exited with status $status\n", stderr)
        }
    }

    private fun waitForEvent(output: Int): Event = memScoped {
        val descriptors = allocArray<pollfd>(2)
        descriptors[0].fd = output
        descriptors[0].events = POLLIN.convert()
        descriptors[1].fd = socket
        descriptors[1].events = POLLIN.convert()
        poll(descriptors, 2.convert(), -1)
        when {
            descriptors[1].revents.toInt() != 0 && isDisconnected() -> Event.DISCONNECT
            descriptors[0].revents.toInt() != 0 -> Event.OUTPUT
            else -> Event.NONE
        }
    }

    private fun isDisconnected(): Boolean {
        val buffer = ByteArray(DISCARD_BUFFER_SIZE)
        val count = buffer.usePinned { recv(socket, it.addressOf(0), buffer.size.convert(), 0) }.toInt()
        return count <= 0
    }

    private fun sendFileBody(file: Body.File) {
        if (pacer == null) {
            failed = !sendFile(file.path, file.length, socket)
        } else {
            streamFile(file.path, file.length, ::sendBytes)
        }
    }

    private fun sendBytes(bytes: ByteArray, length: Int): Boolean {
        val chunkSize = pacer?.chunkSize ?: length
        var offset = 0
        while (offset < length && !failed) {
            val size = minOf(chunkSize, length - offset)
            pacer?.awaitTurn(size)
            sendChunk(bytes, offset, size)
            offset += size
        }
        return !failed
    }

    private fun sendChunk(bytes: ByteArray, offset: Int, length: Int) {
        var sent = 0
        bytes.usePinned { pinned ->
            while (sent < length && !failed) {
                val count = send(socket, pinned.addressOf(offset + sent), (length - sent).convert(), 0).toInt()
                failed = count <= 0
                sent += maxOf(count, 0)
            }
        }
    }

    private fun log(requestLine: String, status: Int) {
        println("$clientAddress - - [${timestamp()}] \"$requestLine\" $status -")
    }

    private fun timestamp(): String = memScoped {
        val now = alloc<time_tVar>().apply { value = time(null) }
        val localTime = alloc<tm>()
        val buffer = allocArray<ByteVar>(64)
        strftime(buffer, 64u, "%d/%b/%Y %H:%M:%S", localtime_r(now.ptr, localTime.ptr))
        buffer.toKString()
    }
}
