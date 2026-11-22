package com.example.ui.editor

import android.util.DisplayMetrics
import com.example.data.mse.MseStyleSpec
import com.example.data.settings.PhysicalCardProfile

private const val MM_PER_INCH = 25.4f

data class PhysicalCardDimensionsPx(
    val widthPx: Float,
    val heightPx: Float,
    val widthMm: Float,
    val heightMm: Float,
    val xdpi: Float,
    val ydpi: Float,
    val calibrationScale: Float,
)

/**
 * Computes exact 1:1 IRL hardware screen pixels using `DisplayMetrics.xdpi` / `ydpi`,
 * active `.mse-style` dimensions + DPI (or real-world physical card profile), and the
 * user's persisted Physical Card Calibration multiplier.
 */
object PhysicalScaleMath {

    fun computeIrlCardPixels(
        displayMetrics: DisplayMetrics,
        styleSpec: MseStyleSpec,
        profile: PhysicalCardProfile,
        calibrationScale: Float,
    ): PhysicalCardDimensionsPx {
        // Guard against anomalous emulator DisplayMetrics values (e.g. xdpi == 0)
        val safeXdpi = if (displayMetrics.xdpi > 40f) displayMetrics.xdpi else displayMetrics.densityDpi.toFloat().coerceAtLeast(160f)
        val safeYdpi = if (displayMetrics.ydpi > 40f) displayMetrics.ydpi else displayMetrics.densityDpi.toFloat().coerceAtLeast(160f)

        val widthInches: Float
        val heightInches: Float

        if (profile == PhysicalCardProfile.TEMPLATE_DPI && styleSpec.cardDpi > 0 && styleSpec.cardWidthPx > 0 && styleSpec.cardHeightPx > 0) {
            widthInches = styleSpec.cardWidthPx.toFloat() / styleSpec.cardDpi.toFloat()
            heightInches = styleSpec.cardHeightPx.toFloat() / styleSpec.cardDpi.toFloat()
        } else {
            widthInches = profile.widthMm / MM_PER_INCH
            heightInches = profile.heightMm / MM_PER_INCH
        }

        val widthMm = widthInches * MM_PER_INCH
        val heightMm = heightInches * MM_PER_INCH

        return PhysicalCardDimensionsPx(
            widthPx = widthInches * safeXdpi * calibrationScale,
            heightPx = heightInches * safeYdpi * calibrationScale,
            widthMm = widthMm,
            heightMm = heightMm,
            xdpi = safeXdpi,
            ydpi = safeYdpi,
            calibrationScale = calibrationScale,
        )
    }
}
