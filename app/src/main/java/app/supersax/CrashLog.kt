package app.supersax

import android.content.Context
import java.io.File

/** Keeps the last crash (stack trace, version, device) so it can be shown and copied on the next launch. */
object CrashLog {
    private fun file(c: Context) = File(c.filesDir, "last_crash.txt")

    fun install(app: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
                file(app).writeText(buildString {
                    append("Super Sax ").append(version).append(" · Android ").append(android.os.Build.VERSION.RELEASE)
                    append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(") · ").append(android.os.Build.MODEL).append('\n')
                    append("Thread: ").append(thread.name).append('\n')
                    append(e.stackTraceToString().take(12_000))
                })
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** The report of the last crash, if one was not yet shown. */
    fun pending(c: Context): String? = file(c).takeIf { it.exists() }?.readText()

    fun clear(c: Context) { file(c).delete() }
}
