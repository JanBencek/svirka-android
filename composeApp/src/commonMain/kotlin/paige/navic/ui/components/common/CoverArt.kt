package paige.navic.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_image_failed_to_load
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Error
import paige.navic.ui.theme.defaultFont
import paige.navic.util.core.Logger
import coil3.compose.LocalPlatformContext as LocalCoilPlatformContext

@Composable
fun CoverArt(
	modifier: Modifier = Modifier,
	coverArtId: String?,
	contentDescription: String? = null,
	onClick: (() -> Unit)? = null,
	onLongClick: (() -> Unit)? = null,
	square: Boolean = true,
	crossfadeMs: Int = 500,
	shadowElevation: Dp = 0.dp,
	interactionSource: MutableInteractionSource? = null,
	shape: Shape? = null,
	/** Small list/sheet artwork: fetch a 200px rendition instead of the full-quality one. */
	thumbnail: Boolean = false
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val shape = shape ?: if (!coverArtId.orEmpty().startsWith("ar-")) {
		preferenceManager.coverArtShape.shape
	} else {
		preferenceManager.artistImageShape.shape
	}
	val coilPlatformContext = LocalCoilPlatformContext.current
	val customHeaders = preferenceManager.customHeaders
	val sessionManager = koinInject<SessionManager>()
	val thumbSize = if (thumbnail) THUMBNAIL_SIZE_PX else null
	val model = remember(coverArtId, customHeaders, thumbSize) {
		val networkHeaders = NetworkHeaders.Builder().apply {
			preferenceManager.customHeadersMap().forEach { (key, value) -> add(key, value) }
		}.build()
		ImageRequest.Builder(coilPlatformContext)
			.data(coverArtId?.let { sessionManager.getCoverArtUrl(it, thumbSize) })
			.memoryCacheKey(thumbSize?.let { "$coverArtId@$it" } ?: coverArtId)
			.diskCacheKey(thumbSize?.let { "$coverArtId@$it" } ?: coverArtId)
			.diskCachePolicy(CachePolicy.ENABLED)
			.memoryCachePolicy(CachePolicy.ENABLED)
			.crossfade(crossfadeMs)
			.httpHeaders(networkHeaders)
			.build()
	}

	val commonModifier = modifier
		.then(if (square) Modifier.aspectRatio(1f) else Modifier)
		.shadow(shadowElevation, shape)
		.clip(shape)
		.background(MaterialTheme.colorScheme.surfaceContainer)
		.then(
			if (onClick != null)
				Modifier.combinedClickable(
					onClick = onClick,
					onLongClick = onLongClick,
					interactionSource = interactionSource
				)
			else Modifier
		)
		.then(
			if (interactionSource != null)
				Modifier.indication(interactionSource, ripple())
			else Modifier
		)

	if (coverArtId.isNullOrBlank()) return Box(commonModifier)
	if (thumbnail) {
		// Plain AsyncImage: SubcomposeAsyncImage's per-item subcomposition is costly in
		// scrolling lists. On error the surfaceContainer background shows through.
		return AsyncImage(
			model = model,
			contentDescription = contentDescription,
			modifier = commonModifier,
			contentScale = ContentScale.Crop
		)
	}
	SubcomposeAsyncImage(
		model = model,
		contentDescription = contentDescription,
		modifier = commonModifier,
		contentScale = ContentScale.Crop,
		error = {
			LaunchedEffect(it.result.throwable) {
				Logger.w(
					"CoverArt",
					"Failed to load cover art, falling back to placeholder",
					it.result.throwable
				)
			}
			LazyColumn(
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center
			) {
				item { Icon(Icons.Outlined.Error, null) }
				item {
					Text(
						stringResource(Res.string.info_image_failed_to_load),
						maxLines = 1,
						autoSize = TextAutoSize.StepBased(
							minFontSize = 1.sp,
							maxFontSize = 14.sp
						),
						fontFamily = defaultFont(grade = 10, round = 100f),
						modifier = Modifier.semantics { hideFromAccessibility() }
					)
				}
			}
		}
	)
}

// 50dp at ~3.5x density ≈ 175px
private const val THUMBNAIL_SIZE_PX = 200
