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

    /** The shipped generator config (technique only: breathing, long tones, scales). */
    val generatorConfig: GeneratorConfig by lazy { json.decodeFromString(GeneratorConfig.serializer(), text("config/generator.json")) }

    /**
     * The spec's original split table (section 6, with song blocks), used to test the generator mechanics
     * (interpolation, drop order, focus and limiter shifts) independently of the shipped numbers.
     */
    val specGeneratorConfig: GeneratorConfig by lazy {
        generatorConfig.copy(
            splits = listOf(
                app.supersax.domain.generator.SplitRow(10, 3, 4, 0, 3),
                app.supersax.domain.generator.SplitRow(20, 4, 6, 5, 5),
                app.supersax.domain.generator.SplitRow(30, 5, 8, 9, 8),
            ),
            dropOrder = listOf(app.supersax.domain.program.BlockKind.NEW_MATERIAL, app.supersax.domain.program.BlockKind.SCALES,
                app.supersax.domain.program.BlockKind.LONG_TONES),
        )
    }
}
