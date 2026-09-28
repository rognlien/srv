@file:OptIn(ExperimentalForeignApi::class)

package srv

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.linux.sendfile
import platform.posix.EAGAIN
import platform.posix.EINTR
import platform.posix.errno
import platform.posix.off_tVar

internal actual fun transferToSocket(descriptor: Int, socket: Int, length: Long): Boolean = memScoped {
    val offset = alloc<off_tVar>().apply { value = 0 }
    var failed = false
    while (offset.value < length && !failed) {
        val sent = sendfile(socket, descriptor, offset.ptr, (length - offset.value).convert())
        failed = sent == 0L || (sent < 0 && errno != EINTR && errno != EAGAIN)
    }
    !failed
}
