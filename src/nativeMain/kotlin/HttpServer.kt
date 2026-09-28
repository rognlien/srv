@file:OptIn(ExperimentalForeignApi::class)

package srv

import kotlinx.cinterop.COpaquePointer
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
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.IPPROTO_IPV6
import platform.posix.IPV6_V6ONLY
import platform.posix.POLLIN
import platform.posix.SIGPIPE
import platform.posix.SIG_IGN
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_REUSEADDR
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.errno
import platform.posix.listen
import platform.posix.memset
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.pthread_create
import platform.posix.pthread_detach
import platform.posix.pthread_tVar
import platform.posix.setsockopt
import platform.posix.signal
import platform.posix.sockaddr_in
import platform.posix.sockaddr_in6
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.strerror

private const val BACKLOG = 128
private const val POLL_INTERVAL_MILLISECONDS = 1000

class ServerException(message: String) : Exception(message)

class HttpServer(private val options: Options) {
    private val port = options.port
    private val activity = Activity()
    private val pacer = options.throttle?.let(::Pacer)

    fun start() {
        signal(SIGPIPE, SIG_IGN)
        val serverSocket = openServerSocket()
        println("Serving HTTP on port $port: http://localhost:$port/${throttleNotice()}${idleNotice()}")
        while (!isIdle()) {
            if (waitForConnection(serverSocket)) {
                acceptConnection(serverSocket)
            }
        }
        close(serverSocket)
        println("No requests for ${options.idleTimeout}, stopping.")
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
        val ipv6Socket = socket(AF_INET6, SOCK_STREAM, 0)
        val serverSocket = if (ipv6Socket >= 0) bindIpv6(ipv6Socket) else bindIpv4(socket(AF_INET, SOCK_STREAM, 0))
        failIf(listen(serverSocket, BACKLOG) != 0, "Unable to listen on port $port")
        return serverSocket
    }

    private fun bindIpv6(serverSocket: Int): Int = memScoped {
        setOption(serverSocket, SOL_SOCKET, SO_REUSEADDR, 1)
        setOption(serverSocket, IPPROTO_IPV6, IPV6_V6ONLY, 0)
        val address = alloc<sockaddr_in6>()
        memset(address.ptr, 0, sizeOf<sockaddr_in6>().convert())
        address.sin6_family = AF_INET6.convert()
        address.sin6_port = toNetworkByteOrder(port)
        failIf(bind(serverSocket, address.ptr.reinterpret(), sizeOf<sockaddr_in6>().convert()) != 0, "Unable to bind port $port")
        serverSocket
    }

    private fun bindIpv4(serverSocket: Int): Int = memScoped {
        failIf(serverSocket < 0, "Unable to create socket")
        setOption(serverSocket, SOL_SOCKET, SO_REUSEADDR, 1)
        val address = alloc<sockaddr_in>()
        memset(address.ptr, 0, sizeOf<sockaddr_in>().convert())
        address.sin_family = AF_INET.convert()
        address.sin_port = toNetworkByteOrder(port)
        failIf(bind(serverSocket, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) != 0, "Unable to bind port $port")
        serverSocket
    }

    private fun setOption(socket: Int, level: Int, option: Int, value: Int) = memScoped {
        val optionValue = alloc<IntVar>().apply { this.value = value }
        setsockopt(socket, level, option, optionValue.ptr, sizeOf<IntVar>().convert())
    }

    private fun acceptConnection(serverSocket: Int) = memScoped {
        val address = alloc<sockaddr_in6>()
        val addressLength = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_in6>().convert() }
        val clientSocket = accept(serverSocket, address.ptr.reinterpret(), addressLength.ptr)
        if (clientSocket >= 0) {
            val clientAddress = formatClientAddress(address)
            activity.connectionStarted()
            startThread(Connection(clientSocket, clientAddress, activity, pacer))
        }
    }

    private fun formatClientAddress(address: sockaddr_in6): String =
        if (address.sin6_family.toInt() == AF_INET) {
            val bytes = address.ptr.reinterpret<sockaddr_in>().pointed.sin_addr.ptr.reinterpret<UByteVar>()
            List(4) { bytes[it].toInt() }.joinToString(".")
        } else {
            val bytes = address.sin6_addr.ptr.reinterpret<UByteVar>()
            formatAddress(List(16) { bytes[it].toInt() })
        }

    private fun startThread(connection: Connection) = memScoped {
        val thread = alloc<pthread_tVar>()
        val reference = StableRef.create(connection)
        pthread_create(thread.ptr, null, staticCFunction(::runConnection), reference.asCPointer())
        pthread_detach(thread.value)
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
