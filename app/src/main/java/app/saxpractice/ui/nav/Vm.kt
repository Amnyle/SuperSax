package app.saxpractice.ui.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.saxpractice.AppContainer

/** Creates a ViewModel with the app container (manual DI). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val c = LocalContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(c) } })
}
