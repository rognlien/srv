package srv

import kotlin.test.Test
import kotlin.test.assertEquals

class EncodingTest {

    @Test
    fun decodesPercentEncodedBytes() {
        assertEquals("a b/æ", percentDecode("a%20b%2F%C3%A6"))
    }

    @Test
    fun keepsInvalidEscapesAsText() {
        assertEquals("100%", percentDecode("100%"))
        assertEquals("%zz", percentDecode("%zz"))
        assertEquals("%4", percentDecode("%4"))
    }

    @Test
    fun requiresTwoHexDigits() {
        assertEquals("%-0", percentDecode("%-0"))
        assertEquals("%+F", percentDecode("%+F"))
    }

    @Test
    fun decodesNulSoCallersCanRejectIt() {
        assertEquals("a\u0000b", percentDecode("a%00b"))
    }

    @Test
    fun encodesEverythingButUnreservedCharacters() {
        assertEquals("a%20b%2F%C3%A6-._~", percentEncode("a b/æ-._~"))
    }

    @Test
    fun escapesHtml() {
        assertEquals("&lt;a href=&quot;x&quot;&gt;&amp;&#x27;", htmlEscape("<a href=\"x\">&'"))
    }
}
