@file:OptIn(ExperimentalForeignApi::class)

package srv

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.osx._NSGetEnviron
import platform.posix.SIGPIPE
import platform.posix.STDERR_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.accept
import platform.posix.pid_tVar
import platform.posix.pipe
import platform.posix.sigset_tVar
import platform.posix.socklen_tVar
import srv.spawn.POSIX_SPAWN_CLOEXEC_DEFAULT
import srv.spawn.POSIX_SPAWN_SETSIGDEF
import srv.spawn.posix_spawn_file_actions_adddup2
import srv.spawn.srv_add_null_input
import srv.spawn.posix_spawn_file_actions_addinherit_np
import srv.spawn.posix_spawn_file_actions_destroy
import srv.spawn.posix_spawn_file_actions_init
import srv.spawn.posix_spawn_file_actions_tVar
import srv.spawn.posix_spawnattr_destroy
import srv.spawn.posix_spawnattr_init
import srv.spawn.posix_spawnattr_setflags
import srv.spawn.posix_spawnattr_setsigdefault
import srv.spawn.posix_spawnattr_tVar
import srv.spawn.posix_spawn

internal actual fun acceptClient(serverSocket: Int, address: COpaquePointer, length: CPointer<socklen_tVar>): Int =
    accept(serverSocket, address.reinterpret(), length)

internal actual fun createPipe(descriptors: CPointer<IntVar>): Int = pipe(descriptors)

internal actual fun currentEnvironment(): List<String> = readCStringArray(_NSGetEnviron()?.pointed?.value)

internal actual fun spawnProcess(
    executable: String,
    arguments: List<String>,
    environment: List<String>,
    outputDescriptor: Int,
): SpawnResult =
    memScoped {
        val actions = alloc<posix_spawn_file_actions_tVar>()
        val attributes = alloc<posix_spawnattr_tVar>()
        val processId = alloc<pid_tVar>()
        val defaultSignals = alloc<sigset_tVar>().apply { value = 1u shl (SIGPIPE - 1) }
        posix_spawn_file_actions_init(actions.ptr)
        srv_add_null_input(actions.ptr)
        posix_spawn_file_actions_adddup2(actions.ptr, outputDescriptor, STDOUT_FILENO)
        posix_spawn_file_actions_addinherit_np(actions.ptr, STDERR_FILENO)
        posix_spawnattr_init(attributes.ptr)
        posix_spawnattr_setsigdefault(attributes.ptr, defaultSignals.ptr)
        posix_spawnattr_setflags(attributes.ptr, (POSIX_SPAWN_CLOEXEC_DEFAULT or POSIX_SPAWN_SETSIGDEF).convert())
        val error = posix_spawn(
            processId.ptr, executable, actions.ptr, attributes.ptr,
            cStringArray(arguments), cStringArray(environment),
        )
        posix_spawn_file_actions_destroy(actions.ptr)
        posix_spawnattr_destroy(attributes.ptr)
        SpawnResult(processId.value, error)
    }
