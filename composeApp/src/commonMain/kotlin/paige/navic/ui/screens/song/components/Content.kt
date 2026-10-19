package paige.navic.ui.screens.song.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableList
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.domain.manager.ExploreResult
import paige.navic.domain.models.DomainSong
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.importLink.SOURCE_LABELS
import paige.navic.ui.svirka.SvirkaEmptyState
import paige.navic.ui.svirka.SvirkaIcons
import paige.navic.ui.svirka.SvirkaPlayShuffleRow
import paige.navic.ui.svirka.SvirkaSectionTitle
import paige.navic.ui.svirka.SvirkaShapes
import paige.navic.ui.svirka.SvirkaText
import paige.navic.ui.svirka.mutedColor

/**
 * Svirka home: search field + Play/Shuffle header, then the (filtered) song rows, then the same
 * search outside the library ([outside]) — tapping one of those imports it and plays it.
 * [songs] is [state]'s list already filtered by [query].
 */
fun LazyListScope.songListScreenContent(
	state: UiState<ImmutableList<DomainSong>>,
	songs: List<DomainSong>,
	query: String,
	onQueryChange: (String) -> Unit,
	outside: List<ExploreResult>?,
	outsideLoading: Boolean,
	playingUrl: String?,
	onPlayOutside: (ExploreResult) -> Unit,
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
	if (state.data.isNullOrEmpty() && state is UiState.Loading) return

	item(key = "svirka_songs_header") {
		Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
			SongSearchField(query = query, onQueryChange = onQueryChange)
			if (songs.isNotEmpty()) {
				SvirkaPlayShuffleRow(
					onPlay = onPlayAll,
					onShuffle = onShuffleAll,
					modifier = Modifier.padding(top = 12.dp)
				)
			}
		}
	}

	if (songs.isEmpty()) {
		item {
			if (query.isBlank()) {
				SvirkaEmptyState(title = "Your library is empty.", hint = "Search above to find songs to play.")
			} else {
				Text(
					text = "Not in your library yet — pick it below and it starts playing.",
					style = SvirkaText.Body,
					color = mutedColor,
					modifier = Modifier.padding(horizontal = 16.dp)
				)
			}
		}
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

	if (outside == null && !outsideLoading) return
	item(key = "svirka_outside_header") {
		Row(
			modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			SvirkaSectionTitle(text = "From YouTube", icon = SvirkaIcons.Compass)
			if (outsideLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
		}
	}
	// the last results stay up while the next search runs
	if (outside?.isEmpty() == true && !outsideLoading) {
		item {
			Text(
				text = "No results — try different words, or paste a link.",
				style = SvirkaText.Body,
				color = mutedColor,
				modifier = Modifier.padding(horizontal = 16.dp)
			)
		}
	}
	items(outside.orEmpty(), key = { "outside:" + it.url }) { result ->
		OutsideResultRow(
			result = result,
			busy = playingUrl == result.url,
			enabled = playingUrl == null,
			onClick = { onPlayOutside(result) }
		)
	}
}

@Composable
private fun OutsideResultRow(result: ExploreResult, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(enabled = enabled, onClick = onClick)
			.padding(horizontal = 16.dp, vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Box(
			Modifier.size(48.dp).clip(SvirkaShapes.Sm).background(MaterialTheme.colorScheme.surfaceContainer),
			contentAlignment = Alignment.Center
		) {
			AsyncImage(
				model = result.thumbnail,
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier.matchParentSize()
			)
			if (busy) {
				Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)))
				CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
			}
		}
		Column(Modifier.weight(1f)) {
			Text(
				text = result.title ?: result.url,
				style = SvirkaText.RowTitle,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = listOfNotNull(
					SOURCE_LABELS[result.source] ?: result.source,
					result.artist,
					result.album,
					result.duration?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }
				).joinToString(" · "),
				style = SvirkaText.Small,
				color = mutedColor,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
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
