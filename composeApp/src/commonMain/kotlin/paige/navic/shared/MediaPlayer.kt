package paige.navic.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import paige.navic.domain.manager.ConnectManager
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainRadio
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.models.settings.ExplicitContentPlayback
import paige.navic.domain.repositories.PlayerStateRepository
import paige.navic.domain.repositories.SongRepository
import paige.navic.ui.core.PlayerUiState
import kotlin.time.Duration.Companion.seconds

abstract class MediaPlayerViewModel(
	private val stateRepository: PlayerStateRepository,
	protected val songRepository: SongRepository,
	protected val connectivityManager: ConnectivityManager,
	protected val downloadManager: DownloadManager,
	protected val preferenceManager: PreferenceManager
) : ViewModel() {

	@Suppress("PropertyName")
	protected val _uiState = MutableStateFlow(PlayerUiState())
	val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

	protected fun isExplicit(song: DomainSong): Boolean {
		return song.explicitStatus == DomainExplicitStatus.Explicit
			&& preferenceManager.explicitContentPlayback != ExplicitContentPlayback.Allowed
	}

	init {
		viewModelScope.launch {
			restoreState()
			observeAndSaveState()
		}
	}

	abstract fun addToQueueSingle(song: DomainSong, notify: Boolean = true)
	abstract fun addToQueue(collection: DomainSongCollection, notify: Boolean = true)
	abstract fun addToQueue(songs: List<DomainSong>, notify: Boolean = true)
	abstract fun removeFromQueue(index: Int)
	abstract fun moveQueueItem(fromIndex: Int, toIndex: Int)
	abstract fun clearQueue()
	abstract fun playAt(index: Int)
	abstract fun playNextSingle(song: DomainSong)
	abstract fun playNext(collection: DomainSongCollection)
	abstract fun playRadio(radio: DomainRadio)
	abstract fun pause()
	abstract fun resume()
	abstract fun seek(normalized: Float)
	abstract fun next()
	abstract fun previous()
	abstract fun toggleShuffle()
	abstract fun toggleRepeat()
	abstract fun shufflePlay(collection: DomainSongCollection)
	abstract fun setPlaybackSpeed(value: Float)

	fun playNow(song: DomainSong) {
		if (addToJam(listOf(song))) return
		clearQueue()
		addToQueueSingle(song, notify = false)
		playAt(0)
		checkAndAutoFillQueue()
	}

	fun playNow(collection: DomainSongCollection, startIndex: Int = 0) {
		if (addToJam(listOfNotNull(collection.songs.getOrNull(startIndex)))) return
		clearQueue()
		addToQueue(collection, notify = false)
		playAt(startIndex)
		checkAndAutoFillQueue()
	}

	fun playNow(songs: List<DomainSong>, startIndex: Int = 0) {
		if (addToJam(listOfNotNull(songs.getOrNull(startIndex)))) return
		clearQueue()
		addToQueue(songs, notify = false)
		playAt(startIndex)
		checkAndAutoFillQueue()
	}

	fun togglePlay() {
		if (!uiState.value.isPaused) {
			pause()
		} else {
			resume()
		}
	}

	abstract fun syncPlayerWithState(state: PlayerUiState)

	/* Svirka Connect (domain/manager/ConnectManager.kt): platforms that can, override these. */

	/** Start [songs] at [index], [positionMs] in: playback handed over from another device. */
	open fun playFrom(songs: List<DomainSong>, index: Int, positionMs: Long) = playNow(songs, index)

	/** 0..1, or null where it can't be set from another device. */
	open val volume: Float? get() = null

	open fun setVolume(value: Float) {}

	/** Swap the queue around the song playing now (at [index] in [songs]) without restarting it. */
	open fun replaceQueue(songs: List<DomainSong>, index: Int) {}

	/**
	 * Svirka Jam: set while this phone plays along with someone's Jam. Then the user's play/pause/skip/seek go to
	 * the host and what they play or queue is added to the host's queue, instead of acting on this player.
	 */
	var jam: ConnectManager? = null

	/** True when [cmd] went to the Jam instead of this player. */
	protected fun toJam(cmd: String, value: Double? = null): Boolean {
		val jam = jam ?: return false
		jam.jamControl(cmd, value)
		return true
	}

	/** True when [songs] went to the Jam host's queue instead of this player. */
	protected fun addToJam(songs: List<DomainSong>): Boolean {
		val jam = jam ?: return false
		jam.jamAdd(songs)
		return true
	}

	fun seekToSeconds(seconds: Double) {
		val duration = uiState.value.currentSong?.duration?.inWholeMilliseconds ?: return
		if (duration > 0) seek((seconds * 1000 / duration).toFloat().coerceIn(0f, 1f))
	}

	private var autoFill: Job? = null

	/** Music never stops (like the Svirka web app): on the last song, a random one from the library is queued next. */
	protected fun checkAndAutoFillQueue() {
		// not in a Jam (the host's queue), not on repeat, and one at a time (playNow and the transition both ask)
		if (!preferenceManager.autoFillQueue || jam != null || autoFill?.isActive == true) return

		val state = uiState.value
		if (state.queue.isEmpty() || state.repeatMode != 0) return

		val remainingCount = state.queue.size - state.currentIndex

		if (remainingCount <= 1) {
			autoFill = viewModelScope.launch {
				val randomSongs = songRepository.getRandomSongs(1)
				addToQueue(randomSongs, notify = false)
			}
		}
	}

	/** The queue ran out anyway (shuffle order, or Next on the last song): play a random song from the library. */
	protected fun playRandomSong() {
		if (!preferenceManager.autoFillQueue || jam != null || uiState.value.queue.isEmpty()) return
		viewModelScope.launch {
			val song = songRepository.getRandomSongs(1).firstOrNull() ?: return@launch
			addToQueueSingle(song, notify = false)
			playAt(uiState.value.queue.lastIndex)
		}
	}

	private suspend fun restoreState() {
		val savedState = stateRepository.state
			.filterNotNull()
			.firstOrNull()
			?.copy(isPaused = true, isLoading = false)
		if (savedState != null) {
			_uiState.value = savedState
			syncPlayerWithState(savedState)
			checkAndAutoFillQueue()
		}
	}

	@OptIn(FlowPreview::class)
	private fun observeAndSaveState() {
		viewModelScope.launch {
			uiState
				.distinctUntilChanged { old, new ->
					old.currentIndex == new.currentIndex &&
						old.queue == new.queue &&
						old.isPaused == new.isPaused &&
						old.repeatMode == new.repeatMode &&
						old.isShuffleEnabled == new.isShuffleEnabled
				}
				.collect { state ->
					stateRepository.setState(state)
				}
		}

		viewModelScope.launch {
			uiState
				.debounce(2.seconds)
				.collect { state ->
					stateRepository.setState(state)
				}
		}
	}
}
