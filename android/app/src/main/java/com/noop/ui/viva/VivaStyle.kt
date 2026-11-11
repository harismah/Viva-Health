package com.noop.ui.viva

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Viva visual tokens: the Android twin of Packages/StrandDesign/.../VivaStyle.swift.
//
// The dark, flat look the app is being rebuilt to: a cool blue-grey canvas that darkens down the
// page, borderless cards, white type, and one colour per pillar. Every value here mirrors the
// Swift file one for one, so the two phones render the same screen from the same numbers. Change
// a token in both places or not at all. The look is dark-only, so nothing reads the app theme.

object VivaStyle {
    // Surfaces
    val canvasTop = Color(0xFF20262C)
    val canvasBottom = Color(0xFF14171B)
    val card = Color(0xFF24272B)
    val inset = Color(0xFF1A1D21)
    val chip = Color(0xFF2B3137)
    val chipActive = Color(0xFF3D4349)
    val callout = Color(0xFF2A1D33)
    val tabBar = Color(0xFF1E2329)
    val coachButton = Color(0xFF26283A)
    val divider = Color(0xFF2E3237)

    // Text
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFF9AA4AC)
    val textTertiary = Color(0xFF6B7177)

    // Pillars
    val sleep = Color(0xFF7096B6)
    val strain = Color(0xFF0088E3)
    val recoveryGreen = Color(0xFF16EC06)
    val recoveryYellow = Color(0xFFFFDE00)
    val recoveryRed = Color(0xFFFF0026)
    val recoveryCalibrating = Color(0xFF909297)
    val ringTrack = Color(0xFF2F343A)

    // Sleep stages
    val stageAwake = Color(0xFFC9CDD2)
    val stageLight = Color(0xFF9B9BF0)
    val stageDeep = Color(0xFFF88BF2)
    val stageREM = Color(0xFFA44BE8)

    // Accents and status
    val magenta = Color(0xFFCF42DD)
    val link = Color(0xFF4C9FEA)
    val positive = Color(0xFF01ED9A)
    val warning = Color(0xFFF0A020)
    val critical = Color(0xFFFF0026)
    val coachRing = Color(0xFF5B6CFF)
    val chipGradientStart = Color(0xFFF9837C)
    val chipGradientEnd = Color(0xFFA231F0)

    // Metrics
    val cardRadius: Dp = 14.dp
    val tileRadius: Dp = 10.dp
    val pagePadding: Dp = 16.dp
    val cardPadding: Dp = 16.dp
    val cardGap: Dp = 12.dp
    val sectionGap: Dp = 28.dp

    /**
     * Recovery is the one pillar whose colour carries its value: green from 67, yellow from 34,
     * red below. A missing score reads as neutral grey so an empty ring never looks like a bad one.
     */
    fun recoveryColor(percent: Double?): Color {
        if (percent == null) return textSecondary
        if (percent >= 67) return recoveryGreen
        if (percent >= 34) return recoveryYellow
        return recoveryRed
    }

    /** The page background: lighter blue-grey behind the top chrome, settling to near-black. */
    val canvasBrush: Brush
        get() = Brush.verticalGradient(
            0f to canvasTop,
            0.42f to canvasBottom,
            1f to canvasBottom,
        )
}

/**
 * Type scale for the Viva look: tight uppercase card titles, mixed-case page sections, and bold
 * condensed numerals. System faces only, as on iPhone; the numerals use the device's condensed
 * sans (Roboto Condensed on most phones) with tabular figures.
 */
@OptIn(ExperimentalTextApi::class)
object VivaFont {
    private val condensed: FontFamily by lazy {
        val name = DeviceFontFamilyName("sans-serif-condensed")
        FontFamily(
            Font(name, FontWeight.Medium),
            Font(name, FontWeight.SemiBold),
            Font(name, FontWeight.Bold),
        )
    }

    private fun numeral(size: Int, weight: FontWeight = FontWeight.Bold) = TextStyle(
        fontFamily = condensed,
        fontSize = size.sp,
        fontWeight = weight,
        fontFeatureSettings = "tnum",
    )

    private fun text(size: Int, weight: FontWeight, tracking: TextUnit = 0.sp) = TextStyle(
        fontSize = size.sp,
        fontWeight = weight,
        letterSpacing = tracking,
    )

    val cardTitleTracking = 1.2.sp
    val labelTracking = 1.0.sp
    val wordmarkTracking = 3.sp

    val cardTitle = text(12, FontWeight.Bold, cardTitleTracking)
    val sectionTitle = text(22, FontWeight.SemiBold)
    val body = text(14, FontWeight.Normal)
    val bodyStrong = text(15, FontWeight.SemiBold)
    val caption = text(12, FontWeight.Normal)
    val label = text(10, FontWeight.Bold, labelTracking)
    val unit = text(12, FontWeight.SemiBold)
    val wordmark = text(15, FontWeight.Light, wordmarkTracking)

    /** Small uppercase action text (`EDIT ALARM`, link labels, chips). */
    val action = text(11, FontWeight.Bold, 1.4.sp)

    /** Centred uppercase title on a detail top bar, and the label inside a large ring. */
    val barTitle = text(13, FontWeight.Bold, 1.6.sp)

    val valueLarge: TextStyle by lazy { numeral(28) }
    val valueMedium: TextStyle by lazy { numeral(22) }
    val valueSmall: TextStyle by lazy { numeral(16, FontWeight.SemiBold) }
    val baseline: TextStyle by lazy { numeral(10, FontWeight.Medium) }
    val ringSmall: TextStyle by lazy { numeral(29) }
    val ringLarge: TextStyle by lazy { numeral(72) }

    fun number(size: Int, weight: FontWeight = FontWeight.Bold): TextStyle = numeral(size, weight)
}

/** Fills its bounds with the Viva canvas and lays [content] over it. */
@Composable
fun VivaCanvas(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.fillMaxSize().background(VivaStyle.canvasBrush), content = content)
}

/** Small uppercase tracked title used at the top of every card. */
@Composable
fun VivaCardTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = VivaStyle.textPrimary,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = VivaFont.cardTitle,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Tiny uppercase tracked caption under values and on chart legends. */
@Composable
fun VivaLabel(text: String, modifier: Modifier = Modifier, color: Color = VivaStyle.textSecondary) {
    Text(text = text.uppercase(), modifier = modifier, style = VivaFont.label, color = color)
}

/**
 * Press feedback for tappable cards and rows: a slight dim, no scale and no ripple, matching the
 * iPhone `VivaPressStyle`.
 */
fun Modifier.vivaPressable(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    this
        .alpha(if (pressed) 0.7f else 1f)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
