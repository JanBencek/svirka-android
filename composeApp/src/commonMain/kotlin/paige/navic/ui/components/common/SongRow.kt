package paige.navic.ui.components.common

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.persistentListOf
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.settings.ExplicitContentPlayback
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.dialogs.QueueDuplicateDialog
import paige.navic.ui.components.sheets.SongSheet
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import paige.navic.ui.svirka.rows.SvirkaRowCover
import paige.navic.ui.svirka.rows.SvirkaRowIndicators
import paige.navic.ui.svirka.rows.SvirkaTrackRow
import paige.navic.util.core.buildSongInfoString

@Composable
fun SongRow(
	modifier: Modifier = Modifier,
	song: DomainSong,
	selected: Boolean = false,
	onClick: (() -> Unit),
	onLongClick: (() -> Unit),
	isOnline: Boolean = false,
	onDismissRequest: () -> Unit,
	onRemoveStar: () -> Unit,
	onAddStar: () -> Unit,
	onShare: () -> Unit,
	starredState: Boolean,
	download: DownloadEntity? = null,
	onDownload: () -> Unit,
	onCancelDownload: () -> Unit,
	onDeleteDownload: () -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	rating: Int,
	onSetRating: (Int) -> Unit
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val playbackState by player.rowPlaybackState(song.id)

	val backStack = LocalNavStack.current
	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	var duplicateQueueDialogShown by rememberSaveable { mutableStateOf(false) }
	var duplicateQueueDialogShownPlayNext by rememberSaveable { mutableStateOf(false) }

	val isDownloaded = download?.status == DownloadStatus.DOWNLOADED
	val isCurrentTrack = playbackState != null
	val isExplicit = song.explicitStatus == DomainExplicitStatus.Explicit
		&& preferenceManager.explicitContentPlayback != ExplicitContentPlayback.Allowed
	val maybeUnavailable = !isOnline && !isDownloaded

	SvirkaTrackRow(
		modifier = modifier.width(400.dp),
		title = buildAnnotatedString {
			append(song.title)
			if (song.explicitStatus == DomainExplicitStatus.Explicit) {
				append(" ")
				appendInlineContent("InlineExplicitIcon")
			}
		},
		subtitle = buildSongInfoString(
			song = song,
			onClickArtist = { backStack.add(Screen.ArtistDetail(it)) },
			showYear = false
		),
		isCurrent = isCurrentTrack,
		onClick = onClick,
		onLongClick = onLongClick,
		enabled = !isExplicit,
		duration = song.duration,
		leading = { SvirkaRowCover(song.coverArtId, playbackState) },
		trailing = {
			SvirkaRowIndicators(
				starred = starredState,
				download = download,
				explicitLocked = isExplicit,
				maybeUnavailable = maybeUnavailable
			)
		}
	)

	if (selected) {
		SongSheet(
			onDismissRequest = onDismissRequest,
			song = song,
			starred = starredState,
			rating = rating,
			onSetStarred = { starred ->
				if (starred) onAddStar() else onRemoveStar()
			},
			onShare = onShare,
			onPlayNext = {
				if (player.uiState.value.queue.any { it.id == song.id }) {
					duplicateQueueDialogShown = true
					duplicateQueueDialogShownPlayNext = true
				} else {
					onPlayNext()
				}
			},
			onAddToQueue = {
				if (player.uiState.value.queue.any { it.id == song.id }) {
					duplicateQueueDialogShown = true
					duplicateQueueDialogShownPlayNext = false
				} else {
					onAddToQueue()
				}
			},
			onTrackInfo = dropUnlessResumed {
				backStack.add(Screen.SongDetailScreen(song.id, song.coverArtId))
			},
			onViewAlbum = dropUnlessResumed {
				backStack.add(
					Screen.CollectionDetail(
						collectionId = song.albumId as String,
						tab = "library"
					)
				)
			},
			onAddToPlaylist = {
				playlistDialogShown = true
			},
			downloadStatus = download?.status,
			onDownload = onDownload,
			onCancelDownload = onCancelDownload,
			onDeleteDownload = onDeleteDownload,
			onSetRating = onSetRating
		)
	}

	if (playlistDialogShown) {
		PlaylistUpdateDialog(
			songs = persistentListOf(song),
			onDismissRequest = { playlistDialogShown = false }
		)
	}

	if (duplicateQueueDialogShown) {
		QueueDuplicateDialog(
			onDismissRequest = {
				duplicateQueueDialogShown = false
				onDismissRequest()
			},
			onConfirm = {
				if (duplicateQueueDialogShownPlayNext) onPlayNext() else onAddToQueue()
			}
		)
	}
}
