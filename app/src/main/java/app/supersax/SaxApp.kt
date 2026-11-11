package app.supersax

import android.app.Application
import kotlinx.coroutines.launch

class SaxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.sessions.restore() }
        container.appScope.launch { app.supersax.update.Updater.schedule(this@SaxApp, container.settings.current().updateCheck) }
    }
}
