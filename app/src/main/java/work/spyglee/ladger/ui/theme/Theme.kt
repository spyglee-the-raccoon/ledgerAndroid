package work.spyglee.ladger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import work.spyglee.ladger.data.CategoryColor

private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    secondary = Color(0xFF5856D6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    secondary = Color(0xFF5E5CE6),
)

@Composable
fun LadgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

/** Income and expense amounts, as on iOS. */
val IncomeColor = Color(0xFF34C759)
val ExpenseColor = Color(0xFFFF3B30)

/** The iOS system colors behind [CategoryColor], in their light and dark variants. */
@Composable
@ReadOnlyComposable
fun CategoryColor.toColor(): Color {
    val dark = isSystemInDarkTheme()
    fun pick(light: Long, darkVariant: Long) = Color(if (dark) darkVariant else light)
    return when (this) {
        CategoryColor.PRIMARY -> MaterialTheme.colorScheme.onSurface
        CategoryColor.RED -> pick(0xFFFF3B30, 0xFFFF453A)
        CategoryColor.ORANGE -> pick(0xFFFF9500, 0xFFFF9F0A)
        CategoryColor.YELLOW -> pick(0xFFFFCC00, 0xFFFFD60A)
        CategoryColor.GREEN -> pick(0xFF34C759, 0xFF30D158)
        CategoryColor.MINT -> pick(0xFF00C7BE, 0xFF63E6E2)
        CategoryColor.TEAL -> pick(0xFF30B0C7, 0xFF40C8E0)
        CategoryColor.CYAN -> pick(0xFF32ADE6, 0xFF64D2FF)
        CategoryColor.BLUE -> pick(0xFF007AFF, 0xFF0A84FF)
        CategoryColor.INDIGO -> pick(0xFF5856D6, 0xFF5E5CE6)
        CategoryColor.PURPLE -> pick(0xFFAF52DE, 0xFFBF5AF2)
        CategoryColor.PINK -> pick(0xFFFF2D55, 0xFFFF375F)
        CategoryColor.BROWN -> pick(0xFFA2845E, 0xFFAC8E68)
        CategoryColor.GRAY -> pick(0xFF8E8E93, 0xFF8E8E93)
    }
}
