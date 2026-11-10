package work.spyglee.ladger.ui.components

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import work.spyglee.ladger.AppContainer
import work.spyglee.ladger.R
import work.spyglee.ladger.reminder.ExactAlarms
import work.spyglee.ladger.ui.LocalAppContainer

/**
 * Asks once for exact alarms (decision D10), right after the notification permission: before a planned
 * transaction is saved or the reminder is turned on. Returns the function to call instead of [onContinue].
 *
 * Whatever the answer, [onContinue] runs straight away. "Allow" then opens the system page. The app sets its
 * alarms again once the user allows them there, so nothing waits for the user to come back. It doesn't ask
 * again; Settings offers the page for as long as exact alarms are off.
 */
@Composable
fun rememberExactAlarmPrompt(onContinue: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val continueWith by rememberUpdatedState(onContinue)
    // Counts as asked until the setting has loaded, so the dialog never shows by mistake.
    val askedBefore by container.settings.didAskExactAlarms.collectAsStateWithLifecycle(initialValue = true)
    var showing by rememberSaveable { mutableStateOf(false) }

    val answer = { allow: Boolean ->
        showing = false
        scope.launch { container.settings.markAskedExactAlarms() }
        continueWith()
        if (allow) openExactAlarmSettings(context, container)
    }
    if (showing) {
        AlertDialog(
            onDismissRequest = { answer(false) },
            title = { Text(stringResource(R.string.exact_alarms_title)) },
            text = { Text(stringResource(R.string.exact_alarms_message)) },
            confirmButton = { TextButton(onClick = { answer(true) }) { Text(stringResource(R.string.exact_alarms_allow)) } },
            dismissButton = { TextButton(onClick = { answer(false) }) { Text(stringResource(R.string.not_now)) } },
        )
    }
    return { if (askedBefore || ExactAlarms.allowed(context)) continueWith() else showing = true }
}

/** Opens the system page for exact alarms. It's one of the app's own pages, so coming back doesn't lock (D5). */
fun openExactAlarmSettings(context: Context, container: AppContainer) {
    container.appLock.expectReturn()
    // Some phones may not have the page; then the alarms simply stay inexact.
    runCatching { context.startActivity(ExactAlarms.settingsIntent(context)) }
}
