package srv

private const val DEFAULT_MIME_TYPE = "application/octet-stream"
private const val UTF_8 = "; charset=utf-8"

private val overrides = mapOf(
    "md" to "text/markdown",
    "markdown" to "text/markdown",
    "map" to "application/json",
)

private val textualTypes = setOf(
    "application/javascript",
    "application/json",
    "application/xml",
    "image/svg+xml",
)

private val mimeTypes: Map<String, String> by lazy { parseMimeTypes(MIME_TYPES) + overrides }

fun contentType(path: String): String {
    val mimeType = mimeTypes[extension(path)] ?: DEFAULT_MIME_TYPE
    return if (isTextual(mimeType)) mimeType + UTF_8 else mimeType
}

private fun extension(path: String): String {
    val name = path.substringAfterLast('/')
    return if ('.' in name) name.substringAfterLast('.').lowercase() else ""
}

private fun isTextual(mimeType: String): Boolean =
    mimeType.startsWith("text/") || mimeType in textualTypes

private fun parseMimeTypes(table: String): Map<String, String> =
    table.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line -> line.split(' ', '\t').filter { it.isNotEmpty() } }
        .flatMap { fields -> fields.drop(1).map { extension -> extension to fields[0] } }
        .toMap()
