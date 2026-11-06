package com.omnibuds.android.ui.compose.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * First-party Material vector icons for glyphs the Compose material-icons
 * core set does not ship. Geometry follows the Material Design icon grid
 * (24dp viewport, filled style) so these sit consistently next to the
 * stock icons used elsewhere in the app.
 */
object OmniIcons {

    /** Bolt / Lightning glyph — charging indicator badge. */
    val Bolt: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniBolt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11f, 21f)
                lineTo(13f, 13f)
                horizontalLineTo(18f)
                lineTo(13f, 3f)
                lineTo(11f, 11f)
                horizontalLineTo(6f)
                close()
            }
        }.build()
    }

    /** Headphones — earbuds, gallery cards, device rows. */
    val Headphones: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniHeadphones",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 1f)
                curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                verticalLineToRelative(7f)
                curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(-8f)
                horizontalLineTo(5f)
                verticalLineToRelative(-2f)
                curveToRelative(0f, -3.87f, 3.13f, -7f, 7f, -7f)
                reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(8f)
                horizontalLineToRelative(3f)
                curveToRelative(1.66f, 0f, 3f, -1.34f, 3f, -3f)
                verticalLineToRelative(-7f)
                curveToRelative(0f, -4.97f, -4.03f, -9f, -9f, -9f)
                close()
            }
        }.build()
    }

    /** Bar chart — equalizer-style glyph. */
    val BarChart: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniBarChart",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 9.2f)
                horizontalLineToRelative(3f)
                verticalLineTo(19f)
                horizontalLineTo(5f)
                close()
                moveTo(10.6f, 5f)
                horizontalLineToRelative(2.8f)
                verticalLineToRelative(14f)
                horizontalLineToRelative(-2.8f)
                close()
                moveTo(17.4f, 3f)
                horizontalLineToRelative(2.8f)
                verticalLineToRelative(16f)
                horizontalLineToRelative(-2.8f)
                close()
            }
        }.build()
    }

    /** Charging case — rounded square with a lid slot (EvenOdd cut-out). */
    val Case: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniCase",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                // outer rounded square
                moveTo(8f, 3f)
                horizontalLineToRelative(8f)
                curveToRelative(2.76f, 0f, 5f, 2.24f, 5f, 5f)
                verticalLineToRelative(8f)
                curveToRelative(0f, 2.76f, -2.24f, 5f, -5f, 5f)
                horizontalLineTo(8f)
                curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f)
                verticalLineTo(8f)
                curveToRelative(0f, -2.76f, 2.24f, -5f, 5f, -5f)
                close()
                // lid slot (cut out)
                moveTo(7f, 9.5f)
                horizontalLineToRelative(10f)
                verticalLineToRelative(2f)
                horizontalLineTo(7f)
                close()
            }
        }.build()
    }

    /** Play — media control. */
    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniPlay",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 5f)
                verticalLineToRelative(14f)
                lineTo(19f, 12f)
                close()
            }
        }.build()
    }

    /** Pause — media control. */
    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniPause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineTo(6f)
                close()
                moveTo(14f, 5f)
                verticalLineToRelative(14f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                close()
            }
        }.build()
    }

    /** Skip next — media control. */
    val SkipNext: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniSkipNext",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 18f)
                lineTo(14.5f, 12f)
                lineTo(6f, 6f)
                close()
                moveTo(16f, 6f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(12f)
                horizontalLineToRelative(-2f)
                close()
            }
        }.build()
    }

    /** Skip previous — media control. */
    val SkipPrevious: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniSkipPrevious",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 6f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(12f)
                horizontalLineTo(6f)
                close()
                moveTo(18f, 6f)
                lineTo(9.5f, 12f)
                lineTo(18f, 18f)
                close()
            }
        }.build()
    }

    /** Volume — speaker glyph for the volume slider row. */
    val VolumeUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniVolumeUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 9f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(4f)
                lineTo(12f, 20f)
                verticalLineTo(4f)
                lineTo(7f, 9f)
                close()
                moveTo(16.5f, 12f)
                arcTo(4.5f, 4.5f, 0f, false, true, 13.5f, 8.2f)
                lineTo(14.6f, 9.9f)
                arcTo(2.5f, 2.5f, 0f, false, false, 14.6f, 14.1f)
                lineTo(13.5f, 15.8f)
                arcTo(4.5f, 4.5f, 0f, false, true, 16.5f, 12f)
                close()
            }
        }.build()
    }

    /** Noise cancellation — sound bars with a slash (blocked sound). */
    val NoiseOff: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniNoiseOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // bars
                moveTo(3f, 9f)
                horizontalLineToRelative(2.4f)
                verticalLineToRelative(6f)
                horizontalLineTo(3f)
                close()
                moveTo(7.6f, 6f)
                horizontalLineToRelative(2.4f)
                verticalLineToRelative(12f)
                horizontalLineTo(7.6f)
                close()
                moveTo(12.2f, 8f)
                horizontalLineToRelative(2.4f)
                verticalLineToRelative(8f)
                horizontalLineToRelative(-2.4f)
                close()
                moveTo(16.8f, 10f)
                horizontalLineToRelative(2.4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(-2.4f)
                close()
                // slash
                moveTo(2.6f, 19.4f)
                lineTo(19.4f, 2.6f)
                lineTo(21.4f, 4.6f)
                lineTo(4.6f, 21.4f)
                close()
            }
        }.build()
    }

    /** Transparency — sound bars passing through, unblocked. */
    val SoundWaves: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniSoundWaves",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 9f)
                horizontalLineToRelative(2.4f)
                verticalLineToRelative(6f)
                horizontalLineTo(3f)
                close()
                moveTo(7.6f, 6f)
                horizontalLineToRelative(2.4f)
                verticalLineToRelative(12f)
                horizontalLineTo(7.6f)
                close()
                moveTo(12.2f, 8f)
                horizontalLineToRelative(2.4f)
                verticalLineToRelative(8f)
                horizontalLineToRelative(-2.4f)
                close()
                moveTo(16.8f, 10f)
                horizontalLineToRelative(2.4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(-2.4f)
                close()
            }
        }.build()
    }

    /** Adaptive — four-point sparkle (smart/automatic). */
    val Sparkle: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniSparkle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2.5f)
                lineTo(14.2f, 9.8f)
                lineTo(21.5f, 12f)
                lineTo(14.2f, 14.2f)
                lineTo(12f, 21.5f)
                lineTo(9.8f, 14.2f)
                lineTo(2.5f, 12f)
                lineTo(9.8f, 9.8f)
                close()
            }
        }.build()
    }

    /** Right earbud — battery ring glyph. */
    val BudRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniBudRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12.4f, 2.2f)
                curveTo(16.8f, 2.2f, 19.9f, 5.5f, 19.9f, 9.7f)
                curveTo(19.9f, 12.0f, 19.2f, 14.0f, 18.0f, 15.7f)
                lineTo(20.6f, 20.3f)
                curveTo(21.2f, 21.4f, 20.5f, 22.7f, 19.3f, 22.6f)
                curveTo(18.6f, 22.6f, 18.0f, 22.1f, 17.6f, 21.4f)
                lineTo(15.2f, 16.9f)
                curveTo(14.3f, 17.4f, 13.2f, 17.7f, 12.0f, 17.7f)
                curveTo(7.7f, 17.7f, 4.4f, 14.2f, 4.4f, 9.9f)
                curveTo(4.4f, 5.6f, 8.0f, 2.2f, 12.4f, 2.2f)
                close()
            }
        }.build()
    }

    /** Left earbud — battery ring glyph. */
    val BudLeft: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniBudLeft",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11.6f, 2.2f)
                curveTo(7.2f, 2.2f, 4.1f, 5.5f, 4.1f, 9.7f)
                curveTo(4.1f, 12.0f, 4.8f, 14.0f, 6.0f, 15.7f)
                lineTo(3.4f, 20.3f)
                curveTo(2.8f, 21.4f, 3.5f, 22.7f, 4.7f, 22.6f)
                curveTo(5.4f, 22.6f, 6.0f, 22.1f, 6.4f, 21.4f)
                lineTo(8.8f, 16.9f)
                curveTo(9.7f, 17.4f, 10.8f, 17.7f, 12.0f, 17.7f)
                curveTo(16.3f, 17.7f, 19.6f, 14.2f, 19.6f, 9.9f)
                curveTo(19.6f, 5.6f, 16.0f, 2.2f, 11.6f, 2.2f)
                close()
            }
        }.build()
    }

    /** Neckband earphones: band with two hanging buds (category artwork + list glyph). */
    val Neckband: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniNeckband",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                curveTo(7f, 3f, 3.5f, 6.8f, 3.5f, 12f)
                lineTo(3.5f, 14f)
                lineTo(6f, 14f)
                lineTo(6f, 12f)
                curveTo(6f, 8.2f, 8.6f, 5.5f, 12f, 5.5f)
                curveTo(15.4f, 5.5f, 18f, 8.2f, 18f, 12f)
                lineTo(18f, 14f)
                lineTo(20.5f, 14f)
                lineTo(20.5f, 12f)
                curveTo(20.5f, 6.8f, 17f, 3f, 12f, 3f)
                close()
                moveTo(3.9f, 13.5f)
                lineTo(5.6f, 13.5f)
                lineTo(5.6f, 16.7f)
                lineTo(3.9f, 16.7f)
                close()
                moveTo(18.4f, 13.5f)
                lineTo(20.1f, 13.5f)
                lineTo(20.1f, 16.7f)
                lineTo(18.4f, 16.7f)
                close()
                moveTo(2.65f, 18.6f)
                arcTo(2.1f, 2.1f, 0f, true, true, 6.85f, 18.6f)
                arcTo(2.1f, 2.1f, 0f, true, true, 2.65f, 18.6f)
                close()
                moveTo(17.15f, 18.6f)
                arcTo(2.1f, 2.1f, 0f, true, true, 21.35f, 18.6f)
                arcTo(2.1f, 2.1f, 0f, true, true, 17.15f, 18.6f)
                close()
            }
        }.build()
    }

    /** Pencil — rename / edit alias. */
    val Edit: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniEdit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 17.25f)
                verticalLineTo(21f)
                horizontalLineToRelative(3.75f)
                lineTo(17.81f, 9.94f)
                lineToRelative(-3.75f, -3.75f)
                lineTo(3f, 17.25f)
                close()
                moveTo(20.71f, 7.04f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                lineToRelative(-2.34f, -2.34f)
                arcToRelative(0.9959f, 0.9959f, 0f, false, false, -1.41f, 0f)
                lineToRelative(-1.83f, 1.83f)
                lineToRelative(3.75f, 3.75f)
                lineToRelative(1.83f, -1.83f)
                close()
            }
        }.build()
    }

    /** Trash can — forget device. */
    val Trash: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniTrash",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(8f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                horizontalLineTo(6f)
                verticalLineToRelative(12f)
                close()
                moveTo(19f, 4f)
                horizontalLineToRelative(-3.5f)
                lineToRelative(-1f, -1f)
                horizontalLineToRelative(-5f)
                lineToRelative(-1f, 1f)
                horizontalLineTo(5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(14f)
                verticalLineTo(4f)
                close()
            }
        }.build()
    }

    /** Power symbol — disconnect action. */
    val Power: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniPower",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13f, 3f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(10f)
                horizontalLineToRelative(2f)
                verticalLineTo(3f)
                close()
                moveToRelative(4.83f, 2.17f)
                lineToRelative(-1.42f, 1.42f)
                curveTo(17.99f, 7.86f, 19f, 9.81f, 19f, 12f)
                curveToRelative(0f, 3.87f, -3.13f, 7f, -7f, 7f)
                reflectiveCurveToRelative(-7f, -3.13f, -7f, -7f)
                curveToRelative(0f, -2.19f, 1.01f, -4.14f, 2.58f, -5.42f)
                lineTo(6.17f, 4.17f)
                curveTo(4.23f, 5.71f, 3f, 8.22f, 3f, 12f)
                curveToRelative(0f, 4.97f, 4.03f, 9f, 9f, 9f)
                reflectiveCurveToRelative(9f, -4.03f, 9f, -9f)
                curveToRelative(0f, -2.78f, -1.23f, -5.29f, -3.17f, -6.83f)
                close()
            }
        }.build()
    }

    /** Person outline — transparency mode. */
    val PersonOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniPersonOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 5.9f)
                curveToRelative(1.16f, 0f, 2.1f, 0.94f, 2.1f, 2.1f)
                reflectiveCurveToRelative(-0.94f, 2.1f, -2.1f, 2.1f)
                reflectiveCurveTo(9.9f, 9.16f, 9.9f, 8f)
                reflectiveCurveToRelative(0.94f, -2.1f, 2.1f, -2.1f)
                moveToRelative(0f, 9f)
                curveToRelative(2.97f, 0f, 6.1f, 1.46f, 6.1f, 2.1f)
                verticalLineToRelative(1.1f)
                horizontalLineTo(5.9f)
                verticalLineTo(17f)
                curveToRelative(0f, -0.64f, 3.13f, -2.1f, 6.1f, -2.1f)
                moveToRelative(0f, -12f)
                curveTo(9.79f, 3f, 8f, 4.79f, 8f, 7f)
                reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
                reflectiveCurveToRelative(4f, -1.79f, 4f, -4f)
                reflectiveCurveToRelative(-1.79f, -4f, -4f, -4f)
                close()
                moveToRelative(0f, 9.9f)
                curveToRelative(-2.67f, 0f, -8f, 1.34f, -8f, 4f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(-3f)
                curveToRelative(0f, -2.66f, -5.33f, -4f, -8f, -4f)
                close()
            }
        }.build()
    }

    /** Pair of earbuds — single-reading battery ring glyph. */
    val BudsPair: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniBudsPair",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7.86f, 4.85f)
                curveTo(4.95f, 4.85f, 2.91f, 7.03f, 2.91f, 9.8f)
                curveTo(2.91f, 11.32f, 3.37f, 12.64f, 4.16f, 13.76f)
                lineTo(2.44f, 16.8f)
                curveTo(2.05f, 17.52f, 2.51f, 18.38f, 3.3f, 18.32f)
                curveTo(3.76f, 18.32f, 4.16f, 17.99f, 4.42f, 17.52f)
                lineTo(6.01f, 14.55f)
                curveTo(6.6f, 14.88f, 7.33f, 15.08f, 8.12f, 15.08f)
                curveTo(10.96f, 15.08f, 13.14f, 12.77f, 13.14f, 9.93f)
                curveTo(13.14f, 7.1f, 10.76f, 4.85f, 7.86f, 4.85f)
                close()
                moveTo(16.38f, 4.85f)
                curveTo(19.29f, 4.85f, 21.33f, 7.03f, 21.33f, 9.8f)
                curveTo(21.33f, 11.32f, 20.87f, 12.64f, 20.08f, 13.76f)
                lineTo(21.8f, 16.8f)
                curveTo(22.19f, 17.52f, 21.73f, 18.38f, 20.94f, 18.32f)
                curveTo(20.48f, 18.32f, 20.08f, 17.99f, 19.82f, 17.52f)
                lineTo(18.23f, 14.55f)
                curveTo(17.64f, 14.88f, 16.91f, 15.08f, 16.12f, 15.08f)
                curveTo(13.28f, 15.08f, 11.1f, 12.77f, 11.1f, 9.93f)
                curveTo(11.1f, 7.1f, 13.48f, 4.85f, 16.38f, 4.85f)
                close()
            }
        }.build()
    }

    /** Sliders — audio/system control rows (material "tune" geometry). */
    val Sliders: ImageVector by lazy {
        ImageVector.Builder(
            name = "OmniSliders",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // tracks (gaps left where the knobs sit)
                moveTo(3f, 5f)
                horizontalLineToRelative(9f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                close()
                moveTo(18f, 5f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-3f)
                close()
                moveTo(3f, 11f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                close()
                moveTo(12f, 11f)
                horizontalLineToRelative(9f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-9f)
                close()
                moveTo(3f, 17f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                close()
                moveTo(21f, 17f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-3f)
                verticalLineToRelative(-2f)
                close()
                // knobs (full circles from two semicircle arcs)
                moveTo(15f, 3.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 15f, 8.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 15f, 3.5f)
                close()
                moveTo(9f, 9.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 9f, 14.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 9f, 9.5f)
                close()
                moveTo(18f, 15.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 18f, 20.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 18f, 15.5f)
                close()
            }
        }.build()
    }
}
