package paige.navic.ui.screens.library.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.ImmutableList
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.home_your_playlists
import navic.composeapp.generated.resources.option_sort_recent
import navic.composeapp.generated.resources.title_albums
import navic.composeapp.generated.resources.title_artists
import navic.composeapp.generated.resources.title_create_playlist
import navic.composeapp.generated.resources.title_genres
import navic.composeapp.generated.resources.title_radios
import navic.composeapp.generated.resources.title_songs
import navic.composeapp.generated.resources.title_starred
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import paige.navic.LocalNavStack
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainAlbumListType
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainGenre
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainSongCollection
import paige.navic.ui.components.layouts.horizontalSection
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.album.components.AlbumListScreenGridItem
import paige.navic.ui.screens.artist.ArtistListScreenGridItem
import paige.navic.ui.screens.genre.components.GenreListScreenCard
import paige.navic.ui.screens.library.LibraryAlbumShelf
import paige.navic.ui.screens.playlist.components.PlaylistListScreenGridItem
import paige.navic.util.ui.withoutTop

private val TileWidth = 140.dp
private const val JUMP_TILES = 6

/** Shared-element tabs: one per Home section (album shelves carry theirs), so an item on two sections never has two covers with one key. */
private const val TAB_PLAYLISTS = "home-playlists"
private const val TAB_ARTISTS = "home-artists"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreenContent(
	scrollBehavior: TopAppBarScrollBehavior,
	innerPadding: PaddingValues,
	onSetShareId: (String) -> Unit,
	onCreatePlaylist: () -> Unit,

	// albums: recently played feeds "Jump back in", the rest are shelves
	recentAlbums: LibraryAlbumShelf,
	albumShelves: List<LibraryAlbumShelf>,
	onPlayAlbumNext: (DomainAlbum) -> Unit,
	onAddAlbumToQueue: (DomainAlbum) -> Unit,

	// artists
	artistsState: UiState<ImmutableList<DomainArtist>>,
	selectedArtist: DomainArtist?,
	selectedArtistAlbums: ImmutableList<DomainAlbum>?,
	selectedArtistIsStarred: Boolean,
	onSelectArtist: (DomainArtist) -> Unit,
	onClearArtistSelection: () -> Unit,
	onStarSelectedArtist: (Boolean) -> Unit,
	onPlayArtistNext: () -> Unit,
	onAddArtistToQueue: () -> Unit,

	// playlists
	playlistsState: UiState<ImmutableList<DomainPlaylist>>,
	selectedPlaylist: DomainPlaylist?,
	onSelectPlaylist: (DomainPlaylist) -> Unit,
	onClearPlaylistSelection: () -> Unit,
	onDeletePlaylist: (String) -> Unit,
	onShufflePlaylist: () -> Unit,
	onPlayPlaylistNext: () -> Unit,
	onAddPlaylistToQueue: () -> Unit,

	// genres
	genresState: UiState<ImmutableList<DomainGenre>>
) {
	// a playlist can sit in "Jump back in" and in "Your playlists"; only the long-pressed one opens its sheet
	var playlistSelectedFromJump by remember { mutableStateOf(false) }

	val recentLoading = recentAlbums.state is UiState.Loading && recentAlbums.state.data.isNullOrEmpty()
	val jump = jumpBackIn(recentAlbums.state.data.orEmpty(), playlistsState.data.orEmpty())

	LazyVerticalGrid(
		modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
		columns = GridCells.Fixed(2),
		contentPadding = innerPadding.withoutTop() + PaddingValues(top = 8.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		item(span = { GridItemSpan(maxLineSpan) }) {
			LibraryShortcuts(onCreatePlaylist = onCreatePlaylist)
		}

		// Jump back in: 2 columns, the left one padded from the start edge, the right one from the end
		if (recentLoading) {
			items(JUMP_TILES) { index ->
				LibraryJumpTilePlaceholder(Modifier.padding(jumpPadding(index)))
			}
		} else {
			items(jump.size) { index ->
				when (val collection = jump[index]) {
					is DomainAlbum -> LibraryJumpTile(
						modifier = Modifier.padding(jumpPadding(index)),
						tab = recentAlbums.tab,
						collection = collection,
						selected = collection == recentAlbums.selected,
						onSelect = { recentAlbums.viewModel.selectAlbum(collection) },
						onDeselect = { recentAlbums.viewModel.clearSelection() },
						onSetShareId = onSetShareId,
						onPlayNext = { onPlayAlbumNext(collection) },
						onAddToQueue = { onAddAlbumToQueue(collection) },
						starred = recentAlbums.starred,
						onSetStarred = { recentAlbums.viewModel.starAlbum(it) },
						rating = recentAlbums.rating,
						onSetRating = { recentAlbums.viewModel.setRating(it) }
					)

					is DomainPlaylist -> LibraryJumpTile(
						modifier = Modifier.padding(jumpPadding(index)),
						tab = recentAlbums.tab,
						collection = collection,
						selected = playlistSelectedFromJump && collection == selectedPlaylist,
						onSelect = {
							playlistSelectedFromJump = true
							onSelectPlaylist(collection)
						},
						onDeselect = onClearPlaylistSelection,
						onSetShareId = onSetShareId,
						onPlayNext = onPlayPlaylistNext,
						onAddToQueue = onAddPlaylistToQueue,
						onShufflePlay = onShufflePlaylist,
						onSetDeletionId = onDeletePlaylist
					)
				}
			}
		}

		albumShelves.forEach { shelf ->
			horizontalSection(
				title = shelf.title,
				destination = Screen.AlbumList(true, shelf.listType),
				state = shelf.state,
				key = { it.id },
				seeAll = true
			) { album ->
				AlbumListScreenGridItem(
					modifier = Modifier.animateItem().width(TileWidth),
					tab = shelf.tab,
					album = album,
					selected = album == shelf.selected,
					starred = shelf.starred,
					onSelect = { shelf.viewModel.selectAlbum(album) },
					onDeselect = { shelf.viewModel.clearSelection() },
					onSetStarred = { shelf.viewModel.starAlbum(it) },
					onSetShareId = { onSetShareId(it) },
					onPlayNext = { onPlayAlbumNext(album) },
					onAddToQueue = { onAddAlbumToQueue(album) },
					rating = shelf.rating,
					onSetRating = { shelf.viewModel.setRating(it) }
				)
			}
		}

		horizontalSection(
			title = Res.string.home_your_playlists,
			destination = Screen.PlaylistList(true),
			state = playlistsState,
			key = { it.id },
			seeAll = true
		) { playlist ->
			PlaylistListScreenGridItem(
				modifier = Modifier.animateItem().width(TileWidth),
				tab = TAB_PLAYLISTS,
				playlist = playlist,
				selected = !playlistSelectedFromJump && playlist == selectedPlaylist,
				onSelect = {
					playlistSelectedFromJump = false
					onSelectPlaylist(playlist)
				},
				onDeselect = { onClearPlaylistSelection() },
				onSetDeletionId = { onDeletePlaylist(it) },
				onSetShareId = { onSetShareId(it) },
				onShufflePlay = onShufflePlaylist,
				onPlayNext = onPlayPlaylistNext,
				onAddToQueue = onAddPlaylistToQueue
			)
		}

		horizontalSection(
			title = Res.string.title_artists,
			destination = Screen.ArtistList(true),
			state = artistsState,
			key = { it.id },
			seeAll = true
		) { artist ->
			ArtistListScreenGridItem(
				modifier = Modifier.animateItem().width(TileWidth),
				tab = TAB_ARTISTS,
				artist = artist,
				selected = artist == selectedArtist,
				selectedArtistAlbums = selectedArtistAlbums,
				starred = selectedArtistIsStarred,
				onSelect = { onSelectArtist(artist) },
				onDeselect = { onClearArtistSelection() },
				onSetStarred = { onStarSelectedArtist(it) },
				onPlayNext = onPlayArtistNext,
				onAddToQueue = onAddArtistToQueue
			)
		}

		horizontalSection(
			title = Res.string.title_genres,
			destination = Screen.GenreList(true),
			state = genresState,
			key = { it.name },
			seeAll = true
		) { genreWithAlbums ->
			GenreListScreenCard(genre = genreWithAlbums)
		}
	}
}

/**
 * Up to 6 recently played albums; playlists (which have no play history) fill in, at most 2, when there are fewer.
 * The Recent list is every album by last play, so never-played ones are dropped here.
 */
private fun jumpBackIn(
	recent: List<DomainAlbum>,
	playlists: List<DomainPlaylist>
): List<DomainSongCollection> {
	val albums = recent.filter { it.lastPlayedAt != null }.take(JUMP_TILES)
	val fill = minOf(2, JUMP_TILES - albums.size)
	return albums + playlists.take(fill)
}

private fun jumpPadding(index: Int) = if (index % 2 == 0) {
	PaddingValues(start = 16.dp)
} else {
	PaddingValues(end = 16.dp)
}

/** The old Library's overview buttons as one scrolling chip row: the lists Home has no shelf for. */
@Composable
private fun LibraryShortcuts(onCreatePlaylist: () -> Unit) {
	val backStack = LocalNavStack.current
	val shortcuts: List<Pair<StringResource, NavKey>> = listOf(
		Res.string.title_starred to Screen.Starred(),
		Res.string.option_sort_recent to Screen.AlbumList(true, DomainAlbumListType.Recent),
		Res.string.title_albums to Screen.AlbumList(true),
		Res.string.title_songs to Screen.SongList(true),
		Res.string.title_radios to Screen.RadioList(true)
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.horizontalScroll(rememberScrollState())
			.padding(horizontal = 16.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		shortcuts.forEach { (label, destination) ->
			SuggestionChip(
				onClick = dropUnlessResumed { backStack.add(destination) },
				label = { Text(stringResource(label), maxLines = 1) }
			)
		}
		SuggestionChip(
			onClick = onCreatePlaylist,
			label = { Text(stringResource(Res.string.title_create_playlist), maxLines = 1) }
		)
	}
}
