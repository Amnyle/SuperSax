package app.saxpractice.arch

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** The domain package must stay pure Kotlin: no `android.*` imports. */
class ArchitectureTest {
    @Test fun domainHasNoAndroidImports() {
        val root = listOf("src/main/java/app/saxpractice/domain", "app/src/main/java/app/saxpractice/domain")
            .map { File(it) }.firstOrNull { it.isDirectory } ?: fail("domain folder not found from ${File(".").absolutePath}")
        val files = root.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(files.size > 10, "found ${files.size} domain files")
        val offenders = files.filter { f ->
            f.readLines().any { it.trimStart().startsWith("import android.") || it.trimStart().startsWith("import androidx.") }
        }
        assertTrue(offenders.isEmpty(), "android imports in domain: ${offenders.map { it.name }}")
    }
}
