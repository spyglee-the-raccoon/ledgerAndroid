package work.spyglee.ladger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import work.spyglee.ladger.AppContainer
import work.spyglee.ladger.lock.AuthPurpose

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** A ViewModel scoped to the current screen, created from the app's dependencies. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel { create(container) }
}

/** Shows the system authentication prompt; returns false when the device has no screen lock to check against. */
fun interface Authenticator {
    fun authenticate(purpose: AuthPurpose): Boolean
}

val LocalAuthenticator = staticCompositionLocalOf { Authenticator { false } }
