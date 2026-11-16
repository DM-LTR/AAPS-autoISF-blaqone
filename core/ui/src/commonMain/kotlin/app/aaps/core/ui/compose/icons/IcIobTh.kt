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
 * Icon for iobTH (AutoISF), from the AutoISF team repo.
 *
 * Bounding box: x: 5.087-18.913, y: 4-20 (viewport: 24x24, ~67% height)
 */
val IcIobTh: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcIobTh",
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
            moveTo(4f,4f)
            horizontalLineToRelative(15f)
            verticalLineToRelative(-1.0f)
            horizontalLineToRelative(-15f)
            verticalLineToRelative(1.0f)
            close()

            moveTo(4f, 9.2f)
            lineToRelative(7.5f, -5.2f)
            lineToRelative(7.5f, +5.2f)
            verticalLineToRelative(+1.2f)
            lineToRelative(-7.5f, -5.2f)
            lineToRelative(-7.5f, +5.2f)
            verticalLineToRelative(-1.2f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcIobThIconPreview() {
    Icon(
        imageVector = IcIobTh,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}
// TODO: get old style line graph?
/*

<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
  <path
      android:pathData="M12,4L5.087,20h13.826L12,4zM11.375,8.236l4.614,10.678H6.761L11.375,8.236z"
      android:fillColor="#36FF00"/>
</vector>

 */
