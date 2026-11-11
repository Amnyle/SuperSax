package app.supersax.domain.update

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

