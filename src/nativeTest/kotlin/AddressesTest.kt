package srv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddressesTest {

    @Test
    fun formatsIpv4MappedAddressesAsIpv4() {
        assertEquals("192.168.1.2", formatAddress(List(10) { 0 } + listOf(0xFF, 0xFF, 192, 168, 1, 2)))
    }

    @Test
    fun compressesTheLongestRunOfZeroGroups() {
        assertEquals("::1", formatAddress(List(15) { 0 } + 1))
        assertEquals("::", formatAddress(List(16) { 0 }))
        assertEquals("2001:db8::1:0:0:1", formatAddress(ipv6(0x2001, 0xdb8, 0, 0, 1, 0, 0, 1)))
    }

    @Test
    fun leavesSingleZeroGroupsAlone() {
        assertEquals("2001:db8:0:1:1:1:1:1", formatAddress(ipv6(0x2001, 0xdb8, 0, 1, 1, 1, 1, 1)))
    }

    @Test
    fun parsesIpv4AndIpv6Addresses() {
        assertEquals(BindAddress("192.168.1.2", listOf(192, 168, 1, 2)), parseBindAddress("192.168.1.2"))
        assertEquals(BindAddress("::1", List(15) { 0 } + 1), parseBindAddress("::1"))
    }

    @Test
    fun rejectsAnythingButAnAddress() {
        listOf("", "localhost", "256.0.0.1", "1.2.3", "::g", "[::1]").forEach { text ->
            assertNull(parseBindAddress(text), "'$text'")
        }
    }

    @Test
    fun showsLocalAndWildcardAddressesAsLocalhost() {
        assertEquals("localhost", parseBindAddress("127.0.0.1")?.urlHost)
        assertEquals("localhost", parseBindAddress("0.0.0.0")?.urlHost)
        assertEquals("localhost", parseBindAddress("::")?.urlHost)
        assertEquals("[::1]", parseBindAddress("::1")?.urlHost)
        assertEquals("192.168.1.2", parseBindAddress("192.168.1.2")?.urlHost)
    }

    private fun ipv6(vararg groups: Int): List<Int> = groups.flatMap { listOf(it shr 8, it and 0xFF) }
}
