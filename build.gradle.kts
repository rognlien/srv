import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform") version "2.2.20"
}

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val nativeTargets: List<KotlinNativeTarget> = when {
        hostOs == "Mac OS X" -> listOf(macosArm64(), macosX64())
        hostOs == "Linux" && isArm64 -> listOf(linuxArm64())
        hostOs == "Linux" -> listOf(linuxX64())
        else -> throw GradleException("Host OS is not supported.")
    }

    nativeTargets.forEach { target ->
        target.binaries {
            executable {
                entryPoint = "srv.main"
                baseName = "srv"
            }
        }
    }
}

val generateBuildInfo by tasks.registering {
    val outputDirectory = layout.buildDirectory.dir("generated/buildInfo")
    val versionText = project.version.toString()
    inputs.property("version", versionText)
    outputs.dir(outputDirectory)
    doLast {
        val file = outputDirectory.get().file("srv/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package srv\n\nconst val VERSION = \"$versionText\"\n")
    }
}

kotlin.sourceSets.matching { it.name == "nativeMain" }.configureEach {
    kotlin.srcDir(generateBuildInfo)
}

val generateMimeTypes by tasks.registering {
    val sourceFile = layout.projectDirectory.file("data/mime.types")
    val outputDirectory = layout.buildDirectory.dir("generated/mimeTypes")
    inputs.file(sourceFile)
    outputs.dir(outputDirectory)
    doLast {
        val table = sourceFile.asFile.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .joinToString("\n")
        val file = outputDirectory.get().file("srv/MimeTypeTable.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package srv\n\ninternal const val MIME_TYPES = \"\"\"\n$table\n\"\"\"\n")
    }
}

kotlin.sourceSets.matching { it.name == "nativeMain" }.configureEach {
    kotlin.srcDir(generateMimeTypes)
}
