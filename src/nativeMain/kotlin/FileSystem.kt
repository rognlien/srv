@file:OptIn(ExperimentalForeignApi::class)

package srv

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.value
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.EAGAIN
import platform.posix.EINTR
import platform.posix.O_RDONLY
import platform.posix.R_OK
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.access
import platform.posix.close
import platform.posix.closedir
import platform.posix.errno
import platform.posix.off_tVar
import platform.posix.open
import platform.posix.opendir
import platform.posix.read
import platform.posix.readdir
import platform.posix.sendfile
import platform.posix.stat

private const val CHUNK_SIZE = 64 * 1024

data class FileInfo(val isDirectory: Boolean, val size: Long)

fun fileInfo(path: String): FileInfo? = memScoped {
    val status = alloc<stat>()
    if (stat(path, status.ptr) == 0) {
        FileInfo((status.st_mode.toInt() and S_IFMT.toInt()) == S_IFDIR.toInt(), status.st_size)
    } else {
        null
    }
}

fun isReadable(path: String): Boolean = access(path, R_OK) == 0

fun listDirectory(path: String): List<String> {
    val names = mutableListOf<String>()
    val directory = opendir(path)
    if (directory != null) {
        var entry = readdir(directory)
        while (entry != null) {
            names += entry.pointed.d_name.toKString()
            entry = readdir(directory)
        }
        closedir(directory)
    }
    return names.filter { it != "." && it != ".." }.sortedBy { it.lowercase() }
}

fun streamFile(path: String, consumer: (ByteArray, Int) -> Boolean) {
    val descriptor = open(path, O_RDONLY)
    if (descriptor >= 0) {
        val buffer = ByteArray(CHUNK_SIZE)
        var count = readChunk(descriptor, buffer)
        while (count > 0 && consumer(buffer, count)) {
            count = readChunk(descriptor, buffer)
        }
        close(descriptor)
    }
}

private fun readChunk(descriptor: Int, buffer: ByteArray): Int =
    buffer.usePinned { read(descriptor, it.addressOf(0), buffer.size.convert()).toInt() }

fun sendFile(path: String, socket: Int): Boolean {
    val descriptor = open(path, O_RDONLY)
    val isSent = descriptor >= 0 && transferToSocket(descriptor, socket)
    if (descriptor >= 0) {
        close(descriptor)
    }
    return isSent
}

private fun transferToSocket(descriptor: Int, socket: Int): Boolean = memScoped {
    val sentLength = alloc<off_tVar>()
    var offset = 0L
    var result: Int
    do {
        sentLength.value = 0
        result = sendfile(descriptor, socket, offset, sentLength.ptr, null, 0)
        offset += sentLength.value
    } while (result != 0 && (errno == EINTR || errno == EAGAIN))
    result == 0
}
