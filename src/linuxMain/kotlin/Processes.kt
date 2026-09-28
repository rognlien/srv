@file:OptIn(ExperimentalForeignApi::class)

package srv

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.linux.POSIX_SPAWN_SETSIGDEF
import platform.linux.posix_spawn_file_actions_adddup2
import platform.linux.posix_spawn_file_actions_destroy
import platform.linux.posix_spawn_file_actions_init
import platform.linux.posix_spawn_file_actions_t
import platform.linux.posix_spawnattr_destroy
import platform.linux.posix_spawnattr_init
import platform.linux.posix_spawnattr_setflags
import platform.linux.posix_spawnattr_setsigdefault
import platform.linux.posix_spawnattr_t
import platform.linux.posix_spawn
import platform.posix.SIGPIPE
import platform.posix.STDOUT_FILENO
import platform.posix.pid_tVar
import platform.posix.sigaddset
import platform.posix.sigemptyset
import platform.posix.sigset_t
import platform.posix.socklen_tVar
import srv.linux.srv_accept_cloexec
import srv.linux.srv_add_null_input
import srv.linux.srv_environment
import srv.linux.srv_pipe_cloexec

internal actual fun acceptClient(serverSocket: Int, address: COpaquePointer, length: CPointer<socklen_tVar>): Int =
    srv_accept_cloexec(serverSocket, address, length)

internal actual fun createPipe(descriptors: CPointer<IntVar>): Int = srv_pipe_cloexec(descriptors)

internal actual fun currentEnvironment(): List<String> = readCStringArray(srv_environment())

internal actual fun spawnProcess(
    executable: String,
    arguments: List<String>,
    environment: List<String>,
    outputDescriptor: Int,
): SpawnResult =
    memScoped {
        val actions = alloc<posix_spawn_file_actions_t>()
        val attributes = alloc<posix_spawnattr_t>()
        val processId = alloc<pid_tVar>()
        val defaultSignals = alloc<sigset_t>()
        sigemptyset(defaultSignals.ptr)
        sigaddset(defaultSignals.ptr, SIGPIPE)
        posix_spawn_file_actions_init(actions.ptr)
        srv_add_null_input(actions.ptr)
        posix_spawn_file_actions_adddup2(actions.ptr, outputDescriptor, STDOUT_FILENO)
        posix_spawnattr_init(attributes.ptr)
        posix_spawnattr_setsigdefault(attributes.ptr, defaultSignals.ptr)
        posix_spawnattr_setflags(attributes.ptr, POSIX_SPAWN_SETSIGDEF.convert())
        val error = posix_spawn(
            processId.ptr, executable, actions.ptr, attributes.ptr,
            cStringArray(arguments), cStringArray(environment),
        )
        posix_spawn_file_actions_destroy(actions.ptr)
        posix_spawnattr_destroy(attributes.ptr)
        SpawnResult(processId.value, error)
    }
