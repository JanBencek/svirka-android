package paige.navic.ui.screens.nowPlaying.components.controls

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import paige.navic.ui.svirka.SvirkaIcons
import paige.navic.shared.MediaPlayerViewModel

@Composable
fun NowPlayingStarButton(
	songIsStarred: Boolean,
	onSetSongIsStarred: (Boolean) -> Unit
) {
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()
	IconButton(
		onClick = {
			onSetSongIsStarred(!songIsStarred)
		},
		colors = IconButtonDefaults.filledTonalIconButtonColors(),
		modifier = Modifier.size(32.dp),
		enabled = playerState.currentSong != null
	) {
		// Svirka calls starring "liking" (heart) everywhere — same icon as the mini player.
		Icon(
			if (songIsStarred) SvirkaIcons.HeartFilled else SvirkaIcons.Heart,
			contentDescription = if (songIsStarred) "Unlike" else "Like",
			modifier = Modifier.size(18.dp)
		)
	}
}
