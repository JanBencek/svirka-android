package paige.navic.ui.screens.playlist.components

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Modifier
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.settings.ListViewMode
import paige.navic.ui.components.layouts.artGridPlaceholder
import paige.navic.ui.core.UiState
import paige.navic.ui.svirka.SvirkaEmptyState
import paige.navic.ui.svirka.SvirkaIcons

fun LazyGridScope.playlistListScreenContent(
	state: UiState<List<DomainPlaylist>>,
	selectedPlaylist: DomainPlaylist?,
	selectedViewMode: ListViewMode,
	onUpdateSelection: (DomainPlaylist) -> Unit,
	onClearSelection: () -> Unit,
	onSetShareId: (String) -> Unit,
	onSetDeletionId: (String) -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
) {
	val data = state.data.orEmpty()
	if (data.isNotEmpty()) {
		items(data, { it.id }) { playlist ->
			if (selectedViewMode == ListViewMode.Grid) {
				PlaylistListScreenGridItem(
					modifier = Modifier.animateItem(),
					tab = "playlists",
					playlist = playlist,
					selected = playlist == selectedPlaylist,
					onSelect = { onUpdateSelection(playlist) },
					onDeselect = { onClearSelection() },
					onSetShareId = onSetShareId,
					onSetDeletionId = onSetDeletionId,
					onPlayNext = onPlayNext,
					onAddToQueue = onAddToQueue,
				)
			} else {
				PlaylistListScreenListItem(
					modifier = Modifier.animateItem(),
					playlist = playlist,
					selected = playlist == selectedPlaylist,
					onSelect = { onUpdateSelection(playlist) },
					onDeselect = { onClearSelection() },
					onSetShareId = onSetShareId,
					onSetDeletionId = onSetDeletionId,
					onPlayNext = onPlayNext,
					onAddToQueue = onAddToQueue,
				)
			}
		}
	} else {
		when (state) {
			is UiState.Loading -> {
				artGridPlaceholder(viewMode = selectedViewMode)
			}

			else -> {
				item(span = { GridItemSpan(maxLineSpan) }) {
					SvirkaEmptyState(
						title = "No playlists yet",
						hint = "Create one and start collecting your favorite songs.",
						icon = SvirkaIcons.ListMusic
					)
				}
			}
		}
	}
}
