package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.ui.UiStrings
import app.aaps.core.ui.compose.stringResource

/**
 * What the status line under the BG shows. Times are epoch milliseconds, null when there is none.
 * [now] moves every second, so the "ago" texts count up like the BG age.
 */
@Immutable
data class LoopStatusUiState(
    val visible: Boolean = false,
    val lastRun: Long? = null,
    val smbAmount: Double? = null,
    val smbTime: Long? = null,
    val tbrText: String? = null,
    val tbrStart: Long? = null,
    val now: Long = 0L
)

/** A loop run older than this is shown in the warning color. */
private const val LOOP_STALE_MS = 15 * 60 * 1000L

/** Ages below this are shown in seconds, like the BG age. */
private const val SECONDS_LIMIT_MS = 10 * 60 * 1000L

/**
 * One line under the BG: when the loop last ran, the last SMB, and the running temporary basal.
 * Only shown where the loop runs (master). Display only.
 */
@Composable
fun LoopStatusLine(state: LoopStatusUiState, modifier: Modifier = Modifier) {
    if (!state.visible) return
    val style = MaterialTheme.typography.bodySmall
    val normal = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        val lastRun = state.lastRun
        val loopStale = lastRun == null || state.now - lastRun > LOOP_STALE_MS
        Text(
            text = stringResource(UiStrings.overview_status_loop, lastRun?.let { ageText(it, state.now) } ?: "-"),
            style = style,
            color = if (loopStale) MaterialTheme.colorScheme.error else normal
        )
        val smbAmount = state.smbAmount
        val smbTime = state.smbTime
        if (smbAmount != null && smbTime != null) {
            Text(
                text = stringResource(
                    UiStrings.overview_status_smb,
                    stringResource(InterfacesStrings.format_insulin_units, smbAmount),
                    ageText(smbTime, state.now)
                ),
                style = style,
                color = normal
            )
        }
        val tbrText = state.tbrText
        val tbrStart = state.tbrStart
        Text(
            text = if (tbrText != null && tbrStart != null)
                stringResource(UiStrings.overview_status_tbr, tbrText, ageText(tbrStart, state.now))
            else stringResource(UiStrings.overview_status_no_tbr),
            style = style,
            color = normal
        )
    }
}

/** "47s ago" up to 10 minutes, then "12m ago", in the user's language. */
@Composable
private fun ageText(timestamp: Long, now: Long): String {
    val ageMs = (now - timestamp).coerceAtLeast(0L)
    return if (ageMs < SECONDS_LIMIT_MS) stringResource(InterfacesStrings.secago, (ageMs / 1000L).toInt())
    else stringResource(InterfacesStrings.minago, (ageMs / 60_000L).toInt())
}

/**
 * One parabola fit as text, formatted in the ViewModel.
 * [usable] is true when the correlation reaches the level where AutoISF uses the fit for acce_ISF.
 */
@Immutable
data class ParabolaFitText(val acceleration: String, val correlation: String, val minutes: Int, val usable: Boolean)

/** Both parabola fits for [ParabolaLine], null when there is nothing to compare. Display only. */
@Immutable
data class ParabolaUiState(
    val raw: ParabolaFitText? = null,
    val smoothed: ParabolaFitText? = null
)

/**
 * AutoISF fork: the parabola fit on the raw readings and on the UKF-smoothed readings, side by side.
 * A fit is green when AutoISF would use it for acce_ISF (correlation 0.9 or more). AutoISF itself
 * always uses the raw one. Display only.
 */
@Composable
fun ParabolaLine(state: ParabolaUiState, modifier: Modifier = Modifier) {
    val raw = state.raw ?: return
    val smoothed = state.smoothed ?: return
    val style = MaterialTheme.typography.bodySmall
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        Text(text = parabolaText(UiStrings.overview_parabola_raw, raw), style = style, color = fitColor(raw))
        Text(text = parabolaText(UiStrings.overview_parabola_smoothed, smoothed), style = style, color = fitColor(smoothed))
    }
}

@Composable
private fun fitColor(fit: ParabolaFitText) =
    if (fit.usable) AapsTheme.generalColors.bgInRange else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun parabolaText(template: TextRef, fit: ParabolaFitText): String =
    stringResource(template, fit.acceleration, fit.correlation, stringResource(CoreUiStrings.format_mins, fit.minutes))
