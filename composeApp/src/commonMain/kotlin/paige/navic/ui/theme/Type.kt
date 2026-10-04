package paige.navic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.geist
import navic.composeapp.generated.resources.google_sans
import org.jetbrains.compose.resources.Font
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.settings.FontOption

private val defaultTypography = Typography()

@Composable
fun googleSans(
	grade: Int = 0,
	width: Float = 100f,
	round: Float = 0f
): FontFamily {
	val font = Font(
		Res.font.google_sans,
		variationSettings = FontVariation.Settings(
			FontVariation.grade(grade),
			FontVariation.width(width),
			FontVariation.Setting("ROND", round)
		)
	)
	return remember { FontFamily(font) }
}

/** Geist (OFL) — Svirka web's typeface. Variable font: one instance per weight used by M3 styles. */
@Composable
fun geist(): FontFamily {
	val fonts = listOf(300, 400, 500, 600, 700, 800).map { w ->
		Font(
			Res.font.geist,
			weight = FontWeight(w),
			variationSettings = FontVariation.Settings(FontVariation.weight(w))
		)
	}
	return remember { FontFamily(fonts) }
}

@Composable
fun defaultFont(
	grade: Int = 0,
	width: Float = 100f,
	round: Float = 0f
): FontFamily {
	val preferenceManager = koinInject<PreferenceManager>()
	val googleSans = googleSans(grade, width, round)
	val geist = geist()
	return remember(preferenceManager.font, preferenceManager.fontPath) {
		when (preferenceManager.font) {
			FontOption.Geist -> geist
			FontOption.System -> FontFamily.Default
			FontOption.GoogleSans -> googleSans
			FontOption.Custom -> FontFamily.Default
		}
	}
}

@Composable
fun typography(): Typography {
	val fontFamily = defaultFont()
	return Typography(
		displayLarge = defaultTypography.displayLarge.copy(fontFamily = fontFamily),
		displayLargeEmphasized = defaultTypography.displayLargeEmphasized.copy(fontFamily = fontFamily),
		displayMedium = defaultTypography.displayMedium.copy(fontFamily = fontFamily),
		displayMediumEmphasized = defaultTypography.displayMediumEmphasized.copy(fontFamily = fontFamily),
		displaySmall = defaultTypography.displaySmall.copy(fontFamily = fontFamily),
		displaySmallEmphasized = defaultTypography.displaySmallEmphasized.copy(fontFamily = fontFamily),

		headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = fontFamily),
		headlineLargeEmphasized = defaultTypography.headlineLargeEmphasized.copy(fontFamily = fontFamily),
		headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = fontFamily),
		headlineMediumEmphasized = defaultTypography.headlineMediumEmphasized.copy(fontFamily = fontFamily),
		headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = fontFamily),
		headlineSmallEmphasized = defaultTypography.headlineSmallEmphasized.copy(fontFamily = fontFamily),

		titleLarge = defaultTypography.titleLarge.copy(fontFamily = fontFamily),
		titleLargeEmphasized = defaultTypography.titleLargeEmphasized.copy(fontFamily = fontFamily),
		titleMedium = defaultTypography.titleMedium.copy(fontFamily = fontFamily),
		titleMediumEmphasized = defaultTypography.titleMediumEmphasized.copy(fontFamily = fontFamily),
		titleSmall = defaultTypography.titleSmall.copy(fontFamily = fontFamily),
		titleSmallEmphasized = defaultTypography.titleSmallEmphasized.copy(fontFamily = fontFamily),

		bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = fontFamily),
		bodyLargeEmphasized = defaultTypography.bodyLargeEmphasized.copy(fontFamily = fontFamily),
		bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = fontFamily),
		bodyMediumEmphasized = defaultTypography.bodyMediumEmphasized.copy(fontFamily = fontFamily),
		bodySmall = defaultTypography.bodySmall.copy(fontFamily = fontFamily),
		bodySmallEmphasized = defaultTypography.bodySmallEmphasized.copy(fontFamily = fontFamily),

		labelLarge = defaultTypography.labelLarge.copy(fontFamily = fontFamily),
		labelLargeEmphasized = defaultTypography.labelLargeEmphasized.copy(fontFamily = fontFamily),
		labelMedium = defaultTypography.labelMedium.copy(fontFamily = fontFamily),
		labelMediumEmphasized = defaultTypography.labelMediumEmphasized.copy(fontFamily = fontFamily),
		labelSmall = defaultTypography.labelSmall.copy(fontFamily = fontFamily),
		labelSmallEmphasized = defaultTypography.labelSmallEmphasized.copy(fontFamily = fontFamily)
	)
}
