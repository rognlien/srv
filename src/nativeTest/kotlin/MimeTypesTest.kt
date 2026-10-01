package srv

import kotlin.test.Test
import kotlin.test.assertEquals

class MimeTypesTest {

    @Test
    fun addsCharsetToTextualTypes() {
        assertEquals("text/html; charset=utf-8", contentType("./index.html"))
        assertEquals("application/json; charset=utf-8", contentType("./data.JSON"))
        assertEquals("text/markdown; charset=utf-8", contentType("./README.md"))
    }

    @Test
    fun leavesBinaryTypesAlone() {
        assertEquals("image/png", contentType("./logo.png"))
    }

    @Test
    fun fallsBackToOctetStream() {
        assertEquals("application/octet-stream", contentType("./Makefile"))
        assertEquals("application/octet-stream", contentType("./archive.unknown"))
        assertEquals("application/octet-stream", contentType("./dir.d/file"))
    }
}
