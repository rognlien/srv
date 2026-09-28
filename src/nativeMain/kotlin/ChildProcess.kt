@file:OptIn(ExperimentalForeignApi::class)

package srv

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.SIGKILL
import platform.posix.SIGTERM
import platform.posix.WNOHANG
import platform.posix.close
import platform.posix.errno
import platform.posix.kill
import platform.posix.strerror
import platform.posix.usleep
import platform.posix.waitpid

private const val TERMINATION_POLLS = 20
private const val TERMINATION_POLL_MICROSECONDS = 100_000u

class ProcessException(error: Int) : Exception(strerror(error)?.toKString())

class ChildProcess private constructor(private val processId: Int, val output: Int) {

    fun finish(terminate: Boolean): Int {
        close(output)
        val status = if (terminate) terminate() else null
        return status ?: waitForExit()
    }

    private fun terminate(): Int? {
        kill(processId, SIGTERM)
        var status = pollExitStatus()
        var polls = 0
        while (status == null && polls < TERMINATION_POLLS) {
            usleep(TERMINATION_POLL_MICROSECONDS)
            status = pollExitStatus()
            polls++
        }
        if (status == null) {
            kill(processId, SIGKILL)
        }
        return status
    }

    private fun pollExitStatus(): Int? = memScoped {
        val status = alloc<IntVar>()
        if (waitpid(processId, status.ptr, WNOHANG) == processId) decodeStatus(status.value) else null
    }

    private fun waitForExit(): Int = memScoped {
        val status = alloc<IntVar>()
        waitpid(processId, status.ptr, 0)
        decodeStatus(status.value)
    }

    private fun decodeStatus(status: Int): Int {
        val signal = status and 0x7F
        return if (signal == 0) (status shr 8) and 0xFF else 128 + signal
    }

    companion object {
        fun start(arguments: List<String>, environment: List<String>): ChildProcess = memScoped {
            val descriptors = allocArray<IntVar>(2)
            if (createPipe(descriptors) != 0) {
                throw ProcessException(errno)
            }
            val result = spawnProcess(arguments, environment, descriptors[1])
            close(descriptors[1])
            if (result.error != 0) {
                close(descriptors[0])
                throw ProcessException(result.error)
            }
            ChildProcess(result.processId, descriptors[0])
        }
    }
}
