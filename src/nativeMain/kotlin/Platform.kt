@file:OptIn(ExperimentalForeignApi::class)

package srv

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import platform.posix.socklen_tVar

class SpawnResult(val processId: Int, val error: Int)

internal expect fun acceptClient(serverSocket: Int, address: COpaquePointer, length: CPointer<socklen_tVar>): Int

internal expect fun createPipe(descriptors: CPointer<IntVar>): Int

internal expect fun spawnProcess(
    executable: String,
    arguments: List<String>,
    environment: List<String>,
    outputDescriptor: Int,
): SpawnResult

internal expect fun currentEnvironment(): List<String>

internal expect fun parseAddressText(family: Int, text: String, address: COpaquePointer): Int

internal fun MemScope.cStringArray(values: List<String>): CPointer<CPointerVar<ByteVar>> {
    val array = allocArray<CPointerVar<ByteVar>>(values.size + 1)
    values.forEachIndexed { index, value -> array[index] = value.cstr.getPointer(this) }
    array[values.size] = null
    return array
}

internal fun readCStringArray(array: CPointer<CPointerVar<ByteVar>>?): List<String> =
    if (array == null) {
        emptyList()
    } else {
        generateSequence(0) { it + 1 }
            .map { array[it] }
            .takeWhile { it != null }
            .map { it!!.toKString() }
            .toList()
    }
