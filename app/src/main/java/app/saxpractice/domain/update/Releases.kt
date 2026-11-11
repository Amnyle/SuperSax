package app.saxpractice.domain.update

import kotlinx.serialization.Serializable

/** The parts of a GitHub release the updater needs (API: GET /repos/{repo}/releases/latest). */
@Serializable
data class GhAsset(val name: String, val browser_download_url: String, val size: Long = 0)

@Serializable
data class GhRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val html_url: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

/** A newer version that can be installed. */
data class AvailableUpdate(val version: String, val notes: String, val apk: GhAsset, val page: String?)

/** Pure rules of the in-app updater. */
object Releases {
    /** "v1.0.12" → [1, 0, 12]; non-digit suffixes are ignored. */
    fun parts(v: String): List<Int> = v.trim().removePrefix("v").removePrefix("V")
        .split('.', '-', '+').map { p -> p.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    /** Numeric comparison part by part: 1.0.10 is newer than 1.0.9. */
    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** The release APK (never a debug build). */
    fun pickApk(assets: List<GhAsset>): GhAsset? =
        assets.filter { it.name.endsWith(".apk") && !it.name.contains("debug", ignoreCase = true) }.maxByOrNull { it.size }

    /** The update to offer, or null when the release is a draft, a pre-release, has no APK, or is not newer. */
    fun available(release: GhRelease, installedVersion: String): AvailableUpdate? {
        if (release.draft || release.prerelease) return null
        val apk = pickApk(release.assets) ?: return null
        val version = release.tag_name.trim().removePrefix("v").removePrefix("V")
        if (!isNewer(version, installedVersion)) return null
        return AvailableUpdate(version, release.body.orEmpty().trim(), apk, release.html_url)
    }
}
