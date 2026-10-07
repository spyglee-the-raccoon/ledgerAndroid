package work.spyglee.ladger.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import work.spyglee.ladger.R
import work.spyglee.ladger.data.CategoryColor
import work.spyglee.ladger.ui.theme.iconFor
import work.spyglee.ladger.ui.theme.toColor

/** Name field with a live icon preview, then the color and icon pickers, as in the iOS editors. */
@Composable
fun AppearanceForm(
    nameLabel: String,
    name: String,
    onNameChange: (String) -> Unit,
    systemImage: String,
    onIconChange: (String) -> Unit,
    color: CategoryColor,
    onColorChange: (CategoryColor) -> Unit,
    iconQuery: String,
    onIconQueryChange: (String) -> Unit,
    afterName: @Composable () -> Unit = {},
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(nameLabel) },
        leadingIcon = { Icon(iconFor(systemImage), contentDescription = null, tint = color.toColor()) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    afterName()
    Column {
        SectionHeader(stringResource(R.string.color), Modifier.padding(bottom = 4.dp))
        ColorPicker(color, onColorChange)
    }
    Column {
        SectionHeader(stringResource(R.string.icon), Modifier.padding(bottom = 4.dp))
        IconPicker(systemImage, iconQuery, onIconQueryChange, onIconChange)
    }
}

/** The "name already exists" alert. */
@Composable
fun DuplicateNameDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        title = { Text(stringResource(R.string.error)) },
        text = { Text(message) },
    )
}
