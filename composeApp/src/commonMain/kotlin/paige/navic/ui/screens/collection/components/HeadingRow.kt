package paige.navic.ui.screens.collection.components

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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
import paige.navic.ui.svirka.svirkaHeaderGradient
import paige.navic.ui.svirka.svirkaLength
import paige.navic.ui.svirka.detail.SvirkaPlaylistCover
import paige.navic.util.ui.EmphasizedDecelerateEasing
import kotlin.time.Duration

@Composable
fun CollectionDetailScreenHeadingRow(
	collection: DomainSongCollection,
	tab: String,
	titleAlpha: Float,
	coverColor: Color
) {
	val backStack = LocalNavStack.current
	val songs = pluralStringResource(Res.plurals.count_songs, collection.songCount, collection.songCount)
	val length = (collection.duration ?: collection.songs.fold(Duration.ZERO) { total, song -> total + song.duration })
		.takeIf { it > Duration.ZERO }
		?.svirkaLength()
	val meta = when (collection) {
		is DomainAlbum -> listOfNotNull(collection.year?.toString(), collection.genre, songs, length)
		is DomainPlaylist -> listOfNotNull(collection.owner.takeIf { it.isNotBlank() }, songs, length)
	}.joinToString(" · ")
	val onArtistClick = dropUnlessResumed {
		(collection as? DomainAlbum)?.artistId?.let { id ->
			backStack.add(Screen.ArtistDetail(id))
		}
	}
	with(LocalSharedTransitionScope.current) {
		val sharedCover = Modifier
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
				.fillMaxWidth()
				.svirkaHeaderGradient(coverColor)
				.padding(horizontal = 16.dp)
				.padding(top = 8.dp, bottom = 24.dp)
				.alpha(titleAlpha)
		) { sizeModifier ->
			if (collection is DomainPlaylist) {
				SvirkaPlaylistCover(iconSize = 80.dp, modifier = sizeModifier.then(sharedCover))
			} else {
				CoverArt(
					coverArtId = collection.coverArtId,
					contentDescription = collection.name,
					modifier = sizeModifier.then(sharedCover),
					shape = SvirkaShapes.Md,
					crossfadeMs = 0
				)
			}
		}
	}
}
