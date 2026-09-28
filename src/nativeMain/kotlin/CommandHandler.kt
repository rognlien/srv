package srv

class CommandHandler(private val program: Source.Program) {
    private val headers = mapOf("Content-Type" to program.contentType)

    fun handle(request: Request, clientAddress: String): Response = when (request.method) {
        "GET" -> run(request, clientAddress)
        "HEAD" -> Response(200, Body.Omitted, headers)
        else -> errorResponse(501, "Unsupported method (${request.method})")
    }

    private fun run(request: Request, clientAddress: String): Response =
        try {
            val process = ChildProcess.start(program.executable, program.arguments, environment(request, clientAddress))
            Response(200, Body.Process(process), headers)
        } catch (exception: ProcessException) {
            errorResponse(500, "Unable to run ${program.arguments[0]}: ${exception.message}")
        }

    private fun environment(request: Request, clientAddress: String): List<String> {
        val path = request.target.substringBefore('?').substringBefore('#')
        val query = request.target.substringAfter('?', "").substringBefore('#')
        return currentEnvironment().filterNot { it.startsWith("SRV_") } + listOf(
            "SRV_METHOD=${request.method}",
            "SRV_PATH=${percentDecode(path)}",
            "SRV_QUERY=$query",
            "SRV_CLIENT=$clientAddress",
        )
    }
}
