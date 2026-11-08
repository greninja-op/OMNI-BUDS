package com.omnibuds.android.ui.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibuds.android.ui.compose.model.AudioFormFactor

enum class GlyphSide {
    LEFT,
    RIGHT,
    PAIR,
    CASE,
}

/**
 * Apple-Grade Two-Tone Hardware Vector Audio Glyph System.
 *
 * Renders hardware-accurate vector silhouettes within a clean 64x64 coordinate space:
 * - Two-Tone Palette: Crisp shell (#F4F5F7), Translucent silicone/cushion (#8E929B),
 *   precision acoustic cutouts (#1C1D21), and inner bore accent (#5A5E66).
 * - Ergonomic 42° acoustic chamber angles with protruding silicone tips.
 * - Exact mathematical mirroring between Left and Right buds so they face each other.
 * - Covers all 9 industrial hardware form factors + matching cases.
 */
@Composable
fun AdaptiveAudioGlyph(
    formFactor: AudioFormFactor,
    side: GlyphSide = GlyphSide.LEFT,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shellColor: Color = Color(0xFFF4F5F7),
    accentColor: Color = Color(0xFF8E929B),
    darkCutoutColor: Color = Color(0xFF1C1D21),
    innerBoreColor: Color = Color(0xFF5A5E66),
) {
    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val scaleX = this.size.width / 64f
            val scaleY = this.size.height / 64f

            scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset.Zero) {
                when {
                    side == GlyphSide.CASE && formFactor.hasCase -> {
                        drawChargingCase(formFactor, shellColor, accentColor, darkCutoutColor)
                    }
                    side == GlyphSide.PAIR && formFactor.isBinauralSplit -> {
                        // Render Left bud on left, Right bud on right facing each other
                        scale(scaleX = 0.72f, scaleY = 0.72f, pivot = Offset(18f, 32f)) {
                            drawSingleBud(formFactor, isRight = false, shellColor, accentColor, darkCutoutColor, innerBoreColor)
                        }
                        scale(scaleX = 0.72f, scaleY = 0.72f, pivot = Offset(46f, 32f)) {
                            drawSingleBud(formFactor, isRight = true, shellColor, accentColor, darkCutoutColor, innerBoreColor)
                        }
                    }
                    else -> {
                        drawSingleBud(formFactor, isRight = (side == GlyphSide.RIGHT), shellColor, accentColor, darkCutoutColor, innerBoreColor)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSingleBud(
    formFactor: AudioFormFactor,
    isRight: Boolean,
    shell: Color,
    accent: Color,
    cutout: Color,
    bore: Color,
) {
    // If Right bud, mirror horizontally across X=32 so buds naturally face each other
    if (isRight && formFactor.isBinauralSplit) {
        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(32f, 32f)) {
            drawBudPath(formFactor, shell, accent, cutout, bore)
        }
    } else {
        drawBudPath(formFactor, shell, accent, cutout, bore)
    }
}

private fun DrawScope.drawBudPath(
    formFactor: AudioFormFactor,
    shell: Color,
    accent: Color,
    cutout: Color,
    bore: Color,
) {
    when (formFactor) {
        AudioFormFactor.STEM_IN_EAR -> drawStemInEar(shell, accent, cutout, bore)
        AudioFormFactor.STEM_HALF_IN_EAR -> drawStemHalfInEar(shell, accent, cutout)
        AudioFormFactor.BUD_BEAN_IN_EAR -> drawBudBeanInEar(shell, accent, cutout, bore)
        AudioFormFactor.BUD_OPEN_RING -> drawBudOpenRing(shell, accent, cutout)
        AudioFormFactor.OPEN_EAR_HOOK -> drawOpenEarHook(shell, accent, cutout)
        AudioFormFactor.OPEN_EAR_CLIP -> drawOpenEarClip(shell, accent, cutout)
        AudioFormFactor.NECKBAND -> drawNeckband(shell, accent, cutout)
        AudioFormFactor.BONE_CONDUCTION -> drawBoneConduction(shell, accent, cutout)
        AudioFormFactor.HEADSET_OVER_ON_EAR -> drawHeadset(isOpenBack = false, shell, accent, cutout)
        AudioFormFactor.HEADSET_OPEN_BACK -> drawHeadset(isOpenBack = true, shell, accent, cutout)
    }
}

/**
 * 1. STEM_IN_EAR (realme Buds T300, AirPods Pro, OnePlus Buds Pro):
 * 42° tilted ergonomic chamber, layered silicone tip pointing inward, dark vent slit, stem + charging cap.
 */
private fun DrawScope.drawStemInEar(shell: Color, accent: Color, cutout: Color, bore: Color) {
    // Protruding Silicone Ear-tip (pointing inward towards right at 42°)
    val siliconeTip = Path().apply {
        moveTo(33f, 15f)
        cubicTo(37f, 13f, 43f, 14f, 46f, 18f)
        cubicTo(48f, 21f, 48f, 25f, 45f, 28f)
        cubicTo(42f, 31f, 36f, 31f, 32f, 29f)
        close()
    }
    drawPath(siliconeTip, color = accent)

    // Inner Silicone Bore
    val siliconeBore = Path().apply {
        moveTo(42f, 19f)
        cubicTo(45f, 21f, 45f, 25f, 42f, 27f)
        cubicTo(39f, 28f, 38f, 24f, 40f, 21f)
        close()
    }
    drawPath(siliconeBore, color = bore)

    // Primary Ergonomic Head Shell (Tilted oval chamber)
    val headShell = Path().apply {
        moveTo(21f, 14f)
        cubicTo(26f, 9f, 36f, 11f, 39f, 17f)
        cubicTo(42f, 23f, 40f, 31f, 34f, 35f)
        cubicTo(30f, 38f, 23f, 36f, 19f, 31f)
        cubicTo(15f, 26f, 16f, 19f, 21f, 14f)
        close()
    }
    drawPath(headShell, color = shell)

    // Stem (Smooth tapered down to bottom contact)
    val stem = Path().apply {
        moveTo(22f, 31f)
        cubicTo(23f, 34f, 24f, 43f, 24f, 52f)
        cubicTo(24f, 54.5f, 28.5f, 54.5f, 28.5f, 52f)
        cubicTo(28.5f, 43f, 29f, 34f, 30f, 31f)
        close()
    }
    drawPath(stem, color = shell)

    // Bottom Metallic Charging Contact Cap
    drawRoundRect(
        color = accent,
        topLeft = Offset(24f, 50.5f),
        size = Size(4.5f, 3.5f),
        cornerRadius = CornerRadius(1.5f, 1.5f),
    )

    // Recessed Dark Acoustic Vent Slit Pill
    drawRoundRect(
        color = cutout,
        topLeft = Offset(21.5f, 18f),
        size = Size(3.5f, 7f),
        cornerRadius = CornerRadius(1.75f, 1.75f),
    )
}

/**
 * 2. STEM_HALF_IN_EAR (AirPods 3/4, Nothing Ear stick):
 * Teardrop acoustic pod with dark speaker mesh grille, smooth stem, NO rubber tip.
 */
private fun DrawScope.drawStemHalfInEar(shell: Color, accent: Color, cutout: Color) {
    // Teardrop acoustic pod shell
    val pod = Path().apply {
        moveTo(22f, 13f)
        cubicTo(28f, 9f, 38f, 11f, 41f, 18f)
        cubicTo(43f, 23f, 41f, 30f, 35f, 34f)
        cubicTo(31f, 37f, 25f, 36f, 21f, 31f)
        cubicTo(17f, 26f, 17f, 18f, 22f, 13f)
        close()
    }
    drawPath(pod, color = shell)

    // Dark Acoustic Speaker Mesh Grille
    val speakerMesh = Path().apply {
        moveTo(34f, 18f)
        cubicTo(38f, 19f, 39f, 25f, 37f, 28f)
        cubicTo(34f, 30f, 32f, 27f, 32f, 22f)
        close()
    }
    drawPath(speakerMesh, color = cutout)

    // Tapered Stem
    val stem = Path().apply {
        moveTo(23f, 31f)
        cubicTo(24f, 34f, 25f, 42f, 25f, 51f)
        cubicTo(25f, 53.5f, 29.5f, 53.5f, 29.5f, 51f)
        cubicTo(29.5f, 42f, 30.5f, 34f, 31.5f, 31f)
        close()
    }
    drawPath(stem, color = shell)

    // Bottom Contact
    drawRoundRect(
        color = accent,
        topLeft = Offset(25f, 49.5f),
        size = Size(4.5f, 3.5f),
        cornerRadius = CornerRadius(1.5f, 1.5f),
    )
}

/**
 * 3. BUD_BEAN_IN_EAR (Samsung Galaxy Buds, Sony WF-1000XM5, Pixel Buds):
 * Compact kidney/pill shell, touchpad contour, dark mic dot, inner silicone tip.
 */
private fun DrawScope.drawBudBeanInEar(shell: Color, accent: Color, cutout: Color, bore: Color) {
    // Inner Silicone Ear-tip
    val tip = Path().apply {
        moveTo(36f, 22f)
        cubicTo(42f, 21f, 46f, 25f, 46f, 31f)
        cubicTo(46f, 36f, 41f, 39f, 35f, 38f)
        close()
    }
    drawPath(tip, color = accent)

    // Main Sculpted Bean Shell
    val bean = Path().apply {
        moveTo(22f, 15f)
        cubicTo(31f, 13f, 39f, 17f, 40f, 26f)
        cubicTo(41f, 35f, 37f, 47f, 28f, 49f)
        cubicTo(19f, 50f, 15f, 42f, 16f, 33f)
        cubicTo(17f, 24f, 18f, 16f, 22f, 15f)
        close()
    }
    drawPath(bean, color = shell)

    // Recessed Touchpad Contour
    val touchContour = Path().apply {
        moveTo(24f, 22f)
        cubicTo(29f, 20f, 33f, 24f, 33f, 31f)
        cubicTo(33f, 37f, 28f, 41f, 23f, 39f)
    }
    drawPath(touchContour, color = accent, style = Stroke(width = 1.75f, cap = StrokeCap.Round))

    // Dark Mic Vent Dot
    drawCircle(color = cutout, radius = 1.75f, center = Offset(22f, 20f))
}

/**
 * 4. BUD_OPEN_RING (Sony LinkBuds, Galaxy Buds Live):
 * Open-ring donut acoustic loop + upper bean body.
 */
private fun DrawScope.drawBudOpenRing(shell: Color, accent: Color, cutout: Color) {
    // Upper bean body
    val upperBody = Path().apply {
        moveTo(23f, 14f)
        cubicTo(31f, 12f, 39f, 16f, 40f, 24f)
        cubicTo(40f, 31f, 35f, 35f, 29f, 36f)
        cubicTo(21f, 36f, 17f, 30f, 18f, 22f)
        close()
    }
    drawPath(upperBody, color = shell)

    // Open-Ring Donut Driver (Outer Ring)
    drawCircle(color = shell, radius = 12f, center = Offset(32f, 44f))
    // Donut Center Hole (Open-ear acoustic cutout)
    drawCircle(color = cutout, radius = 6.5f, center = Offset(32f, 44f))

    // Sensor / Vent Dot on upper body
    drawCircle(color = accent, radius = 1.8f, center = Offset(29f, 24f))
}

/**
 * 5. OPEN_EAR_HOOK (Powerbeats Pro, Nothing Ear Open, JBL Soundgear):
 * Sweeping flexible C-hook arching over the top, connecting to a front speaker pod.
 */
private fun DrawScope.drawOpenEarHook(shell: Color, accent: Color, cutout: Color) {
    // Flexible Over-Ear Hook (sweeping C-curve)
    val hook = Path().apply {
        moveTo(28f, 36f)
        cubicTo(18f, 33f, 12f, 24f, 14f, 16f)
        cubicTo(16f, 9f, 26f, 7f, 34f, 10f)
        cubicTo(41f, 13f, 45f, 20f, 44f, 30f)
        cubicTo(43f, 41f, 38f, 52f, 31f, 56f)
    }
    drawPath(hook, color = accent, style = Stroke(width = 4.5f, cap = StrokeCap.Round))

    // Front Speaker Driver Pod resting in ear
    val speakerPod = Path().apply {
        moveTo(25f, 27f)
        cubicTo(30f, 24f, 37f, 27f, 38f, 33f)
        cubicTo(39f, 39f, 34f, 44f, 28f, 44f)
        cubicTo(22f, 44f, 19f, 38f, 20f, 33f)
        close()
    }
    drawPath(speakerPod, color = shell)

    // Acoustic Output Mesh Slit
    drawRoundRect(
        color = cutout,
        topLeft = Offset(26f, 32f),
        size = Size(6f, 2.5f),
        cornerRadius = CornerRadius(1.25f, 1.25f),
    )
}

/**
 * 6. OPEN_EAR_CLIP (Bose Ultra Open, Huawei FreeClip):
 * Front acoustic sphere ball connected via curved C-bridge to rear battery barrel.
 */
private fun DrawScope.drawOpenEarClip(shell: Color, accent: Color, cutout: Color) {
    // C-Bridge Arch
    val bridge = Path().apply {
        moveTo(24f, 35f)
        cubicTo(21f, 24f, 36f, 18f, 42f, 26f)
        cubicTo(44f, 29f, 43f, 36f, 39f, 39f)
    }
    drawPath(bridge, color = accent, style = Stroke(width = 3.5f, cap = StrokeCap.Round))

    // Front Acoustic Driver Sphere
    drawCircle(color = shell, radius = 7.5f, center = Offset(22f, 37f))
    drawCircle(color = cutout, radius = 2.5f, center = Offset(21f, 37f))

    // Rear Battery / Sensor Barrel
    drawRoundRect(
        color = shell,
        topLeft = Offset(36f, 35f),
        size = Size(8f, 15f),
        cornerRadius = CornerRadius(4f, 4f),
    )
}

/**
 * 7. NECKBAND (OnePlus Bullets Wireless, realme Buds Wireless):
 * U-shaped flexible collar band with weighted control endpoints, wires, and magnetic buds.
 */
private fun DrawScope.drawNeckband(shell: Color, accent: Color, cutout: Color) {
    // U-shaped flexible collar band
    val collar = Path().apply {
        moveTo(14f, 42f)
        cubicTo(12f, 26f, 20f, 14f, 32f, 14f)
        cubicTo(44f, 14f, 52f, 26f, 50f, 42f)
    }
    drawPath(collar, color = accent, style = Stroke(width = 3f, cap = StrokeCap.Round))

    // Weighted control module on left
    drawRoundRect(
        color = shell,
        topLeft = Offset(11f, 38f),
        size = Size(6f, 15f),
        cornerRadius = CornerRadius(3f, 3f),
    )
    // Weighted control module on right
    drawRoundRect(
        color = shell,
        topLeft = Offset(47f, 38f),
        size = Size(6f, 15f),
        cornerRadius = CornerRadius(3f, 3f),
    )

    // Button cutouts on left control module
    drawCircle(color = cutout, radius = 1f, center = Offset(14f, 42f))
    drawCircle(color = cutout, radius = 1f, center = Offset(14f, 45.5f))
    drawCircle(color = cutout, radius = 1f, center = Offset(14f, 49f))

    // Thin curved wires sweeping up to magnetic buds
    val wireLeft = Path().apply {
        moveTo(14f, 53f)
        cubicTo(15f, 58f, 24f, 58f, 27f, 50f)
    }
    drawPath(wireLeft, color = accent, style = Stroke(width = 1.25f))

    val wireRight = Path().apply {
        moveTo(50f, 53f)
        cubicTo(49f, 58f, 40f, 58f, 37f, 50f)
    }
    drawPath(wireRight, color = accent, style = Stroke(width = 1.25f))

    // Paired magnetic buds snapped together at center bottom
    drawRoundRect(
        color = shell,
        topLeft = Offset(26f, 46f),
        size = Size(5.5f, 4.5f),
        cornerRadius = CornerRadius(1.5f, 1.5f),
    )
    drawRoundRect(
        color = shell,
        topLeft = Offset(32.5f, 46f),
        size = Size(5.5f, 4.5f),
        cornerRadius = CornerRadius(1.5f, 1.5f),
    )
}

/**
 * 8. BONE_CONDUCTION (Shokz OpenRun Pro):
 * Horizontal wrap-around headband with dual temporal bone transducer pads.
 */
private fun DrawScope.drawBoneConduction(shell: Color, accent: Color, cutout: Color) {
    // Wrap-around neckband arch
    val wrap = Path().apply {
        moveTo(16f, 38f)
        cubicTo(12f, 22f, 22f, 15f, 32f, 15f)
        cubicTo(42f, 15f, 52f, 22f, 48f, 38f)
    }
    drawPath(wrap, color = accent, style = Stroke(width = 3.5f, cap = StrokeCap.Round))

    // Ear loops curving forward over the ears
    val leftLoop = Path().apply {
        moveTo(16f, 38f)
        cubicTo(14f, 45f, 21f, 47f, 24f, 41f)
    }
    drawPath(leftLoop, color = accent, style = Stroke(width = 3f, cap = StrokeCap.Round))

    val rightLoop = Path().apply {
        moveTo(48f, 38f)
        cubicTo(50f, 45f, 43f, 47f, 40f, 41f)
    }
    drawPath(rightLoop, color = accent, style = Stroke(width = 3f, cap = StrokeCap.Round))

    // Temporal Transducer Pads (resting in front of the ears)
    drawRoundRect(
        color = shell,
        topLeft = Offset(22f, 38f),
        size = Size(5f, 8f),
        cornerRadius = CornerRadius(2.5f, 2.5f),
    )
    drawCircle(color = cutout, radius = 1.2f, center = Offset(24.5f, 42f))

    drawRoundRect(
        color = shell,
        topLeft = Offset(37f, 38f),
        size = Size(5f, 8f),
        cornerRadius = CornerRadius(2.5f, 2.5f),
    )
    drawCircle(color = cutout, radius = 1.2f, center = Offset(39.5f, 42f))
}

/**
 * 9 & 10. HEADSET_OVER_ON_EAR & HEADSET_OPEN_BACK:
 * Padded headband arch, telescoping yokes, angled earcups, plush cushions, and open-back louvers.
 */
private fun DrawScope.drawHeadset(
    isOpenBack: Boolean,
    shell: Color,
    accent: Color,
    cutout: Color,
) {
    // Outer Headband Arch
    val headband = Path().apply {
        moveTo(13f, 36f)
        cubicTo(12f, 18f, 20f, 9f, 32f, 9f)
        cubicTo(44f, 9f, 52f, 18f, 51f, 36f)
    }
    drawPath(headband, color = shell, style = Stroke(width = 4f, cap = StrokeCap.Round))

    // Inner Padded Headband Cushion
    val cushion = Path().apply {
        moveTo(19f, 24f)
        cubicTo(22f, 15f, 27f, 12f, 32f, 12f)
        cubicTo(37f, 12f, 42f, 15f, 45f, 24f)
    }
    drawPath(cushion, color = accent, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

    // Left Earcup Cushion (Inner layer)
    drawRoundRect(
        color = accent,
        topLeft = Offset(9f, 33f),
        size = Size(8f, 22f),
        cornerRadius = CornerRadius(4f, 4f),
    )
    // Left Earcup Shell (Outer shell)
    drawRoundRect(
        color = shell,
        topLeft = Offset(12f, 35f),
        size = Size(7f, 18f),
        cornerRadius = CornerRadius(3.5f, 3.5f),
    )

    // Right Earcup Cushion (Inner layer)
    drawRoundRect(
        color = accent,
        topLeft = Offset(47f, 33f),
        size = Size(8f, 22f),
        cornerRadius = CornerRadius(4f, 4f),
    )
    // Right Earcup Shell (Outer shell)
    drawRoundRect(
        color = shell,
        topLeft = Offset(45f, 35f),
        size = Size(7f, 18f),
        cornerRadius = CornerRadius(3.5f, 3.5f),
    )

    // Open-Back Acoustic Grille Louvers
    if (isOpenBack) {
        // Diagonal louvers across outer shell on Left and Right
        for (offsetY in listOf(38f, 41f, 44f, 47f)) {
            drawLine(
                color = cutout,
                start = Offset(13.5f, offsetY),
                end = Offset(17.5f, offsetY + 2f),
                strokeWidth = 1.25f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = cutout,
                start = Offset(46.5f, offsetY),
                end = Offset(50.5f, offsetY + 2f),
                strokeWidth = 1.25f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Matching Charging Cases for TWS form factors.
 */
private fun DrawScope.drawChargingCase(
    formFactor: AudioFormFactor,
    shell: Color,
    accent: Color,
    cutout: Color,
) {
    when (formFactor) {
        AudioFormFactor.STEM_IN_EAR, AudioFormFactor.STEM_HALF_IN_EAR -> {
            // Wide rounded-rectangle pebble case (e.g. realme Buds T300 / AirPods Pro)
            drawRoundRect(
                color = shell,
                topLeft = Offset(10f, 18f),
                size = Size(44f, 32f),
                cornerRadius = CornerRadius(12f, 12f),
            )
            // Horizontal lid split seam
            drawLine(
                color = accent,
                start = Offset(10.5f, 29f),
                end = Offset(53.5f, 29f),
                strokeWidth = 1.5f,
            )
            // Center finger-indent notch pill
            drawRoundRect(
                color = cutout,
                topLeft = Offset(28f, 28f),
                size = Size(8f, 2f),
                cornerRadius = CornerRadius(1f, 1f),
            )
            // Front Status LED Dot
            drawCircle(
                color = Color(0xFF22E55E),
                radius = 1.5f,
                center = Offset(32f, 36f),
            )
        }
        AudioFormFactor.BUD_BEAN_IN_EAR, AudioFormFactor.BUD_OPEN_RING -> {
            // Square rounded clamshell jewelry-box case (e.g. Galaxy Buds / Sony WF-1000XM5)
            drawRoundRect(
                color = shell,
                topLeft = Offset(14f, 16f),
                size = Size(36f, 36f),
                cornerRadius = CornerRadius(10f, 10f),
            )
            // Front lid seam
            drawLine(
                color = accent,
                start = Offset(14.5f, 28f),
                end = Offset(49.5f, 28f),
                strokeWidth = 1.5f,
            )
            // Wide front lip notch
            drawRoundRect(
                color = cutout,
                topLeft = Offset(26f, 27f),
                size = Size(12f, 2f),
                cornerRadius = CornerRadius(1f, 1f),
            )
            // LED Dot
            drawCircle(
                color = Color(0xFF22E55E),
                radius = 1.5f,
                center = Offset(32f, 37f),
            )
        }
        AudioFormFactor.OPEN_EAR_HOOK -> {
            // Large rounded clamshell sports case (e.g. Powerbeats Pro)
            drawRoundRect(
                color = shell,
                topLeft = Offset(8f, 14f),
                size = Size(48f, 40f),
                cornerRadius = CornerRadius(14f, 14f),
            )
            drawLine(
                color = accent,
                start = Offset(8.5f, 30f),
                end = Offset(55.5f, 30f),
                strokeWidth = 1.75f,
            )
            drawCircle(
                color = Color(0xFF22E55E),
                radius = 1.75f,
                center = Offset(32f, 38f),
            )
        }
        AudioFormFactor.OPEN_EAR_CLIP -> {
            // Pebble clip cradle case
            drawRoundRect(
                color = shell,
                topLeft = Offset(12f, 16f),
                size = Size(40f, 36f),
                cornerRadius = CornerRadius(12f, 12f),
            )
            drawLine(
                color = accent,
                start = Offset(12.5f, 28f),
                end = Offset(51.5f, 28f),
                strokeWidth = 1.5f,
            )
            drawCircle(
                color = Color(0xFF22E55E),
                radius = 1.5f,
                center = Offset(32f, 36f),
            )
        }
        else -> {
            // Non-case devices (Neckband, Headsets, Bone Conduction)
        }
    }
}

