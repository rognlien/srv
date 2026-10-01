@file:OptIn(ExperimentalForeignApi::class)

package srv

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.FD_CLOEXEC
import platform.posix.F_SETFD
import platform.posix.AF_INET6
import platform.posix.IPPROTO_IPV6
import platform.posix.IPV6_V6ONLY
import platform.posix.MSG_DONTWAIT
import platform.posix.POLLIN
import platform.posix.SIGPIPE
import platform.posix.SIG_IGN
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_REUSEADDR
import platform.posix.bind
import platform.posix.close
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.listen
import platform.posix.memset
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.pthread_create
import platform.posix.pthread_detach
import platform.posix.pthread_tVar
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.signal
import platform.posix.sockaddr_in
import platform.posix.sockaddr_in6
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.strerror

private const val BACKLOG = 128
private const val POLL_INTERVAL_MILLISECONDS = 1000
private const val MAX_CONNECTIONS = 128
private const val BUSY_RESPONSE = "HTTP/1.1 503 Service Unavailable\r\nServer: srv\r\nConnection: close\r\nContent-Length: 0\r\nRetry-After: 1\r\n\r\n"

class ServerException(message: String) : Exception(message)

class HttpServer(private val options: Options) {
    private val port = options.port
    private val bindAddress = options.bindAddress
    private val activity = Activity()
    private val pacer = options.throttle?.let(::Pacer)
    private val handler: (Request, String) -> Response = when (val source = options.source) {
        is Source.Directory -> { request, _ -> FileHandler.handle(request) }
        is Source.Program -> CommandHandler(source)::handle
    }

    fun start() {
        signal(SIGPIPE, SIG_IGN)
        val serverSocket = openServerSocket()
        println("Serving ${sourceDescription()} on ${bindAddress.text} port $port: http://${bindAddress.urlHost}:$port/${throttleNotice()}${idleNotice()}")
        while (!isIdle()) {
            if (waitForConnection(serverSocket)) {
                acceptConnection(serverSocket)
            }
        }
        close(serverSocket)
        println("No requests for ${options.idleTimeout}, stopping.")
    }

    private fun sourceDescription(): String = when (val source = options.source) {
        is Source.Directory -> if (source.path == ".") "HTTP" else source.path
        is Source.Program -> "the output of '${source.arguments.joinToString(" ")}'"
    }

    private fun throttleNotice(): String =
        options.throttle?.let { " Throttled to $it." }.orEmpty()

    private fun idleNotice(): String =
        options.idleTimeout?.let { " Stops after $it without requests." }.orEmpty()

    private fun isIdle(): Boolean =
        options.idleTimeout?.let { activity.idleTime() >= it } ?: false

    private fun waitForConnection(serverSocket: Int): Boolean = memScoped {
        val descriptor = alloc<pollfd>().apply {
            fd = serverSocket
            events = POLLIN.convert()
            revents = 0
        }
        poll(descriptor.ptr, 1u, POLL_INTERVAL_MILLISECONDS) > 0
    }

    private fun openServerSocket(): Int {
        val serverSocket = if (bindAddress.isIpv6) {
            bindIpv6(socket(AF_INET6, SOCK_STREAM, 0))
        } else {
            bindIpv4(socket(AF_INET, SOCK_STREAM, 0))
        }
        fcntl(serverSocket, F_SETFD, FD_CLOEXEC)
        failIf(listen(serverSocket, BACKLOG) != 0, "Unable to listen on port $port")
        return serverSocket
    }

    private fun bindIpv6(serverSocket: Int): Int = memScoped {
        failIf(serverSocket < 0, "Unable to create socket")
        setOption(serverSocket, SOL_SOCKET, SO_REUSEADDR, 1)
        setOption(serverSocket, IPPROTO_IPV6, IPV6_V6ONLY, 0)
        val address = alloc<sockaddr_in6>()
        memset(address.ptr, 0, sizeOf<sockaddr_in6>().convert())
        address.sin6_family = AF_INET6.convert()
        address.sin6_port = toNetworkByteOrder(port)
        copyAddress(address.sin6_addr.ptr.reinterpret())
        failIf(bind(serverSocket, address.ptr.reinterpret(), sizeOf<sockaddr_in6>().convert()) != 0, bindFailure())
        serverSocket
    }

