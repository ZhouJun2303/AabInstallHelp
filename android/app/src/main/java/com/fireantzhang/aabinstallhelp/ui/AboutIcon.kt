package com.fireantzhang.aabinstallhelp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val AboutIcon: ImageVector
    get() {
        val cached = _aboutIcon
        if (cached != null) return cached
        return ImageVector.Builder(
            name = "About",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(20.5f, 12f)
                arcToRelative(8.5f, 8.5f, 0f, true, false, -17f, 0f)
                arcToRelative(8.5f, 8.5f, 0f, true, false, 17f, 0f)
                close()
                moveTo(13.15f, 8f)
                arcToRelative(1.15f, 1.15f, 0f, true, false, -2.3f, 0f)
                arcToRelative(1.15f, 1.15f, 0f, true, false, 2.3f, 0f)
                close()
                moveTo(11.15f, 10.5f)
                horizontalLineToRelative(1.7f)
                verticalLineToRelative(6.1f)
                horizontalLineToRelative(-1.7f)
                close()
            }
        }.build().also { _aboutIcon = it }
    }

private var _aboutIcon: ImageVector? = null
