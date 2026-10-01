package srv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpTest {

    @Test
    fun parsesTheRequestLine() {
        assertEquals(
            Request("GET", "/docs/?a=1", "GET /docs/?a=1 HTTP/1.1"),
            parseRequest("GET /docs/?a=1 HTTP/1.1\r\nHost: localhost\r\n\r\n"),
        )
    }

    @Test
    fun rejectsRequestLinesWithoutThreeParts() {
        assertNull(parseRequest("GET /\r\n\r\n"))
        assertNull(parseRequest("GET / HTTP/1.1 extra\r\n\r\n"))
    }

    @Test
    fun rejectsTargetsThatAreNotPaths() {
        assertNull(parseRequest("OPTIONS * HTTP/1.1\r\n\r\n"))
        assertNull(parseRequest("GET http://example.com/ HTTP/1.1\r\n\r\n"))
    }

    @Test
    fun rejectsControlCharacters() {
        assertNull(parseRequest("GET /docs?\nX-Injected:yes HTTP/1.1\r\n\r\n"))
        assertNull(parseRequest("GET /\u001b]0;title\u0007 HTTP/1.1\r\n\r\n"))
    }
}
