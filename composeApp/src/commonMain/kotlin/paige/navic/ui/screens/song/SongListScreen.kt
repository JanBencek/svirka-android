package paige.navic.ui.screens.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_cancel
import navic.composeapp.generated.resources.action_delete
import navic.composeapp.generated.resources.info_action_is_permanent
import navic.composeapp.generated.resources.info_error
import navic.composeapp.generated.resources.notice_deleted_song
import navic.composeapp.generated.resources.title_delete_song
import navic.composeapp.generated.resources.title_songs
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.ConnectManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongListType
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Delete
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.dialogs.FormDialog
import paige.navic.ui.components.dialogs.QueueDuplicateDialog
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.PersistentViewModelStoreOwner
import paige.navic.ui.screens.share.dialogs.ShareDialog
import paige.navic.ui.screens.song.components.SongListScreenSortButton
import paige.navic.ui.screens.song.components.songListScreenContent
import paige.navic.ui.screens.song.viewmodels.SongListViewModel
import paige.navic.util.core.isLandscape
import paige.navic.util.ui.withoutTop
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongListScreen(
	nested: Boolean,
	listType: DomainSongListType
) {
	val platformContext = LocalPlatformContext.current
	val viewModel = koinViewModel<SongListViewModel>(
		key = listType.toString(),
		parameters = { parametersOf(listType) },
		viewModelStoreOwner = if (nested) {
			LocalViewModelStoreOwner.current!!
		} else {
			koinInject<PersistentViewModelStoreOwner>()
		}
	)
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val songsState by viewModel.songsState.collectAsStateWithLifecycle()
	val selectedSong by viewModel.selectedSong.collectAsStateWithLifecycle()
	val selectedSorting by viewModel.selectedSorting.collectAsStateWithLifecycle()
	val selectedReversed by viewModel.selectedReversed.collectAsStateWithLifecycle()
	val starred by viewModel.starred.collectAsStateWithLifecycle()
	val selectedSongRating by viewModel.selectedSongRating.collectAsStateWithLifecycle()
	val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
	val outside by viewModel.outside.collectAsStateWithLifecycle()
	val outsideLoading by viewModel.outsideLoading.collectAsStateWithLifecycle()
	val busyUrls by viewModel.busyUrls.collectAsStateWithLifecycle()

	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	var songToQueue by remember { mutableStateOf<DomainSong?>(null) }
	var songToDelete by remember { mutableStateOf<DomainSong?>(null) }
	// Only the server's admin (Navidrome) may delete songs from the library.
	val connect = koinInject<ConnectManager>()
	val isAdmin by produceState(false) {
		value = try {
			connect.isAdmin()
		} catch (e: CancellationException) {
			throw e
		} catch (_: Exception) {
			false
		}
	}
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	// Svirka home search: filters the already-loaded list in memory.
	var query by rememberSaveable { mutableStateOf("") }
	val filteredSongs = remember(songsState, query) {
		val all = songsState.data.orEmpty()
		val q = query.trim()
		if (q.isEmpty()) all else all.filter {
			it.title.contains(q, ignoreCase = true)
				|| it.artistName.contains(q, ignoreCase = true)
				|| it.albumTitle?.contains(q, ignoreCase = true) == true
		}
	}

	// ...and, once typing pauses, outside it (YouTube Music + YouTube), so one search finds everything.
	LaunchedEffect(query) {
		delay(300)
		viewModel.searchOutside(query.trim())
	}

	val actions: @Composable RowScope.() -> Unit = {
		SongListScreenSortButton(
			nested = nested,
			selectedSorting = selectedSorting,
			onSetSorting = { viewModel.setSorting(it) },
			selectedReversed = selectedReversed,
			onSetReversed = { viewModel.setReversed(it) }
		)
	}

	Scaffold(
		topBar = {
			if (!nested) {
				RootTopBar(
					title = { Text(stringResource(Res.string.title_songs)) },
					scrollBehavior = scrollBehavior,
					actions = actions
				)
			} else {
				NestedTopBar(
					title = { Text(stringResource(Res.string.title_songs)) },
					actions = actions
				)
			}
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			val preferVisible = preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
			if (!nested || (!platformContext.isLandscape() && preferVisible)) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = songsState !is UiState.Loading,
			onRefresh = { viewModel.refreshSongs(true) },
			key = songsState
		) {
			LazyColumn(
				modifier = if (!nested)
					Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
				else Modifier.fillMaxSize(),
				contentPadding = innerPadding.withoutTop(),
				verticalArrangement = if ((songsState as? UiState.Success)?.data?.isEmpty() == true)
					Arrangement.Center
				else Arrangement.spacedBy(2.dp)
			) {
				songListScreenContent(
					state = songsState,
					songs = filteredSongs,
					query = query,
					onQueryChange = { query = it },
					outside = outside,
					outsideLoading = outsideLoading,
					busyUrls = busyUrls,
					onPlayOutside = { result -> viewModel.playOutside(result) { player.playNow(it) } },
					onQueueOutside = { result, next ->
						viewModel.playOutside(result, queued = true) { if (next) player.playNext(it) else player.addToQueue(it) }
					},
					onPlayAll = { player.playNow(filteredSongs) },
					onShuffleAll = { player.playNow(filteredSongs.shuffled()) },
					selectedSongIsStarred = starred,
					selectedSongRating = selectedSongRating,
					selectedSong = selectedSong,
					onUpdateSelection = { viewModel.selectSong(it) },
					onClearSelection = { viewModel.clearSelection() },
					onSetShareId = { newShareId ->
						shareId = newShareId
					},
					onSetStarred = { viewModel.starSong(it) },
					onPlayNext = { song ->
						if (player.uiState.value.queue.any { it.id == song.id }) {
							songToQueue = song
						} else {
							player.playNextSingle(song)
						}
					},
					onAddToQueue = { song ->
						if (player.uiState.value.queue.any { it.id == song.id }) {
							songToQueue = song
						} else {
							player.addToQueueSingle(song)
						}
					},
					onPlaySong = { song ->
						player.playNow(song)
					},
					onSetRating = { viewModel.rateSelectedSong(it) },
					onDownload = { viewModel.downloadSong(it) },
					allDownloads = allDownloads,
					onCancelDownload = { viewModel.cancelDownload(it.id) },
					onDeleteDownload = { viewModel.deleteDownload(it.id) },
					onDeleteFromLibrary = { song: DomainSong -> songToDelete = song }.takeIf { isAdmin }
				)
			}
		}
	}

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)

	songToDelete?.let { song ->
		DeleteSongDialog(
			song = song,
			onDismissRequest = { songToDelete = null },
			onDeleted = { viewModel.removeDeletedSong(song) }
		)
	}

	if (songToQueue != null) {
		QueueDuplicateDialog(
			onDismissRequest = { songToQueue = null },
			onConfirm = {
				songToQueue?.let { player.addToQueueSingle(it) }
			}
		)
	}
}

