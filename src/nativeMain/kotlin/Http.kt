package srv

data class Request(val method: String, val target: String, val requestLine: String)

sealed interface Body {
    val length: Long?

    class Bytes(val content: ByteArray) : Body {
        override val length: Long = content.size.toLong()
    }

    class File(val path: String, override val length: Long) : Body

    class Process(val process: ChildProcess) : Body {
        override val length: Long? = null
    }

    data object Omitted : Body {
        override val length: Long? = null
    }
}

class Response(
    val status: Int,
    val body: Body,
    val headers: Map<String, String> = emptyMap(),
)

fun parseRequest(head: String): Request? {
    val requestLine = head.substringBefore("\r\n")
    val parts = requestLine.split(' ')
    return if (parts.size == 3) Request(parts[0], parts[1], requestLine) else null
}

fun htmlResponse(status: Int, html: String) =
    Response(status, Body.Bytes(html.encodeToByteArray()), mapOf("Content-Type" to "text/html; charset=utf-8"))

fun errorResponse(status: Int, message: String) =
    htmlResponse(status, "<!DOCTYPE html>\n<html><head><title>Error $status</title></head><body><h1>Error $status</h1><p>${htmlEscape(message)}</p></body></html>\n")

fun redirectResponse(location: String) =
    Response(301, Body.Bytes(ByteArray(0)), mapOf("Location" to location))

fun reasonPhrase(status: Int): String = when (status) {
    200 -> "OK"
    301 -> "Moved Permanently"
    400 -> "Bad Request"
    404 -> "Not Found"
    500 -> "Internal Server Error"
    501 -> "Not Implemented"
    else -> "Unknown"
}
