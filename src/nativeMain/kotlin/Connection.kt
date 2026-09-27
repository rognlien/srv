@file:OptIn(ExperimentalForeignApi::class)

package srv

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.SOL_SOCKET
import platform.posix.SO_RCVTIMEO
import platform.posix.close
import platform.posix.localtime
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.strftime
import platform.posix.time
import platform.posix.time_tVar
import platform.posix.timeval

private const val MAX_HEAD_SIZE = 8192
private const val RECEIVE_TIMEOUT_SECONDS = 10
private const val HEAD_TERMINATOR = "\r\n\r\n"

class Connection(
    private val socket: Int,
    private val clientAddress: String,
    private val activity: Activity,
    private val pacer: Pacer?,
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
        val response = if (request == null) errorResponse(400, "Bad request") else FileHandler.handle(request)
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
        val buffer = ByteArray(MAX_HEAD_SIZE)
        var total = 0
        var complete = false
        while (!complete && total < buffer.size) {
            val count = buffer.usePinned { recv(socket, it.addressOf(total), (buffer.size - total).convert(), 0) }.toInt()
            total += maxOf(count, 0)
            complete = count <= 0 || buffer.decodeToString(0, total).contains(HEAD_TERMINATOR)
        }
        return buffer.decodeToString(0, total)
    }

    private fun sendHead(response: Response) {
        val head = buildString {
            append("HTTP/1.1 ${response.status} ${reasonPhrase(response.status)}\r\n")
            append("Server: srv\r\n")
            append("Connection: close\r\n")
            append("Content-Length: ${response.body.length}\r\n")
            response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
            append("\r\n")
        }
        val bytes = head.encodeToByteArray()
        sendBytes(bytes, bytes.size)
    }

    private fun sendBody(body: Body) = when (body) {
        is Body.Bytes -> sendBytes(body.content, body.content.size)
        is Body.File -> streamFile(body.path, ::sendBytes)
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
        val buffer = allocArray<ByteVar>(64)
        strftime(buffer, 64u, "%d/%b/%Y %H:%M:%S", localtime(now.ptr))
        buffer.toKString()
    }
}