/** Admin only: deletes the song's file from the server's library (`DELETE /api/library/songs/<id>`). */
@Composable
private fun DeleteSongDialog(song: DomainSong, onDismissRequest: () -> Unit, onDeleted: () -> Unit) {
	val connect = koinInject<ConnectManager>()
	val snackBar = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()
	var deleting by remember { mutableStateOf(false) }
	var error by remember { mutableStateOf<String?>(null) }

	FormDialog(
		onDismissRequest = { if (!deleting) onDismissRequest() },
		icon = { Icon(Icons.Outlined.Delete, null) },
		title = { Text(stringResource(Res.string.title_delete_song)) },
		buttons = {
			FormButton(
				onClick = {
					deleting = true
					error = null
					scope.launch {
						error = try {
							connect.deleteSong(song.id)
						} catch (e: CancellationException) {
							throw e
						} catch (e: Exception) {
							e.message.orEmpty()
						}
						deleting = false
						if (error == null) {
							snackBar.notify(Res.string.notice_deleted_song)
							onDeleted()
							onDismissRequest()
						}
					}
				},
				enabled = !deleting,
				color = MaterialTheme.colorScheme.error
			) {
				if (deleting) CircularProgressIndicator(Modifier.size(20.dp))
				else Text(stringResource(Res.string.action_delete))
			}
			FormButton(onClick = onDismissRequest, enabled = !deleting) {
				Text(stringResource(Res.string.action_cancel))
			}
		},
		content = {
			Text("${song.title} — ${song.artistName}")
			error?.let {
				Text(it.ifBlank { stringResource(Res.string.info_error) }, color = MaterialTheme.colorScheme.error)
			}
			Text(stringResource(Res.string.info_action_is_permanent))
		}
	)
}
