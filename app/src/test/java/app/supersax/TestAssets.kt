package app.supersax

import app.supersax.domain.generator.GeneratorConfig
import kotlinx.serialization.json.Json

/** Reads the real asset files (src/main/assets is on the unit-test classpath). */
object TestAssets {
    val json = Json { ignoreUnknownKeys = true }

    fun text(path: String): String =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream(path)) { "missing asset $path" }
            .bufferedReader().readText()

    fun list(dir: String): List<String> {
        val url = requireNotNull(javaClass.classLoader!!.getResource(dir)) { "missing dir $dir" }
        return java.io.File(url.toURI()).listFiles()!!.map { it.name }.sorted()
    }

    val generatorConfig: GeneratorConfig by lazy { json.decodeFromString(GeneratorConfig.serializer(), text("config/generator.json")) }
}
