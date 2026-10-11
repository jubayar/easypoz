package net.redngreen.easypos

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.relativeTo
import kotlin.streams.toList
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architecture guard (restructure design D10). The clean-architecture
 * boundaries are package-level (two Gradle modules by design), so the
 * compiler cannot enforce them — this test does:
 *
 * 1. `:core` never imports Compose (UI lives in `:app` only).
 * 2. A feature never imports another feature's `ui`, `data`, or `framework`
 *    package — cross-feature imports target `domain` only.
 * 3. `feature/<name>/domain` stays pure Kotlin (no Android framework imports).
 */
class ArchitectureGuardTest {

    private val appRoot: Path = Paths.get(System.getProperty("user.dir"))
    private val featureRoot: Path =
        appRoot.resolve("src/main/java/net/redngreen/easypos/feature")
    private val coreRoot: Path =
        appRoot.parent.resolve("core/src/main/java")

    private fun featureDirs(): List<Path> =
        if (Files.isDirectory(featureRoot)) {
            Files.list(featureRoot).use { stream ->
                stream.filter { Files.isDirectory(it) }.toList()
            }
        } else {
            emptyList()
        }

    private fun walkKt(root: Path): List<Path> =
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.extension == "kt" }.toList()
        }

    private fun importLines(file: Path): List<String> =
        Files.readAllLines(file)
            .map { it.trim() }
            .filter { it.startsWith("import ") }

    /** Returns (feature, layer) for an import of net.redngreen.easypos.feature.<f>.<layer>.* */
    private fun featureImport(import: String): Pair<String, String>? {
        val marker = "net.redngreen.easypos.feature."
        val idx = import.indexOf(marker)
        if (idx < 0) return null
        val rest = import.substring(idx + marker.length)
        val parts = rest.split(".")
        if (parts.size < 2) return null
        return parts[0] to parts[1]
    }

    @Test
    fun `core module has no Compose imports`() {
        if (!Files.isDirectory(coreRoot)) return
        val offenders = walkKt(coreRoot)
            .flatMap { file ->
                importLines(file)
                    .filter { it.contains("androidx.compose") }
                    .map { "${file.relativeTo(coreRoot.parent)} -> $it" }
            }
        assertTrue("Compose leaked into :core:\n$offenders", offenders.isEmpty())
    }

    @Test
    fun `features import other features only through domain`() {
        val features = featureDirs()
        if (features.isEmpty()) return
        val offenders = features.flatMap { feature ->
            val name = feature.fileName.toString()
            walkKt(feature)
                .flatMap { importLines(it) }
                .mapNotNull { featureImport(it) }
                .filter { (other, layer) -> other != name && layer != "domain" }
                .map { (other, layer) -> "$name imports $other.$layer (only domain allowed)" }
        }
        assertTrue("Cross-feature boundary violations:\n$offenders", offenders.isEmpty())
    }

    @Test
    fun `feature domain packages are pure Kotlin`() {
        val features = featureDirs()
        if (features.isEmpty()) return
        val offenders = features.flatMap { feature ->
            val domain = feature.resolve("domain")
            if (!Files.isDirectory(domain)) return@flatMap emptyList<String>()
            walkKt(domain)
                .flatMap { importLines(it) }
                .filter { it.startsWith("import android.") || it.startsWith("import androidx.") }
                .map { "${feature.fileName}/domain -> $it" }
        }
        assertTrue("Android imports in feature domain:\n$offenders", offenders.isEmpty())
    }

    @Test
    fun `features declare all four layers`() {
        val features = featureDirs()
        if (features.isEmpty()) return
        val offenders = features
            .filterNot { feature ->
                listOf("ui", "domain", "data", "framework").all {
                    Files.isDirectory(feature.resolve(it))
                }
            }
            .map { it.fileName.toString() }
        assertTrue("Features missing layer packages:\n$offenders", offenders.isEmpty())
    }
}
