package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * The AutoISF factors of the last loop run as text, "-" where the run has none. Display only.
 * [acce] is null while AutoISF is not the active APS, and then the chip is not shown.
 * [apsPluginClassName] is the class of the active APS plugin, so a click can open its screen.
 */
@Immutable
data class AutoIsfFactorsUiState(
    val acce: String? = null,
    val bg: String = "-",
    val pp: String = "-",
    val dura: String = "-",
    val final: String = "-",
    val iobTh: String = "-",
    val apsPluginClassName: String? = null
)

/**
 * AutoISF fork: a chip over the whole width under the BG and the chips, with the factors of the last
 * loop run (acce, bg, pp, dura, final ISF and the effective iobTH). The text is right-aligned.
 * Same color and height as the sensitivity chip. Shown only while AutoISF is the active APS.
 * A click or a long click opens the AutoISF screen (APS tab), where the calculation is shown.
 * Display only.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoIsfChip(
    state: AutoIsfFactorsUiState,
    onNavigate: (NavigationRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    val acce = state.acce ?: return
    val openAps = { state.apsPluginClassName?.let { onNavigate(NavigationRequest.Plugin(it)) } ?: Unit }
    val openLabel = stringResource(UiStrings.overview_autoisf_open)
    val style = MaterialTheme.typography.bodyMedium
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
        color = ElementType.SENSITIVITY.color().copy(alpha = 0.2f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.medium)
            .combinedClickable(
                onClickLabel = openLabel,
                onLongClickLabel = openLabel,
                onLongClick = openAps,
                onClick = openAps
            )
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AapsSpacing.chipHeight)
                .padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium, Alignment.End),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(UiStrings.overview_autoisf_factors, acce, state.bg, state.pp, state.dura, state.final),
                style = style,
                color = textColor,
                textAlign = TextAlign.End
            )
            Text(
                text = stringResource(UiStrings.overview_autoisf_iobth, state.iobTh),
                style = style,
                color = textColor,
                textAlign = TextAlign.End
            )
        }
    }
}
