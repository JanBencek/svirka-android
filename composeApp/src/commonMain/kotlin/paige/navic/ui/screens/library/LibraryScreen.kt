package paige.navic.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.home_greeting_afternoon
import navic.composeapp.generated.resources.home_greeting_evening
import navic.composeapp.generated.resources.home_greeting_morning
import navic.composeapp.generated.resources.home_most_played
import navic.composeapp.generated.resources.home_rediscover
import navic.composeapp.generated.resources.option_sort_newest
import navic.composeapp.generated.resources.option_sort_recent
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.domain.manager.LoginManager
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainAlbumListType
import paige.navic.domain.models.DomainArtistListType
import paige.navic.domain.models.DomainSongCollection
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.dialogs.DeletionDialog
import paige.navic.ui.components.dialogs.DeletionEndpoint
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.components.snackbars.ErrorSnackBar
import paige.navic.ui.core.LoginUiState
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.PersistentViewModelStoreOwner
import paige.navic.ui.screens.album.viewmodels.AlbumListViewModel
import paige.navic.ui.screens.artist.viewmodels.ArtistListViewModel
import paige.navic.ui.screens.genre.viewmodels.GenreListViewModel
import paige.navic.ui.screens.library.components.LibraryScreenContent
import paige.navic.ui.screens.playlist.dialogs.PlaylistCreateDialog
import paige.navic.ui.screens.playlist.viewmodels.PlaylistListViewModel
import paige.navic.ui.screens.share.dialogs.ShareDialog
import kotlin.time.Clock
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
	val persistentViewModelStoreOwner = koinInject<PersistentViewModelStoreOwner>()

	// "Jump back in" keeps the old Library's recently-played view model; each shelf gets its own
	val recentAlbums = libraryAlbumShelf("libraryAlbums", "home-jump", Res.string.option_sort_recent, DomainAlbumListType.Recent)
	val albumShelves = listOf(
		libraryAlbumShelf("homeNewest", "home-newest", Res.string.option_sort_newest, DomainAlbumListType.Newest),
		libraryAlbumShelf("homeFrequent", "home-frequent", Res.string.home_most_played, DomainAlbumListType.Frequent),
		libraryAlbumShelf("homeRandom", "home-random", Res.string.home_rediscover, DomainAlbumListType.Random)
	)
	val allAlbums = listOf(recentAlbums) + albumShelves

	val playlistsViewModel = koinViewModel<PlaylistListViewModel>(
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val playlistsState by playlistsViewModel.playlistsState.collectAsStateWithLifecycle()
	val selectedPlaylist by playlistsViewModel.selectedPlaylist.collectAsStateWithLifecycle()

	val artistsViewModel = koinViewModel<ArtistListViewModel>(
		key = "libraryArtists",
		parameters = { parametersOf(DomainArtistListType.AlphabeticalByName) },
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val artistsState by artistsViewModel.artistsState.collectAsStateWithLifecycle()
	val selectedArtist by artistsViewModel.selectedArtist.collectAsStateWithLifecycle()
	val selectedArtistAlbums by artistsViewModel.selectedArtistAlbums.collectAsStateWithLifecycle()
	val selectedArtistIsStarred by artistsViewModel.starred.collectAsStateWithLifecycle()

	val genresViewModel = koinViewModel<GenreListViewModel>(
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val genresState by genresViewModel.genresState.collectAsStateWithLifecycle()

	val loginManager = koinInject<LoginManager>()
	val loginState by loginManager.loginState.collectAsStateWithLifecycle()

	var shareId by rememberSaveable { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	var playlistDeletionId by rememberSaveable { mutableStateOf<String?>(null) }
	var playlistCreateDialogShown by rememberSaveable { mutableStateOf(false) }

	val player = koinInject<MediaPlayerViewModel>()

	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	LaunchedEffect(loginState is LoginUiState.Success) {
		allAlbums.forEach { it.viewModel.refreshAlbums(false) }
		playlistsViewModel.refreshPlaylists(false)
		artistsViewModel.refreshArtists(false)
		genresViewModel.refreshGenres(false)
	}

	Scaffold(
		topBar = { RootTopBar({ Text(stringResource(greeting())) }, scrollBehavior) },
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			RootBottomBar(scrolled = scrollManager.isTriggered)
		}
	) { innerPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = allAlbums.none { it.state is UiState.Loading } &&
				playlistsState !is UiState.Loading &&
				artistsState !is UiState.Loading &&
				genresState !is UiState.Loading,
			onRefresh = {
				allAlbums.forEach { it.viewModel.refreshAlbums(true) }
				playlistsViewModel.refreshPlaylists(true)
				artistsViewModel.refreshArtists(true)
				genresViewModel.refreshGenres(true)
			},
			key = allAlbums.map { it.state } + listOf(playlistsState, artistsState, genresState)
		) {
			LibraryScreenContent(
				scrollBehavior = scrollBehavior,
				innerPadding = innerPadding,
				onSetShareId = { shareId = it },
				onCreatePlaylist = { playlistCreateDialogShown = true },

				recentAlbums = recentAlbums,
				albumShelves = albumShelves,
				onPlayAlbumNext = { player.playNext(it) },
				onAddAlbumToQueue = { player.addToQueue(it) },

				artistsState = artistsState,
				selectedArtist = selectedArtist,
				selectedArtistAlbums = selectedArtistAlbums,
				selectedArtistIsStarred = selectedArtistIsStarred,
				onSelectArtist = { artistsViewModel.selectArtist(it) },
				onClearArtistSelection = { artistsViewModel.clearSelection() },
				onStarSelectedArtist = { artistsViewModel.starArtist(it) },
				onPlayArtistNext = {
					if (selectedArtist != null) artistsViewModel.playArtistAlbumsNext(
						player
					)
				},
				onAddArtistToQueue = {
					if (selectedArtist != null) artistsViewModel.addArtistAlbumsToQueue(
						player
					)
				},

				playlistsState = playlistsState,
				selectedPlaylist = selectedPlaylist,
				onSelectPlaylist = { playlistsViewModel.selectPlaylist(it) },
				onClearPlaylistSelection = { playlistsViewModel.clearSelection() },
				onDeletePlaylist = { playlistDeletionId = it },
				onShufflePlaylist = {
					if (selectedPlaylist != null) player.shufflePlay(selectedPlaylist as DomainSongCollection)
				},
				onPlayPlaylistNext = {
					if (selectedPlaylist != null) player.playNext(
						selectedPlaylist as DomainSongCollection
					)
				},
				onAddPlaylistToQueue = {
					if (selectedPlaylist != null) player.addToQueue(
						selectedPlaylist as DomainSongCollection
					)
				},

				genresState = genresState
			)
		}
	}

	val flattenedErrors = (allAlbums.map { (it.state as? UiState.Error)?.error } + listOf(
		(playlistsState as? UiState.Error)?.error,
		(artistsState as? UiState.Error)?.error,
		(genresState as? UiState.Error)?.error
	)).mapNotNull { it?.stackTraceToString() }.takeIf { it.isNotEmpty() }?.joinToString("\n\n")

	ErrorSnackBar(
		error = flattenedErrors?.let { Error(it) },
		onClearError = {
			allAlbums.forEach { it.viewModel.clearError() }
			playlistsViewModel.clearError()
			artistsViewModel.clearError()
			genresViewModel.clearError()
		}
	)

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)

	DeletionDialog(
		endpoint = DeletionEndpoint.PLAYLIST,
		id = playlistDeletionId,
		onIdClear = { playlistDeletionId = null },
		onRefresh = { playlistsViewModel.refreshPlaylists(false) }
	)

	if (playlistCreateDialogShown) {
		PlaylistCreateDialog(
			onDismissRequest = { playlistCreateDialogShown = false },
			onRefresh = { playlistsViewModel.refreshPlaylists(true) }
		)
	}
}

