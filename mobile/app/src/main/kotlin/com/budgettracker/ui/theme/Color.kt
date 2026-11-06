package com.budgettracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The palette.
 *
 * One warm accent carries every action, a near-black card carries the figure
 * that matters most on a screen, and everything else is a quiet grey so the
 * two have something to stand out against.
 *
 * `positive` and `negative` are the money colours: they carry the sign of a
 * figure and are used for nothing else, so a colour is always a statement
 * about direction.
 */
internal object Palette {

    /** The accent. Spending, the selected tab, the primary button. */
    val Coral = Color(0xFFFF6B52)
    val CoralDark = Color(0xFFFF8471)
    val CoralSoftLight = Color(0xFFFFE8E2)
    val CoralSoftDark = Color(0xFF3E201A)

    /**
     * The hero card.
     *
     * In the dark theme it is near-black rather than true black, so the card
     * still lifts off the background.
     *
     * In the light theme it is a warm off-white instead. A near-black panel
     * on a white page is a hole punched in it — the shadow has nothing to
     * fall on and the glow in its corner is the only thing with any light in
     * it. Warm against the page's cool grey is what makes it the one card
     * that is not like the others, which is the whole job the near-black
     * does in the dark.
     */
    val InkOnLight = Color(0xFFFFF1EC)
    val InkOnDark = Color(0xFF000105)
    val InkRaisedLight = Color(0xFFFFE2D9)
    val InkRaisedDark = Color(0xFF191B22)

    /** What sits on the hero card: the figure, and the words around it. */
    val OnInkLight = Color(0xFF13141A)
    val InkMutedLight = Color(0xFF656876)
    val InkMutedDark = Color(0xFF9A9DA8)

    val Positive = Color(0xFF12A05F)
    val PositiveContainerLight = Color(0xFFDDF4E8)
    val PositiveDark = Color(0xFF53D497)
    val PositiveContainerDark = Color(0xFF123529)

    val Negative = Color(0xFFE5533F)
    val NegativeContainerLight = Color(0xFFFFE6E1)
    val NegativeDark = Color(0xFFFF8E7C)
    val NegativeContainerDark = Color(0xFF42201A)

    val Warning = Color(0xFFD08A0B)
    val WarningContainerLight = Color(0xFFFCEFD6)
    val WarningContainerDark = Color(0xFF3B2D12)
    val WarningDark = Color(0xFFEFBB58)

    /**
     * The colours a category card is painted in, in the order they are handed
     * out. Six hues that stay apart from each other at a glance and all carry
     * white text at the weight a filled card needs.
     */
    val CategoryBlue = Color(0xFF2E8CF0)
    val CategoryCoral = Color(0xFFF2635C)
    val CategoryGreen = Color(0xFF14A05E)
    val CategoryAmber = Color(0xFFEFA524)
    val CategoryViolet = Color(0xFF8B5CF6)
    val CategoryPink = Color(0xFFEC6FA6)

    val Categories = listOf(
        CategoryBlue,
        CategoryCoral,
        CategoryGreen,
        CategoryAmber,
        CategoryViolet,
        CategoryPink,
    )
}

/** Money and status colours that Material's scheme has no slot for. */
@Immutable
data class MoneyColors(
    val positive: Color,
    val positiveContainer: Color,
    val negative: Color,
    val negativeContainer: Color,
    val warning: Color,
    val warningContainer: Color,
)

/**
 * The design's own colours: the hero card, the accent, and the category hues.
 *
 * These sit alongside Material's scheme rather than inside it because the
 * hero card is a fixed dark surface in both themes — it is not `surface` at
 * some tonal elevation, and treating it as one makes it disappear in the dark.
 */
@Immutable
data class AccentColors(
    val dark: Boolean,
    val ink: Color,
    val onInk: Color,
    val inkMuted: Color,
    val inkRaised: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val track: Color,
    val categories: List<Color>,
) {
    /** The hue a category takes when it has not chosen one of its own. */
    fun category(index: Int): Color = categories[index.mod(categories.size)]

    /**
     * The bead on a line drawn across the hero card — the dot on the end of
     * a sparkline, and anything else punched out of it.
     *
     * It is always the lighter of the card and the text on it: white on the
     * near-black card, the card's own off-white on the light one. Taken from
     * [onInk] alone it would be a black blot in the light theme, which is
     * the one thing a highlight cannot be.
     */
    val inkBead: Color get() = if (dark) onInk else ink
}

