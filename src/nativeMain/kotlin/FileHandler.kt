package srv

private const val INDEX_FILE = "index.html"

object FileHandler {

    fun handle(request: Request): Response = when (request.method) {
        "GET", "HEAD" -> serve(request.target)
        else -> errorResponse(501, "Unsupported method (${request.method})")
    }

    private fun serve(target: String): Response {
        val urlPath = target.substringBefore('?').substringBefore('#')
        val filePath = toFilePath(urlPath)
        val info = fileInfo(filePath)
        return when {
            '\u0000' in percentDecode(urlPath) -> errorResponse(400, "Bad request")
            info == null || !(info.isDirectory || info.isRegularFile) -> errorResponse(404, "File not found")
            info.isDirectory && !urlPath.endsWith("/") -> redirectResponse("/${urlPath.trimStart('/')}/${target.substring(urlPath.length)}")
            info.isDirectory -> serveDirectory(filePath, urlPath)
            else -> serveFile(filePath, info)
        }
    }

    private fun serveDirectory(directoryPath: String, urlPath: String): Response {
        val indexPath = "$directoryPath/$INDEX_FILE"
        val indexInfo = fileInfo(indexPath)
        return if (indexInfo?.isRegularFile == true) {
            serveFile(indexPath, indexInfo)
        } else {
            htmlResponse(200, directoryListing(directoryPath, percentDecode(urlPath)))
        }
    }

    private fun serveFile(path: String, info: FileInfo): Response =
        if (isReadable(path)) {
            Response(200, Body.File(path, info.size), mapOf("Content-Type" to contentType(path)))
        } else {
            errorResponse(404, "File not found")
        }

    private fun toFilePath(urlPath: String): String {
        val segments = percentDecode(urlPath)
            .split('/')
            .filter { it.isNotEmpty() && it != "." && it != ".." }
        return (listOf(".") + segments).joinToString("/")
    }
}
