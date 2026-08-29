package com.example.a2uicomposelabs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The API key must reach the build from `local.properties` or the environment, and from
 * nowhere else. If anyone ever pastes one into a source file, config file, or demo asset,
 * this fails — before it can be committed.
 */
class NoHardcodedSecretsTest {

    /** Google API keys, and the usual ways a secret gets left in a file. */
    private val secretPatterns = listOf(
        Regex("""AIza[0-9A-Za-z_\-]{30,}"""),
        Regex("""(?i)(api[_-]?key|apikey|secret|token)\s*[:=]\s*["'][A-Za-z0-9_\-]{20,}["']"""),
    )

    private val scannedExtensions = setOf("kt", "kts", "java", "json", "jsonl", "xml", "properties")

    @Test
    fun `no api key literal appears anywhere in the project sources`() {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").exists() }
            ?: error("could not locate the Gradle root")

        val offenders = root.walkTopDown()
            .onEnter { dir -> dir.name !in setOf("build", ".gradle", ".git", ".idea") }
            .filter { it.isFile && it.extension in scannedExtensions }
            // local.properties is gitignored and is exactly where the key is supposed to live.
            .filterNot { it.name == "local.properties" }
            .filter { file ->
                val text = file.readText()
                secretPatterns.any { it.containsMatchIn(text) }
            }
            .map { it.relativeTo(root).path }
            .toList()

        assertEquals("secret-shaped literal found in tracked files", emptyList<String>(), offenders)
    }
}
