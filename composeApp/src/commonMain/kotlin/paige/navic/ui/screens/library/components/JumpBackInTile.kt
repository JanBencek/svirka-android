package paige.navic.ui.screens.library.components

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.notice_deleted_download
import navic.composeapp.generated.resources.notice_download_started
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.LocalSharedTransitionScope
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainSongCollection
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.sheets.CollectionSheet
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import paige.navic.ui.svirka.SvirkaShapes
import paige.navic.ui.svirka.SvirkaText
import paige.navic.ui.svirka.detail.SvirkaPlaylistCover
import paige.navic.util.ui.EmphasizedDecelerateEasing
import paige.navic.util.ui.shimmerLoading

/**
 * One tile of Home's "Jump back in" grid (Spotify's compact top grid): a small cover on the left, the name on
 * the right. Tap opens the album/playlist with the cover morphing into its header; long-press opens the same
 * sheet as the album/playlist cards. Album-only and playlist-only actions are null for the other kind.
 */
@Composable
fun LibraryJumpTile(
	modifier: Modifier = Modifier,
	tab: String,
	collection: DomainSongCollection,
	selected: Boolean,
	onSelect: () -> Unit,
	onDeselect: () -> Unit,
	onSetShareId: (String) -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	starred: Boolean? = null,
	onSetStarred: ((Boolean) -> Unit)? = null,
	rating: Int? = null,
	onSetRating: ((Int) -> Unit)? = null,
	onShufflePlay: (() -> Unit)? = null,
	onSetDeletionId: ((String) -> Unit)? = null
) {
	val backStack = LocalNavStack.current
	val snackBarManager = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()
	val interactionSource = remember { MutableInteractionSource() }

	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }

	val downloadManager = koinInject<DownloadManager>()
	val downloadStatus by downloadManager
		.getCollectionDownloadStatus(collection.songs.map { it.id })
		.collectAsState(initial = DownloadStatus.NOT_DOWNLOADED)

	val album = collection as? DomainAlbum
	val onViewArtist = dropUnlessResumed {
		album?.let { backStack.add(Screen.ArtistDetail(it.artistId)) }
	}

	Box(modifier) {
		with(LocalSharedTransitionScope.current) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.clip(SvirkaShapes.Md)
					.background(MaterialTheme.colorScheme.surfaceContainer)
					.combinedClickable(
						interactionSource = interactionSource,
						indication = null,
						onClick = dropUnlessResumed {
							scope.launch {
								backStack.add(Screen.CollectionDetail(collection.id, tab))
							}
						},
						onLongClick = onSelect
					)
					.semantics {
						contentDescription = collection.name
					},
				verticalAlignment = Alignment.CenterVertically
			) {
				val coverModifier = Modifier
					.size(56.dp)
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
				// playlists show the gradient everywhere (card and detail header), so the morph matches
				if (collection is DomainPlaylist) {
					SvirkaPlaylistCover(iconSize = 24.dp, modifier = coverModifier)
				} else {
					CoverArt(
						coverArtId = collection.coverArtId,
						contentDescription = collection.name,
						modifier = coverModifier,
						interactionSource = interactionSource,
						shape = SvirkaShapes.Md
					)
				}
				Text(
					text = collection.name,
					style = SvirkaText.RowTitle.copy(fontWeight = FontWeight.SemiBold),
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier
						.weight(1f)
						.padding(horizontal = 12.dp)
				)
			}
		}

		if (selected) {
			CollectionSheet(
				onDismissRequest = onDeselect,
				collection = collection,
				onShare = { onSetShareId(collection.id) },
				onDelete = if (onSetDeletionId != null) {
					{ onSetDeletionId(collection.id) }
				} else {
					null
				},
				onShufflePlay = onShufflePlay,
				onPlayNext = onPlayNext,
				onAddToQueue = onAddToQueue,
				onAddAllToPlaylist = { playlistDialogShown = true },
				downloadStatus = downloadStatus,
				onDownloadAll = {
					scope.launch {
						downloadManager.downloadCollection(collection)
						snackBarManager.notify(Res.string.notice_download_started)
					}
				},
				onCancelDownloadAll = {
					scope.launch {
						collection.songs.forEach { downloadManager.cancelDownload(it.id) }
					}
				},
				onDeleteDownloadAll = {
					scope.launch {
						downloadManager.deleteDownloadedCollection(collection)
						snackBarManager.notify(Res.string.notice_deleted_download)
					}
				},
				starred = starred,
				onSetStarred = onSetStarred,
				onViewArtist = if (album != null) onViewArtist else null,
				rating = rating,
				onSetRating = onSetRating
			)
		}

		if (playlistDialogShown) {
			PlaylistUpdateDialog(
				songs = collection.songs.toPersistentList(),
				onDismissRequest = { playlistDialogShown = false }
			)
		}
	}
}

@Composable
fun LibraryJumpTilePlaceholder(modifier: Modifier = Modifier) {
	Box(
		modifier = modifier
			.fillMaxWidth()
			.height(56.dp)
			// placeholders shouldn't use continuous corners
			// because it's less performant
			.clip(SvirkaShapes.Md)
			.shimmerLoading()
	)
}
