package paige.navic.ui.screens.collection.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_delete_download
import navic.composeapp.generated.resources.info_download_failed
import navic.composeapp.generated.resources.notice_deleted_download
import navic.composeapp.generated.resources.notice_download_started
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.domain.models.DomainSongCollection
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Close
import paige.navic.icons.outlined.Delete
import paige.navic.icons.outlined.DownloadOff
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.svirka.SvirkaIcons
import paige.navic.ui.svirka.SvirkaPlayShuffleRow
import paige.navic.ui.svirka.mutedColor

@Composable
fun CollectionDetailScreenHeadingRowButtons(
	collection: DomainSongCollection
) {
	val player = koinInject<MediaPlayerViewModel>()
	val snackBarManager = koinInject<SnackBarManager>()
	val downloadManager = koinInject<DownloadManager>()
	val scope = rememberCoroutineScope()

	val downloadStatus by downloadManager
		.getCollectionDownloadStatus(collection.songs.map { it.id })
		.collectAsState(initial = DownloadStatus.NOT_DOWNLOADED)

	val hasSongs = collection.songs.isNotEmpty()

	SvirkaPlayShuffleRow(
		onPlay = { player.playNow(collection) },
		onShuffle = { player.shufflePlay(collection) },
		enabled = hasSongs,
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp)
			.padding(bottom = 24.dp)
	) {
		IconButton(
			modifier = Modifier.size(40.dp),
			onClick = {
				scope.launch {
					when (downloadStatus) {
						DownloadStatus.NOT_DOWNLOADED, DownloadStatus.FAILED -> {
							downloadManager.downloadCollection(collection)
							snackBarManager.notify(Res.string.notice_download_started)
						}

						DownloadStatus.DOWNLOADING -> {
							downloadManager.cancelCollectionDownload(collection)
						}

						DownloadStatus.DOWNLOADED -> {
							downloadManager.deleteDownloadedCollection(collection)
							snackBarManager.notify(Res.string.notice_deleted_download)
						}
					}
				}
			},
			enabled = hasSongs ||
				(downloadStatus == DownloadStatus.DOWNLOADED || downloadStatus == DownloadStatus.DOWNLOADING)
		) {
			when (downloadStatus) {
				DownloadStatus.DOWNLOADING -> {
					Box(contentAlignment = Alignment.Center) {
						CircularProgressIndicator(
							modifier = Modifier.size(22.dp),
							strokeWidth = 2.dp,
							color = MaterialTheme.colorScheme.primary
						)
						Icon(
							imageVector = Icons.Outlined.Close,
							contentDescription = "Cancel",
							modifier = Modifier.size(12.dp),
							tint = MaterialTheme.colorScheme.primary
						)
					}
				}

				DownloadStatus.DOWNLOADED -> {
					Icon(
						imageVector = Icons.Outlined.Delete,
						contentDescription = stringResource(Res.string.action_delete_download),
						modifier = Modifier.size(20.dp),
						tint = MaterialTheme.colorScheme.primary
					)
				}

				DownloadStatus.FAILED -> {
					Icon(
						imageVector = Icons.Outlined.DownloadOff,
						contentDescription = stringResource(Res.string.info_download_failed),
						modifier = Modifier.size(20.dp),
						tint = MaterialTheme.colorScheme.error
					)
				}

				else -> {
					Icon(
						imageVector = SvirkaIcons.Download,
						contentDescription = "Download",
						modifier = Modifier.size(20.dp),
						tint = mutedColor
					)
				}
			}
		}
	}
}
