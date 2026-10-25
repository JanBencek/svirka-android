package paige.navic.ui.screens.song.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.notice_import_joins_queue
import navic.composeapp.generated.resources.notice_play_import_failed
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.ExploreResult
import paige.navic.domain.manager.ImportManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongListType
import paige.navic.domain.repositories.SongRepository
import paige.navic.ui.core.UiState

class SongListViewModel(
	initialListType: DomainSongListType = DomainSongListType.FrequentlyPlayed,
	private val repository: SongRepository,
	private val downloadManager: DownloadManager,
	private val sessionManager: SessionManager,
	connectivityManager: ConnectivityManager,
	private val importManager: ImportManager,
	private val snackBarManager: SnackBarManager
) : ViewModel() {
	val songsState: StateFlow<UiState<ImmutableList<DomainSong>>>
		field = MutableStateFlow<UiState<ImmutableList<DomainSong>>>(UiState.Loading())

	val allDownloads = downloadManager.allDownloads
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = persistentListOf()
		)

	val selectedSong: StateFlow<DomainSong?>
		field = MutableStateFlow(null)

	val starred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedSongRating: StateFlow<Int>
		field = MutableStateFlow(0)

	val selectedSorting: StateFlow<DomainSongListType>
		field = MutableStateFlow(initialListType)

	val selectedReversed: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val isOnline = connectivityManager.isOnline

	/** The search query looked up outside the library (YouTube Music + YouTube); null = no search. */
	val outside: StateFlow<List<ExploreResult>?>
		field = MutableStateFlow<List<ExploreResult>?>(null)

	val outsideLoading: StateFlow<Boolean>
		field = MutableStateFlow(false)

	/** The outside result being imported to play, if any. */
	val playingUrl: StateFlow<String?>
		field = MutableStateFlow<String?>(null)

	private var outsideJob: Job? = null

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshSongs(false) }
		}
		viewModelScope.launch {
			importManager.libraryChanged.collect { refreshSongs(false) }
		}
	}

	/** A newer query cancels the last; blank or several pasted links aren't a search. */
	fun searchOutside(query: String) {
		outsideJob?.cancel()
		if (query.isBlank() || ImportManager.extractUrls(query).size > 1) {
			outside.value = null
			outsideLoading.value = false
			return
		}
		outsideJob = viewModelScope.launch {
			outsideLoading.value = true
			outside.value = runCatching { importManager.search(query) }
				.onFailure { if (it is CancellationException) throw it }
				.getOrDefault(emptyList())
			outsideLoading.value = false
		}
	}

	/**
	 * Tap = play, long-press = queue ([queued]): imported first (a few seconds), then handed to [play];
	 * it shows up in Songs too.
	 */
	fun playOutside(result: ExploreResult, queued: Boolean = false, play: (List<DomainSong>) -> Unit) {
		if (playingUrl.value != null) return
		playingUrl.value = result.url
		// ponytail: lands when the import finishes, so songs queued meanwhile can end up ahead of it
		if (queued) snackBarManager.notify(Res.string.notice_import_joins_queue)
		viewModelScope.launch {
			try {
				val songs = repository.getSongsByIds(importManager.importForPlay(result.url))
				if (songs.isNotEmpty()) play(songs)
			} catch (e: CancellationException) {
				throw e
			} catch (_: Exception) {
				snackBarManager.notify(Res.string.notice_play_import_failed)
			} finally {
				playingUrl.value = null
			}
		}
	}

	/** The song was deleted on the server: drop it here too. */
	fun removeDeletedSong(song: DomainSong) {
		viewModelScope.launch {
			repository.removeLocalSong(song)
			refreshSongs(false)
		}
	}

	fun selectSong(song: DomainSong) {
		viewModelScope.launch {
			selectedSong.value = song
			starred.value = repository.isSongStarred(song)
			selectedSongRating.value = repository.getSongRating(song)
		}
	}

	fun clearSelection() {
		selectedSong.value = null
	}

	fun refreshSongs(fullRefresh: Boolean) {
		viewModelScope.launch {
			repository.getSongsFlow(
				fullRefresh,
				selectedSorting.value,
				selectedReversed.value
			).collect {
				songsState.value = it
			}
		}
	}

	fun starSong(isStarred: Boolean) {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatching {
				if (isStarred) {
					repository.starSong(selection)
				} else {
					repository.unstarSong(selection)
				}
				starred.value = isStarred
				refreshSongs(false)
			}
		}
	}

	fun rateSelectedSong(rating: Int) {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatching {
				repository.rateSong(selection, rating)
				selectedSongRating.value = rating
			}
		}
	}

	fun setSorting(sorting: DomainSongListType) {
		selectedSorting.value = sorting
		refreshSongs(false)
	}

	fun setReversed(reversed: Boolean) {
		selectedReversed.value = reversed
		refreshSongs(false)
	}

	fun clearError() {
		songsState.value = UiState.Success(songsState.value.data ?: persistentListOf())
	}

	fun downloadSong(song: DomainSong) {
		downloadManager.downloadSong(song)
	}

	fun cancelDownload(songId: String) {
		downloadManager.cancelDownload(songId)
	}

	fun deleteDownload(songId: String) {
		downloadManager.deleteDownload(songId)
	}
}
