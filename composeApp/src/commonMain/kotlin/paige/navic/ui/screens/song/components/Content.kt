package paige.navic.ui.screens.song.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.domain.models.DomainSong
import paige.navic.ui.core.UiState
import paige.navic.ui.svirka.SvirkaEmptyState
import paige.navic.ui.svirka.SvirkaIcons
import paige.navic.ui.svirka.SvirkaPlayShuffleRow
import paige.navic.ui.svirka.SvirkaShapes
import paige.navic.ui.svirka.SvirkaText
import paige.navic.ui.svirka.mutedColor

/**
 * Svirka home: search field + Play/Shuffle header, then the (filtered) song rows.
 * [songs] is [state]'s list already filtered by [query].
 */
fun LazyListScope.songListScreenContent(
	state: UiState<ImmutableList<DomainSong>>,
	songs: List<DomainSong>,
	query: String,
	onQueryChange: (String) -> Unit,
	onPlayAll: () -> Unit,
	onShuffleAll: () -> Unit,
	selectedSong: DomainSong?,
	selectedSongIsStarred: Boolean,
	selectedSongRating: Int,
	allDownloads: List<DownloadEntity>,
	onUpdateSelection: (DomainSong) -> Unit,
	onClearSelection: () -> Unit,
	onSetShareId: (String) -> Unit,
	onSetStarred: (Boolean) -> Unit,
	onPlayNext: (DomainSong) -> Unit,
	onAddToQueue: (DomainSong) -> Unit,
	onPlaySong: (DomainSong) -> Unit,
	onSetRating: (Int) -> Unit,
	onDownload: (DomainSong) -> Unit,
	onCancelDownload: (DomainSong) -> Unit,
	onDeleteDownload: (DomainSong) -> Unit
) {
	if (state.data.isNullOrEmpty()) {
		if (state !is UiState.Loading) {
			item {
				SvirkaEmptyState(
					title = "Your library is empty.",
					hint = "Drop some music into the library on the server."
				)
			}
		}
		return
	}

	item(key = "svirka_songs_header") {
		Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
			SongSearchField(query = query, onQueryChange = onQueryChange)
			SvirkaPlayShuffleRow(
				onPlay = onPlayAll,
				onShuffle = onShuffleAll,
				enabled = songs.isNotEmpty(),
				modifier = Modifier.padding(top = 12.dp)
			)
		}
	}

	if (songs.isEmpty()) {
		item {
			SvirkaEmptyState(
				title = "No songs match your search.",
				hint = "Try a different title or artist."
			)
		}
		return
	}

	// O(1) lookup per row instead of scanning every download for every row.
	val downloadsBySong = allDownloads.associateBy { it.songId }
	items(songs) { song ->
		val download = downloadsBySong[song.id]
		SongListScreenItem(
			modifier = Modifier.animateItem().padding(horizontal = 8.dp),
			song = song,
			selected = song == selectedSong,
			starred = if (song == selectedSong) selectedSongIsStarred else song.starredAt != null,
			rating = if (song == selectedSong) selectedSongRating else 0,
			onSelect = { onUpdateSelection(song) },
			onDeselect = { onClearSelection() },
			onSetStarred = { onSetStarred(it) },
			onSetShareId = onSetShareId,
			onPlayNext = { onPlayNext(song) },
			onAddToQueue = { onAddToQueue(song) },
			onClick = { onPlaySong(song) },
			onSetRating = onSetRating,
			download = download,
			onDownload = { onDownload(song) },
			onCancelDownload = { onCancelDownload(song) },
			onDeleteDownload = { onDeleteDownload(song) }
		)
	}
}

@Composable
private fun SongSearchField(query: String, onQueryChange: (String) -> Unit) {
	OutlinedTextField(
		value = query,
		onValueChange = onQueryChange,
		modifier = Modifier.widthIn(max = 448.dp).fillMaxWidth(),
		singleLine = true,
		shape = SvirkaShapes.Lg,
		textStyle = SvirkaText.Body,
		placeholder = { Text("Search songs…", style = SvirkaText.Body, color = mutedColor) },
		leadingIcon = { Icon(SvirkaIcons.Search, null, Modifier.size(16.dp), tint = mutedColor) },
		trailingIcon = if (query.isNotEmpty()) {
			{
				IconButton(onClick = { onQueryChange("") }) {
					Icon(SvirkaIcons.CircleX, "Clear search", Modifier.size(16.dp), tint = mutedColor)
				}
			}
		} else null
	)
}