internal val LightMoneyColors = MoneyColors(
    positive = Palette.Positive,
    positiveContainer = Palette.PositiveContainerLight,
    negative = Palette.Negative,
    negativeContainer = Palette.NegativeContainerLight,
    warning = Palette.Warning,
    warningContainer = Palette.WarningContainerLight,
)

internal val DarkMoneyColors = MoneyColors(
    positive = Palette.PositiveDark,
    positiveContainer = Palette.PositiveContainerDark,
    negative = Palette.NegativeDark,
    negativeContainer = Palette.NegativeContainerDark,
    warning = Palette.WarningDark,
    warningContainer = Palette.WarningContainerDark,
)

internal val LightAccentColors = AccentColors(
    dark = false,
    ink = Palette.InkOnLight,
    onInk = Palette.OnInkLight,
    inkMuted = Palette.InkMutedLight,
    inkRaised = Palette.InkRaisedLight,
    accent = Palette.Coral,
    onAccent = Color.White,
    accentSoft = Palette.CoralSoftLight,
    onAccentSoft = Color(0xFF8C2A19),
    track = Color(0xFFECEDF3),
    categories = Palette.Categories,
)

internal val DarkAccentColors = AccentColors(
    dark = true,
    ink = Palette.InkOnDark,
    onInk = Color.White,
    inkMuted = Palette.InkMutedDark,
    inkRaised = Palette.InkRaisedDark,
    accent = Palette.CoralDark,
    onAccent = Color(0xFF3B140C),
    accentSoft = Palette.CoralSoftDark,
    onAccentSoft = Color(0xFFFFD6CC),
    track = Color(0xFF262A35),
    categories = Palette.Categories,
)

internal val LightScheme: ColorScheme = lightColorScheme(
    primary = Palette.Coral,
    onPrimary = Color.White,
    primaryContainer = Palette.CoralSoftLight,
    onPrimaryContainer = Color(0xFF8C2A19),
    secondary = Color(0xFF585C6B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E9F0),
    onSecondaryContainer = Color(0xFF1B1D26),
    tertiary = Palette.InkOnLight,
    onTertiary = Color.White,
    background = Color(0xFFF2F3F8),
    onBackground = Color(0xFF13141A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF13141A),
    surfaceVariant = Color(0xFFF4F5F9),
    onSurfaceVariant = Color(0xFF86899A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFD),
    surfaceContainer = Color(0xFFF4F5F9),
    surfaceContainerHigh = Color(0xFFECEDF3),
    surfaceContainerHighest = Color(0xFFE4E6EE),
    outline = Color(0xFFD5D7E1),
    outlineVariant = Color(0xFFEDEEF4),
    error = Palette.Negative,
    onError = Color.White,
    errorContainer = Palette.NegativeContainerLight,
    onErrorContainer = Color(0xFF68190E),
    scrim = Color(0xFF0B0C10),
)

internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = Palette.CoralDark,
    onPrimary = Color(0xFF3B140C),
    primaryContainer = Palette.CoralSoftDark,
    onPrimaryContainer = Color(0xFFFFD6CC),
    secondary = Color(0xFFB9BDCC),
    onSecondary = Color(0xFF22252F),
    secondaryContainer = Color(0xFF2A2E39),
    onSecondaryContainer = Color(0xFFE0E3EC),
    tertiary = Color(0xFFE6E8F0),
    onTertiary = Color(0xFF13141A),
    background = Color(0xFF0B0C11),
    onBackground = Color(0xFFF3F4F8),
    surface = Color(0xFF15171E),
    onSurface = Color(0xFFF3F4F8),
    surfaceVariant = Color(0xFF1E212A),
    onSurfaceVariant = Color(0xFF9094A3),
    surfaceContainerLowest = Color(0xFF0E1015),
    surfaceContainerLow = Color(0xFF14161C),
    surfaceContainer = Color(0xFF1A1D25),
    surfaceContainerHigh = Color(0xFF22252F),
    surfaceContainerHighest = Color(0xFF2A2E39),
    outline = Color(0xFF3A3E4A),
    outlineVariant = Color(0xFF252933),
    error = Palette.NegativeDark,
    onError = Color(0xFF42201A),
    errorContainer = Palette.NegativeContainerDark,
    onErrorContainer = Color(0xFFFFDAD3),
    scrim = Color(0xFF000000),
)
