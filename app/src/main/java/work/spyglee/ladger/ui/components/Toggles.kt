package work.spyglee.ladger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import work.spyglee.ladger.R
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.ui.theme.toColor

/**
 * Swipe to the end to switch a row on or off (pause/resume, activate/deactivate), and to the start
 * to delete it, as iOS's leading and trailing swipe actions. Without [onDelete] only the toggle swipes.
 */
@Composable
fun ToggleOrDeleteSwipe(
    isOn: Boolean,
    turnOnLabel: String,
    turnOffLabel: String,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val currentOnToggle by rememberUpdatedState(onToggle)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        enableDismissFromEndToStart = onDelete != null,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> currentOnDelete?.invoke()
                SwipeToDismissBoxValue.StartToEnd -> {
                    currentOnToggle()
                    scope.launch { state.reset() }
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = {
            val toDelete = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        when {
                            toDelete -> MaterialTheme.colorScheme.errorContainer
                            isOn -> CategoryColor.ORANGE.toColor()
                            else -> CategoryColor.GREEN.toColor()
                        }
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = if (toDelete) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                when {
                    toDelete -> Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onErrorContainer)
                    isOn -> Icon(Icons.Outlined.Pause, turnOffLabel, tint = MaterialTheme.colorScheme.surface)
                    else -> Icon(Icons.Outlined.PlayArrow, turnOnLabel, tint = MaterialTheme.colorScheme.surface)
                }
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
    }
}

/** A title with a switch; the whole row toggles. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
