package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Icon for acce_weight.
 * adapted from LoopSuspended
 *
 * Bounding box: x: 1.3-22.8, y: 2.1-21.8 (viewport: 24x24, ~90% width)
 */
val IcAcceWeight: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcLoopLgs",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {

            moveTo(22.8f, 12.19f)
            lineToRelative(-2.687f, -4.0f)
            lineToRelative(-3.26f, 3.9f)
            lineToRelative(2.0f, -0.728f)
            arcToRelative(10f, 10f, 0.0f, false, true, -13.8f, 7.2f )
            lineToRelative(-1.082f, 2.0378f)
            arcToRelative(12f, 12f, 0.0f, false, false, 16.8f, -9.2f )
            lineTo(22.8f, 12.19f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcAcceWeightIconPreview() {
    Icon(
        imageVector = IcAcceWeight,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}