/** One album shelf on Home: its own view model (so it loads, selects and fails on its own) and shared-element tab. */
class LibraryAlbumShelf(
	val tab: String,
	val title: StringResource,
	val listType: DomainAlbumListType,
	val viewModel: AlbumListViewModel,
	val state: UiState<ImmutableList<DomainAlbum>>,
	val selected: DomainAlbum?,
	val starred: Boolean,
	val rating: Int
)

@Composable
private fun libraryAlbumShelf(
	key: String,
	tab: String,
	title: StringResource,
	listType: DomainAlbumListType
): LibraryAlbumShelf {
	val viewModel = koinViewModel<AlbumListViewModel>(
		key = key,
		parameters = { parametersOf(listType) },
		viewModelStoreOwner = koinInject<PersistentViewModelStoreOwner>()
	)
	val state by viewModel.albumsState.collectAsStateWithLifecycle()
	val selected by viewModel.selectedAlbum.collectAsStateWithLifecycle()
	val starred by viewModel.starred.collectAsStateWithLifecycle()
	val rating by viewModel.rating.collectAsStateWithLifecycle()
	return LibraryAlbumShelf(tab, title, listType, viewModel, state, selected, starred, rating)
}

/** Same hours as the web app's Home greeting. */
private fun greeting(): StringResource =
	when (Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour) {
		in 5..11 -> Res.string.home_greeting_morning
		in 12..17 -> Res.string.home_greeting_afternoon
		else -> Res.string.home_greeting_evening
	}
