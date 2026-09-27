package srv

fun directoryListing(directoryPath: String, displayPath: String): String {
    val title = htmlEscape("Directory listing for $displayPath")
    val items = listDirectory(directoryPath).joinToString("\n") { name -> listItem(directoryPath, name) }
    return """
        |<!DOCTYPE HTML>
        |<html lang="en">
        |<head>
        |<meta charset="utf-8">
        |<title>$title</title>
        |</head>
        |<body>
        |<h1>$title</h1>
        |<hr>
        |<ul>
        |$items
        |</ul>
        |<hr>
        |</body>
        |</html>
        |""".trimMargin()
}

private fun listItem(directoryPath: String, name: String): String {
    val suffix = if (fileInfo("$directoryPath/$name")?.isDirectory == true) "/" else ""
    return "<li><a href=\"${percentEncode(name)}$suffix\">${htmlEscape(name)}$suffix</a></li>"
}
