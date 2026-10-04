package paige.navic.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.core.PlayerUiState

/**
 * Per-row playback state: null when [songId] isn't the current track, else its isPaused.
 *
 * List rows must NOT collect the whole player uiState: `progress` ticks every 200ms, which
 * recomposed every visible row 5×/s while music played (scroll jank). Non-current rows
 * see a constant null and never recompose from playback.
 */
@Composable
fun MediaPlayerViewModel.rowPlaybackState(songId: String): State<Boolean?> {
	fun pick(s: PlayerUiState) = if (s.currentSong?.id == songId) s.isPaused else null
	val flow = remember(this, songId) { uiState.map(::pick).distinctUntilChanged() }
	return flow.collectAsStateWithLifecycle(pick(uiState.value))
}
