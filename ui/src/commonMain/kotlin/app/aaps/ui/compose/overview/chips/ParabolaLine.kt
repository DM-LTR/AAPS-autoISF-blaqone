package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.ui.UiStrings
import app.aaps.core.ui.compose.stringResource

/**
 * One parabola fit as text, formatted in the ViewModel.
 * [usable] is true when the correlation reaches the level where AutoISF uses the fit for acce_ISF.
 * [active] is true for the fit AutoISF uses (setting "parabola on smoothed values"); it is shown in bold.
 */
@Immutable
data class ParabolaFitText(val acceleration: String, val correlation: String, val minutes: Int, val usable: Boolean, val active: Boolean = false)

/** Both parabola fits for [ParabolaLine], null when there is nothing to compare. Display only. */
@Immutable
data class ParabolaUiState(
    val raw: ParabolaFitText? = null,
    val smoothed: ParabolaFitText? = null
)

/**
 * AutoISF fork, dev version: the parabola fit on the raw readings and on the UKF-smoothed readings, side
 * by side, inside the open AutoISF chip. A fit is green when its correlation is good enough for acce_ISF
 * (0.9 or more). The fit AutoISF uses (switch "Parabola on smoothed values") is bold and marked "used".
 * Display only.
 */
@Composable
fun ParabolaLine(state: ParabolaUiState, modifier: Modifier = Modifier) {
    val raw = state.raw ?: return
    val smoothed = state.smoothed ?: return
    val style = MaterialTheme.typography.bodySmall
    FlowRow(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium, Alignment.End)
    ) {
        Text(text = parabolaText(UiStrings.overview_parabola_raw, raw), style = style, color = fitColor(raw), fontWeight = fitWeight(raw))
        Text(text = parabolaText(UiStrings.overview_parabola_smoothed, smoothed), style = style, color = fitColor(smoothed), fontWeight = fitWeight(smoothed))
    }
}

@Composable
private fun fitColor(fit: ParabolaFitText) =
    if (fit.usable) AapsTheme.generalColors.bgInRange else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun parabolaText(template: TextRef, fit: ParabolaFitText): String {
    val text = stringResource(template, fit.acceleration, fit.correlation, stringResource(CoreUiStrings.format_mins, fit.minutes))
    return if (fit.active) stringResource(UiStrings.overview_parabola_used, text) else text
}

private fun fitWeight(fit: ParabolaFitText) = if (fit.active) FontWeight.Bold else FontWeight.Normal
