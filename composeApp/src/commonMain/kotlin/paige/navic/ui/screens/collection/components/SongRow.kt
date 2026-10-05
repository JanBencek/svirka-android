package paige.navic.ui.screens.collection.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_add_to_queue
import navic.composeapp.generated.resources.action_play_next
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.settings.ExplicitContentPlayback
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Queue
import paige.navic.icons.outlined.QueuePlayNext
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.rowPlaybackState
import paige.navic.ui.components.dialogs.QueueDuplicateDialog
import paige.navic.ui.navigation.Screen
import paige.navic.ui.svirka.SvirkaShapes
import paige.navic.ui.svirka.rows.SvirkaRowCover
import paige.navic.ui.svirka.rows.SvirkaRowIndicators
import paige.navic.ui.svirka.rows.SvirkaRowTrackNumber
import paige.navic.ui.svirka.rows.SvirkaTrackRow
import paige.navic.util.core.buildSongInfoString

@Composable
fun CollectionDetailScreenSongRow(
	song: DomainSong,
	index: Int,
	count: Int,
	isPlaylist: Boolean = false,
	onClick: (() -> Unit),
	onLongClick: (() -> Unit),
	onPlayNext: (() -> Unit),
	onAddToQueue: (() -> Unit),
	isStarred: Boolean,
	download: DownloadEntity? = null,
	isOffline: Boolean = false
) {
	val preferenceManager = koinInject<PreferenceManager>()

	val player = koinInject<MediaPlayerViewModel>()
	val playbackState by player.rowPlaybackState(song.id)

	val isDownloaded = download?.status == DownloadStatus.DOWNLOADED
	val isCurrentTrack = playbackState != null
	val isExplicit = song.explicitStatus == DomainExplicitStatus.Explicit
		&& preferenceManager.explicitContentPlayback != ExplicitContentPlayback.Allowed
	val maybeUnavailable = isOffline && !isDownloaded

	val dismissState = rememberSwipeToDismissBoxState()
	val scope = rememberCoroutineScope()

	var isPlayNextPending by rememberSaveable { mutableStateOf<Boolean?>(null) }

	val backStack = LocalNavStack.current

	SwipeToDismissBox(
		modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
		state = dismissState,
		gesturesEnabled = !isExplicit,
		onDismiss = {
			if (it == SwipeToDismissBoxValue.StartToEnd) {
				if (player.uiState.value.queue.any { item -> item.id == song.id }) {
					isPlayNextPending = false
				} else {
					onAddToQueue()
				}
			}
			if (it == SwipeToDismissBoxValue.EndToStart) {
				if (player.uiState.value.queue.any { item -> item.id == song.id }) {
					isPlayNextPending = true
				} else {
					onPlayNext()
				}
			}
			scope.launch { dismissState.reset() }
		},
		backgroundContent = {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.clip(SvirkaShapes.Md)
					.background(MaterialTheme.colorScheme.primaryContainer)
					.padding(horizontal = 20.dp)
			) {
				when (dismissState.dismissDirection) {
					SwipeToDismissBoxValue.StartToEnd -> {
						Icon(
							imageVector = Icons.Outlined.Queue,
							contentDescription = stringResource(Res.string.action_add_to_queue),
							tint = MaterialTheme.colorScheme.onPrimaryContainer,
							modifier = Modifier.align(Alignment.CenterStart)
						)
					}

					SwipeToDismissBoxValue.EndToStart -> {
						Icon(
							imageVector = Icons.Outlined.QueuePlayNext,
							contentDescription = stringResource(Res.string.action_play_next),
							tint = MaterialTheme.colorScheme.onPrimaryContainer,
							modifier = Modifier.align(Alignment.CenterEnd)
						)
					}

					else -> {}
				}
			}
		}
	) {
		SvirkaTrackRow(
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
				showYear = false,
				showAlbum = false,
				// A tap anywhere on the row plays; artist navigation is in the long-press sheet.
				clickableArtist = false
			),
			isCurrent = isCurrentTrack,
			onClick = onClick,
			onLongClick = onLongClick,
			enabled = !isExplicit,
			duration = song.duration,
			leading = {
				if (isPlaylist) SvirkaRowCover(song.coverArtId, playbackState)
				else SvirkaRowTrackNumber(index + 1, playbackState)
			},
			trailing = {
				SvirkaRowIndicators(
					starred = isStarred,
					download = download,
					explicitLocked = isExplicit,
					maybeUnavailable = maybeUnavailable
				)
			}
		)
	}

	if (isPlayNextPending != null) {
		QueueDuplicateDialog(
			onDismissRequest = { isPlayNextPending = null },
			onConfirm = {
				if (isPlayNextPending == true) onPlayNext() else onAddToQueue()
				isPlayNextPending = null
			}
		)
	}
}