    private fun bindIpv4(serverSocket: Int): Int = memScoped {
        failIf(serverSocket < 0, "Unable to create socket")
        setOption(serverSocket, SOL_SOCKET, SO_REUSEADDR, 1)
        val address = alloc<sockaddr_in>()
        memset(address.ptr, 0, sizeOf<sockaddr_in>().convert())
        address.sin_family = AF_INET.convert()
        address.sin_port = toNetworkByteOrder(port)
        copyAddress(address.sin_addr.ptr.reinterpret())
        failIf(bind(serverSocket, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) != 0, bindFailure())
        serverSocket
    }

    private fun copyAddress(target: CPointer<UByteVar>) {
        bindAddress.bytes.forEachIndexed { index, byte -> target[index] = byte.toUByte() }
    }

    private fun bindFailure(): String = "Unable to bind to ${bindAddress.text} port $port"

    private fun setOption(socket: Int, level: Int, option: Int, value: Int) = memScoped {
        val optionValue = alloc<IntVar>().apply { this.value = value }
        setsockopt(socket, level, option, optionValue.ptr, sizeOf<IntVar>().convert())
    }

    private fun acceptConnection(serverSocket: Int) = memScoped {
        val address = alloc<sockaddr_in6>()
        val addressLength = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_in6>().convert() }
        val clientSocket = acceptClient(serverSocket, address.ptr, addressLength.ptr)
        when {
            clientSocket < 0 -> Unit
            activity.activeConnectionCount() >= MAX_CONNECTIONS -> rejectConnection(clientSocket)
            else -> startConnection(clientSocket, formatClientAddress(address))
        }
    }

    private fun startConnection(clientSocket: Int, clientAddress: String) {
        activity.connectionStarted()
        if (!startThread(Connection(clientSocket, clientAddress, activity, pacer, handler))) {
            close(clientSocket)
            activity.connectionFinished()
        }
    }

    private fun rejectConnection(clientSocket: Int) {
        val bytes = BUSY_RESPONSE.encodeToByteArray()
        bytes.usePinned { send(clientSocket, it.addressOf(0), bytes.size.convert(), MSG_DONTWAIT) }
        close(clientSocket)
    }

    private fun formatClientAddress(address: sockaddr_in6): String =
        if (address.sin6_family.toInt() == AF_INET) {
            val bytes = address.ptr.reinterpret<sockaddr_in>().pointed.sin_addr.ptr.reinterpret<UByteVar>()
            List(4) { bytes[it].toInt() }.joinToString(".")
        } else {
            val bytes = address.sin6_addr.ptr.reinterpret<UByteVar>()
            formatAddress(List(16) { bytes[it].toInt() })
        }

    private fun startThread(connection: Connection): Boolean = memScoped {
        val thread = alloc<pthread_tVar>()
        val reference = StableRef.create(connection)
        val isStarted = pthread_create(thread.ptr, null, staticCFunction(::runConnection), reference.asCPointer()) == 0
        if (isStarted) {
            pthread_detach(thread.value)
        } else {
            reference.dispose()
        }
        isStarted
    }

    private fun failIf(condition: Boolean, message: String) {
        if (condition) {
            throw ServerException("$message: ${strerror(errno)?.toKString()}")
        }
    }

    private fun toNetworkByteOrder(value: Int): UShort =
        (((value and 0xFF) shl 8) or ((value shr 8) and 0xFF)).toUShort()
}

private fun runConnection(argument: COpaquePointer?): COpaquePointer? {
    val reference = argument!!.asStableRef<Connection>()
    val connection = reference.get()
    reference.dispose()
    connection.handle()
    return null
}
