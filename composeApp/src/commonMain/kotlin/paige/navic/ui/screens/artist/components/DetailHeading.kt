package paige.navic.ui.screens.artist.components

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_more
import navic.composeapp.generated.resources.count_albums
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import paige.navic.LocalSharedTransitionScope
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.screens.artist.truncateText
import paige.navic.ui.svirka.SvirkaDetailHeader
import paige.navic.ui.svirka.SvirkaText
import paige.navic.ui.svirka.mutedColor
import paige.navic.ui.svirka.svirkaHeaderGradient
import paige.navic.util.ui.EmphasizedDecelerateEasing

@Composable
fun ArtistDetailScreenHeading(
	artistId: String,
	tab: String,
	artistName: String,
	coverArtId: String?,
	albumCount: Int,
	subtitle: String?,
	lastfm: String?,
	innerPadding: PaddingValues,
	scrolled: Boolean,
	coverColor: Color
) {
	val layoutDirection = LocalLayoutDirection.current
	val progress by animateFloatAsState(if (scrolled) 0f else 1f)
	Column(
		modifier = Modifier
			.fillMaxWidth()
			// the content starts under the transparent top bar, so the wash runs up behind it
			.svirkaHeaderGradient(coverColor)
			.padding(top = innerPadding.calculateTopPadding() + 8.dp)
			.padding(
				start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
				end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp
			)
			.alpha(progress)
	) {
		SvirkaDetailHeader(
			eyebrow = "Artist",
			title = artistName,
			meta = pluralStringResource(Res.plurals.count_albums, albumCount, albumCount)
		) { sizeModifier ->
			// same key as the artist card it was opened from (ArtGridItem / list row), so the image morphs in
			with(LocalSharedTransitionScope.current) {
				CoverArt(
					coverArtId = coverArtId,
					contentDescription = artistName,
					modifier = sizeModifier.sharedElement(
						sharedContentState = this@with.rememberSharedContentState("${tab}-${artistId}-cover"),
						boundsTransform = BoundsTransform { _, _ ->
							tween(
								durationMillis = 500,
								easing = EmphasizedDecelerateEasing
							)
						},
						animatedVisibilityScope = LocalNavAnimatedContentScope.current
					),
					shape = CircleShape,
					crossfadeMs = 0
				)
			}
		}
		subtitle?.takeIf { it.isNotBlank() }?.let { bio ->
			Text(
				text = buildAnnotatedString {
					append(truncateText(bio, 200))
					if (bio.length > 200 && lastfm != null) {
						append(" ")
						withLink(LinkAnnotation.Url(lastfm)) {
							append(stringResource(Res.string.action_more))
						}
					}
				},
				style = SvirkaText.Small,
				color = mutedColor,
				modifier = Modifier.padding(top = 16.dp)
			)
		}
	}
}
