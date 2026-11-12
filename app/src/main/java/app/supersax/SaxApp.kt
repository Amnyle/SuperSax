package app.supersax

import android.app.Application
import kotlinx.coroutines.launch

class SaxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        container = AppContainer(this)
        container.appScope.launch { container.sessions.restore() }
        container.appScope.launch { app.supersax.update.Updater.schedule(this@SaxApp, container.settings.current().updateCheck) }
        runCatching { app.supersax.work.AutoBackup.schedule(this) }
        // Unfinished AI separations carry on (WorkManager also resumes them by itself).
        container.appScope.launch { if (container.separationQueue.all().any { it.active }) runCatching { app.supersax.separation.SeparationQueue.kick(this@SaxApp) } }
    }
}
