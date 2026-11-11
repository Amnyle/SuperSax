package app.supersax.data.assets

import android.content.res.AssetManager
import app.supersax.domain.common.ExerciseCatalog
import app.supersax.domain.generator.GeneratorConfig
import app.supersax.domain.program.Program
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Loads content data files from `assets/`: configuration, exercise descriptions, prebuilt programs,
 * rank thresholds, pieces, lessons and fingerings. Everything is read once and cached.
 */
open class ContentRepository(private val assets: AssetManager) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    protected fun text(path: String): String = assets.open(path).bufferedReader().use { it.readText() }
    protected fun <T> load(path: String, ser: KSerializer<T>): T = json.decodeFromString(ser, text(path))
    protected fun list(dir: String): List<String> = (assets.list(dir) ?: emptyArray()).filter { it.endsWith(".json") }.sorted()

    val generatorConfig: GeneratorConfig by lazy { load("config/generator.json", GeneratorConfig.serializer()) }
    val exercises: ExerciseCatalog by lazy { load("exercises/exercises.json", ExerciseCatalog.serializer()) }
    val bundledPrograms: List<Program> by lazy { list("programs").map { load("programs/$it", Program.serializer()) } }
}
