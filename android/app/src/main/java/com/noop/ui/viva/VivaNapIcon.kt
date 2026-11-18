package com.noop.ui.viva

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Viva's own nap mark: a charging battery under a rising zZ, since a nap tops the body up. Drawn on the
 * 24-unit grid in the rounded outlined stroke the app's other icons use; black paths, tinted where shown.
 */
val VivaNapIcon: ImageVector by lazy {
    val ink = SolidColor(Color.Black)
    ImageVector.Builder(name = "VivaNap", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        // Battery body, rounded.
        path(stroke = ink, strokeLineWidth = 1.8f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4.3f, 9.5f)
            lineTo(14.7f, 9.5f)
            arcTo(1.8f, 1.8f, 0f, false, true, 16.5f, 11.3f)
            lineTo(16.5f, 16.7f)
            arcTo(1.8f, 1.8f, 0f, false, true, 14.7f, 18.5f)
            lineTo(4.3f, 18.5f)
            arcTo(1.8f, 1.8f, 0f, false, true, 2.5f, 16.7f)
            lineTo(2.5f, 11.3f)
            arcTo(1.8f, 1.8f, 0f, false, true, 4.3f, 9.5f)
            close()
        }
        // Terminal nub.
        path(fill = ink) {
            moveTo(17.4f, 12.3f)
            lineTo(18.4f, 12.3f)
            arcTo(0.6f, 0.6f, 0f, false, true, 19f, 12.9f)
            lineTo(19f, 15.1f)
            arcTo(0.6f, 0.6f, 0f, false, true, 18.4f, 15.7f)
            lineTo(17.4f, 15.7f)
            close()
        }
        // Charging bolt, centred in the body.
        path(fill = ink) {
            moveTo(10.4f, 10.9f)
            lineTo(6.9f, 14.6f)
            lineTo(9.3f, 14.6f)
            lineTo(8.5f, 17.2f)
            lineTo(12.1f, 13.4f)
            lineTo(9.7f, 13.4f)
            close()
        }
        // z, then a larger Z rising to the right.
        path(stroke = ink, strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(13.9f, 4.9f)
            lineTo(16.5f, 4.9f)
            lineTo(13.9f, 7.7f)
            lineTo(16.5f, 7.7f)
        }
        path(stroke = ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(18.1f, 1.8f)
            lineTo(21.7f, 1.8f)
            lineTo(18.1f, 5.9f)
            lineTo(21.7f, 5.9f)
        }
    }.build()
}
