package paige.navic.util.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The BencIT hub's colour themes (hub/ui.css in bencit-alati, Svirka web src/lib/palettes.ts):
 * accent and accent text, light then dark. The first is the default.
 */
private val HUB_ACCENTS = mapOf(
	"ocean" to listOf(0xFF0369A1, 0xFF075985, 0xFF0284C7, 0xFF7DD3FC),
	"indigo" to listOf(0xFF4F46E5, 0xFF4338CA, 0xFF5850EC, 0xFFA5B4FC),
	"forest" to listOf(0xFF047857, 0xFF065F46, 0xFF059669, 0xFF6EE7B7),
	"sunset" to listOf(0xFFC2410C, 0xFF9A3412, 0xFFEA580C, 0xFFFDBA74),
	"rose" to listOf(0xFFBE123C, 0xFF9F1239, 0xFFE11D48, 0xFFFDA4AF),
	"grape" to listOf(0xFF7E22CE, 0xFF6B21A8, 0xFF9333EA, 0xFFD8B4FE),
)

/**
 * Svirka web palette (src/app/globals.css, shadcn "neutral" surfaces) with the BencIT hub colour theme's accent,
 * like the web app: [palette] comes from the hub through Svirka Connect.
 */
fun svirkaColorScheme(isDark: Boolean, palette: String = "ocean"): ColorScheme {
	val (lightAccent, lightText, darkAccent, darkText) = (HUB_ACCENTS[palette] ?: HUB_ACCENTS.getValue("ocean")).map { Color(it) }
	val base = neutralScheme(isDark)
	val accent = if (isDark) darkAccent else lightAccent
	val soft = lerp(base.background, accent, if (isDark) .22f else .12f)
	return base.copy(
		primary = accent,
		onPrimary = Color.White,
		primaryContainer = soft,
		onPrimaryContainer = if (isDark) darkText else lightText,
		secondaryContainer = soft,
		onSecondaryContainer = if (isDark) darkText else lightText,
		surfaceTint = accent,
		inversePrimary = if (isDark) lightAccent else darkText,
	)
}

private fun neutralScheme(isDark: Boolean): ColorScheme = if (isDark) darkColorScheme(
	primary = Color(0xFFE5E5E5),
	onPrimary = Color(0xFF171717),
	primaryContainer = Color(0xFF262626),
	onPrimaryContainer = Color(0xFFFAFAFA),
	inversePrimary = Color(0xFF171717),
	secondary = Color(0xFFA1A1A1),
	onSecondary = Color(0xFF171717),
	secondaryContainer = Color(0xFF262626),
	onSecondaryContainer = Color(0xFFFAFAFA),
	tertiary = Color(0xFFA1A1A1),
	onTertiary = Color(0xFF171717),
	tertiaryContainer = Color(0xFF262626),
	onTertiaryContainer = Color(0xFFFAFAFA),
	background = Color(0xFF0A0A0A),
	onBackground = Color(0xFFFAFAFA),
	surface = Color(0xFF0A0A0A),
	onSurface = Color(0xFFFAFAFA),
	surfaceVariant = Color(0xFF262626),
	onSurfaceVariant = Color(0xFFA1A1A1),
	surfaceTint = Color(0xFFE5E5E5),
	inverseSurface = Color(0xFFFAFAFA),
	inverseOnSurface = Color(0xFF171717),
	outline = Color(0xFF404040),
	outlineVariant = Color(0xFF262626),
	surfaceBright = Color(0xFF262626),
	surfaceDim = Color(0xFF0A0A0A),
	surfaceContainerLowest = Color(0xFF0A0A0A),
	surfaceContainerLow = Color(0xFF121212),
	surfaceContainer = Color(0xFF171717),
	surfaceContainerHigh = Color(0xFF1F1F1F),
	surfaceContainerHighest = Color(0xFF262626),
) else lightColorScheme(
	primary = Color(0xFF171717),
	onPrimary = Color(0xFFFAFAFA),
	primaryContainer = Color(0xFFF5F5F5),
	onPrimaryContainer = Color(0xFF171717),
	inversePrimary = Color(0xFFE5E5E5),
	secondary = Color(0xFF737373),
	onSecondary = Color(0xFFFAFAFA),
	secondaryContainer = Color(0xFFF5F5F5),
	onSecondaryContainer = Color(0xFF171717),
	tertiary = Color(0xFF737373),
	onTertiary = Color(0xFFFAFAFA),
	tertiaryContainer = Color(0xFFF5F5F5),
	onTertiaryContainer = Color(0xFF171717),
	background = Color(0xFFFFFFFF),
	onBackground = Color(0xFF0A0A0A),
	surface = Color(0xFFFFFFFF),
	onSurface = Color(0xFF0A0A0A),
	surfaceVariant = Color(0xFFF5F5F5),
	onSurfaceVariant = Color(0xFF737373),
	surfaceTint = Color(0xFF171717),
	inverseSurface = Color(0xFF171717),
	inverseOnSurface = Color(0xFFFAFAFA),
	outline = Color(0xFFD4D4D4),
	outlineVariant = Color(0xFFE5E5E5),
	surfaceBright = Color(0xFFFFFFFF),
	surfaceDim = Color(0xFFE5E5E5),
	surfaceContainerLowest = Color(0xFFFFFFFF),
	surfaceContainerLow = Color(0xFFFAFAFA),
	surfaceContainer = Color(0xFFF5F5F5),
	surfaceContainerHigh = Color(0xFFEEEEEE),
	surfaceContainerHighest = Color(0xFFE5E5E5),
)
