package app.saxpractice

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import app.saxpractice.ui.nav.AppNav
import app.saxpractice.ui.nav.LocalContainer
import app.saxpractice.ui.nav.LocalSettings
import app.saxpractice.ui.theme.SaxTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as SaxApp).container
    private var appliedLanguage: String = "system"

    override fun attachBaseContext(newBase: Context) {
        val app = newBase.applicationContext as SaxApp
        appliedLanguage = app.container.settings.currentBlocking().language
        super.attachBaseContext(LocaleHelper.wrap(newBase, appliedLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initial = container.settings.currentBlocking()
        val startSession = intent?.getBooleanExtra(EXTRA_START_SESSION, false) == true && savedInstanceState == null
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initial)
            // Switching language recreates the activity so every string changes.
            LaunchedEffect(settings.language) { if (settings.language != appliedLanguage) recreate() }
            CompositionLocalProvider(LocalContainer provides container, LocalSettings provides settings) {
                SaxTheme(settings.theme) {
                    AppNav(rememberNavController(), startOnboarding = !initial.onboardingDone, startSession = startSession, onRecreate = { recreate() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_START_SESSION = "start_session"
    }
}
