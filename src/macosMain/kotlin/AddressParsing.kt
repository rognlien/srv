@file:OptIn(ExperimentalForeignApi::class)

package srv

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import srv.spawn.srv_parse_address

internal actual fun parseAddressText(family: Int, text: String, address: COpaquePointer): Int =
    srv_parse_address(family, text, address)
