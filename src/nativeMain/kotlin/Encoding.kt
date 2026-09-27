package srv

private const val UNRESERVED_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

fun percentDecode(value: String): String {
    val source = value.encodeToByteArray()
    val output = ArrayList<Byte>(source.size)
    var index = 0
    while (index < source.size) {
        val encoded = if (source[index] == '%'.code.toByte() && index + 2 < source.size) {
            source.decodeToString(index + 1, index + 3).toIntOrNull(16)
        } else {
            null
        }
        output += encoded?.toByte() ?: source[index]
        index += if (encoded != null) 3 else 1
    }
    return output.toByteArray().decodeToString()
}

fun percentEncode(value: String): String =
    value.encodeToByteArray().joinToString("") { byte ->
        val code = byte.toInt() and 0xFF
        val character = code.toChar()
        if (character in UNRESERVED_CHARACTERS) character.toString() else "%" + code.toString(16).uppercase().padStart(2, '0')
    }

fun htmlEscape(value: String): String = buildString {
    value.forEach { character ->
        append(
            when (character) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&#x27;"
                else -> character
            }
        )
    }
}
