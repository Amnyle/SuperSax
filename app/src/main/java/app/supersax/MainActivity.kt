package app.supersax

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import app.supersax.ui.nav.AppNav
import app.supersax.ui.nav.LocalContainer
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.theme.SaxTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as SaxApp).container
    private var appliedLanguage: String = "system"
    /** Incremented each time the widget asks to start today's session. */
    private val startSignal = mutableIntStateOf(0)
    /** Incremented when the update notification asks to open the update settings. */
    private val updatesSignal = mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        val app = newBase.applicationContext as SaxApp
        appliedLanguage = app.container.settings.currentBlocking().language
        super.attachBaseContext(LocaleHelper.wrap(newBase, appliedLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initial = container.settings.currentBlocking()
        if (intent?.getBooleanExtra(EXTRA_START_SESSION, false) == true && savedInstanceState == null) startSignal.intValue++
        if (intent?.getBooleanExtra(EXTRA_OPEN_UPDATES, false) == true && savedInstanceState == null) updatesSignal.intValue++
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initial)
            // Switching language recreates the activity so every string changes.
            LaunchedEffect(settings.language) { if (settings.language != appliedLanguage) recreate() }
            CompositionLocalProvider(LocalContainer provides container, LocalSettings provides settings) {
                SaxTheme(settings.theme) {
                    AppNav(rememberNavController(), startOnboarding = !initial.onboardingDone, startSignal = startSignal.intValue, updatesSignal = updatesSignal.intValue, onRecreate = { recreate() })
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START_SESSION, false)) startSignal.intValue++
        if (intent.getBooleanExtra(EXTRA_OPEN_UPDATES, false)) updatesSignal.intValue++
    }

    companion object {
        const val EXTRA_START_SESSION = "start_session"
        const val EXTRA_OPEN_UPDATES = "open_updates"
    }
}
