package paige.navic.ui.screens.collection.components

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.count_songs
import org.jetbrains.compose.resources.pluralStringResource
import paige.navic.LocalNavStack
import paige.navic.LocalSharedTransitionScope
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainSongCollection
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.navigation.Screen
import paige.navic.ui.svirka.SvirkaDetailHeader
import paige.navic.ui.svirka.SvirkaShapes
import paige.navic.ui.svirka.detail.SvirkaPlaylistCover
import paige.navic.util.ui.EmphasizedDecelerateEasing

@Composable
fun CollectionDetailScreenHeadingRow(
	collection: DomainSongCollection,
	tab: String,
	titleAlpha: Float
) {
	val backStack = LocalNavStack.current
	val songs = pluralStringResource(Res.plurals.count_songs, collection.songCount, collection.songCount)
	val meta = when (collection) {
		is DomainAlbum -> listOfNotNull(collection.year?.toString(), collection.genre, songs)
		is DomainPlaylist -> listOfNotNull(collection.owner.takeIf { it.isNotBlank() }, songs)
	}.joinToString(" · ")
	val onArtistClick = dropUnlessResumed {
		(collection as? DomainAlbum)?.artistId?.let { id ->
			backStack.add(Screen.ArtistDetail(id))
		}
	}
	with(LocalSharedTransitionScope.current) {
		val coverModifier = Modifier
			.size(140.dp)
			.sharedElement(
				sharedContentState = this@with.rememberSharedContentState("${tab}-${collection.id}-cover"),
				boundsTransform = BoundsTransform { _, _ ->
					tween(
						durationMillis = 500,
						easing = EmphasizedDecelerateEasing
					)
				},
				animatedVisibilityScope = LocalNavAnimatedContentScope.current
			)
		SvirkaDetailHeader(
			eyebrow = if (collection is DomainPlaylist) "Playlist" else "Album",
			title = collection.name,
			subtitle = when (collection) {
				is DomainAlbum -> collection.artistName
				is DomainPlaylist -> collection.comment?.takeIf { it.isNotBlank() }
			},
			onSubtitleClick = if (collection is DomainAlbum) onArtistClick else null,
			meta = meta,
			modifier = Modifier
				.padding(horizontal = 16.dp)
				.padding(top = 8.dp, bottom = 24.dp)
				.alpha(titleAlpha)
		) {
			if (collection is DomainPlaylist) {
				SvirkaPlaylistCover(iconSize = 64.dp, modifier = coverModifier)
			} else {
				CoverArt(
					coverArtId = collection.coverArtId,
					contentDescription = collection.name,
					modifier = coverModifier,
					shape = SvirkaShapes.Md,
					crossfadeMs = 0
				)
			}
		}
	}
}
