@file:OptIn(ExperimentalForeignApi::class)

package srv

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.posix.EINTR
import platform.posix.errno
import platform.posix.off_tVar
import platform.posix.sendfile

internal actual fun transferToSocket(descriptor: Int, socket: Int, length: Long): Boolean = memScoped {
    val sentLength = alloc<off_tVar>()
    var offset = 0L
    var failed = false
    while (offset < length && !failed) {
        sentLength.value = length - offset
        val result = sendfile(descriptor, socket, offset, sentLength.ptr, null, 0)
        offset += sentLength.value
        failed = if (result == 0) sentLength.value == 0L else errno != EINTR
    }
    !failed
}
