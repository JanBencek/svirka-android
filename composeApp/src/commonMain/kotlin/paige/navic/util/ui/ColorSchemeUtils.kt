package paige.navic.util.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.kmpalette.rememberDominantColorState
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.utils.ColorUtils.calculateLuminance
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.settings.ThemeMode
import paige.navic.shared.MediaPlayerViewModel
import coil3.compose.LocalPlatformContext as LocalCoilPlatformContext

/**
 * Loads [coverArtId] through Coil and returns its dominant colour, or null until the first one is extracted.
 * Keeps the previous colour while the next cover loads, so switching songs doesn't flash.
 */
@Composable
private fun rememberDominantCoverColor(coverArtId: String?): Color? {
	val sessionManager = koinInject<SessionManager>()
	val coverArtUri = remember(coverArtId) {
		coverArtId?.let { sessionManager.getCoverArtUrl(it) }
	}

	val coilPlatformContext = LocalCoilPlatformContext.current
	val loader = SingletonImageLoader.get(coilPlatformContext)
	val model = remember(coverArtUri) {
		ImageRequest.Builder(coilPlatformContext)
			.data(coverArtUri)
			.memoryCacheKey(coverArtId)
			.diskCacheKey(coverArtId)
			.diskCachePolicy(CachePolicy.ENABLED)
			.memoryCachePolicy(CachePolicy.ENABLED)
			.build()
	}
	val dominantColorState = rememberDominantColorState(cacheSize = 4)
	var color by remember { mutableStateOf<Color?>(null) }

	LaunchedEffect(model) {
		val result = loader.execute(model)
		result.image?.toImageBitmap()?.let { imageBitmap ->
			dominantColorState.updateFrom(imageBitmap)
			color = dominantColorState.color
		}
	}

	return color
}

@Composable
fun rememberColorSchemeFromCoverArt(
	coverArtId: String?,
	forceDark: Boolean = false,
	style: PaletteStyle = PaletteStyle.Content,
	specVersion: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2021
): ColorScheme {
	val seedColor = rememberDominantCoverColor(coverArtId) ?: MaterialTheme.colorScheme.primary

	val preferenceManager = koinInject<PreferenceManager>()
	val inDarkTheme = isSystemInDarkTheme()
	val isDark = remember(forceDark, preferenceManager.themeMode) {
		forceDark || when (preferenceManager.themeMode) {
			ThemeMode.System -> inDarkTheme
			ThemeMode.Dark -> true
			ThemeMode.Light -> false
		}
	}
	val scheme = rememberDynamicColorScheme(
		seedColor = seedColor,
		isDark = isDark,
		style = style,
		specVersion = specVersion
	)

	return scheme
}

/**
 * The cover's dominant colour for tinting (Spotify-style headers, the mini player) without re-theming anything,
 * like the web's use-cover-color. Very dark colours are lifted so they still read as a colour on the dark UI.
 * Animates between covers; [fallback] until the first colour is extracted.
 */
@Composable
fun rememberCoverColor(
	coverArtId: String?,
	fallback: Color = MaterialTheme.colorScheme.surface
): Color {
	val target = rememberDominantCoverColor(coverArtId)?.let { color ->
		val luminance = calculateLuminance(color.toArgb()).toFloat()
		// ponytail: blend towards white below 8% luminance; HCT tone lift if greys look too washed out
		if (luminance < MIN_COVER_LUMINANCE) {
			lerp(color, Color.White, (MIN_COVER_LUMINANCE - luminance) / MIN_COVER_LUMINANCE * 0.3f)
		} else color
	} ?: fallback
	val color by animateColorAsState(target)
	return color
}

private const val MIN_COVER_LUMINANCE = 0.08f

@Composable
fun rememberColorSchemeForCurrentSong(forceDark: Boolean = true): ColorScheme {
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val coverArtId = playerState.currentSong?.coverArtId
	return rememberColorSchemeFromCoverArt(
		coverArtId = coverArtId,
		forceDark = forceDark,
		style = if (coverArtId != null) PaletteStyle.Content else PaletteStyle.Monochrome
	)
}

private val IosRed = Color(255, 66, 69)

@Composable
fun lightIosColorScheme(
	accent: Color
): ColorScheme {
	return rememberDynamicColorScheme(
		primary = Color.White,
		isDark = false,
		isAmoled = true,
		specVersion = ColorSpec.SpecVersion.SPEC_2021,
		style = PaletteStyle.Content,
		modifyColorScheme = { scheme ->
			scheme.copy(
				primary = accent,
				onPrimary = Color.White,
				primaryContainer = accent.copy(alpha = .3f),
				onPrimaryContainer = accent,
				secondaryContainer = accent.copy(alpha = .3f),
				onSecondaryContainer = accent,
				secondary = accent,
				tertiaryContainer = accent.copy(alpha = .3f),
				onTertiaryContainer = accent,
				tertiary = accent,
				error = IosRed,
				onError = Color.White,
				errorContainer = IosRed,
				onErrorContainer = Color.White,
				surfaceVariant = Color(224, 221, 220)
			)
		}
	)
}

@Composable
fun darkIosColorScheme(
	accent: Color
): ColorScheme {
	return rememberDynamicColorScheme(
		primary = Color.White,
		isDark = true,
		isAmoled = true,
		specVersion = ColorSpec.SpecVersion.SPEC_2021,
		style = PaletteStyle.Content,
		modifyColorScheme = { scheme ->
			scheme.copy(
				primary = accent,
				onPrimary = Color.White,
				primaryContainer = accent.copy(alpha = .3f),
				onPrimaryContainer = accent,
				secondaryContainer = accent.copy(alpha = .3f),
				onSecondaryContainer = accent,
				secondary = accent,
				tertiaryContainer = accent.copy(alpha = .3f),
				onTertiaryContainer = accent,
				tertiary = accent,
				error = IosRed,
				onError = Color.White,
				errorContainer = IosRed,
				onErrorContainer = Color.White,
				surfaceVariant = Color(44, 44, 46),
				onSurfaceVariant = Color(142, 142, 147)
			)
		}
	)
}
