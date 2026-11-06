package com.budgettracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Categories and accounts carry a colour chosen by the account holder, as a
 * `#RRGGBB` string. A value the server never set, or one that is not a colour
 * at all, falls back to the caller's default rather than painting black.
 */
fun hexColor(value: String?, fallback: Color): Color {
    val cleaned = value?.trim()?.removePrefix("#") ?: return fallback

    val hex = when (cleaned.length) {
        3 -> cleaned.map { "$it$it" }.joinToString("")
        6, 8 -> cleaned
        else -> return fallback
    }

    val parsed = hex.toLongOrNull(radix = 16) ?: return fallback

    return when (hex.length) {
        // `#RRGGBBAA`, the CSS order, moved into Compose's `AARRGGBB`.
        8 -> Color(((parsed and 0xFFL) shl 24) or (parsed shr 8))
        else -> Color(0xFF000000L or parsed)
    }
}
