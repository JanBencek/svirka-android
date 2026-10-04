package paige.navic.ui.screens.artist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.materialkolor.dynamiccolor.ColorSpec
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_see_all
import navic.composeapp.generated.resources.count_albums
import navic.composeapp.generated.resources.info_bulk_download_warning
import navic.composeapp.generated.resources.notice_deleted_download
import navic.composeapp.generated.resources.notice_download_started
import navic.composeapp.generated.resources.title_albums
import navic.composeapp.generated.resources.title_bulk_download
import navic.composeapp.generated.resources.title_similar_artists
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.domain.models.DomainSongListType
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.ErrorBox
import paige.navic.ui.components.common.SongRow
import paige.navic.ui.components.dialogs.BulkDownloadDialog
import paige.navic.ui.components.layouts.ArtCarousel
import paige.navic.ui.components.layouts.ArtCarouselItem
import paige.navic.ui.components.layouts.ArtGridItem
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.sheets.CollectionSheet
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.artist.components.ArtistActionButtons
import paige.navic.ui.screens.artist.components.ArtistDetailScreenHeading
import paige.navic.ui.screens.artist.components.ArtistDetailScreenTopBar
import paige.navic.ui.screens.artist.viewmodels.ArtistDetailViewModel
import paige.navic.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import paige.navic.ui.screens.share.dialogs.ShareDialog
import paige.navic.ui.svirka.SvirkaSectionTitle
import paige.navic.ui.svirka.SvirkaText
import paige.navic.ui.svirka.mutedColor
import paige.navic.ui.theme.NavicTheme
import paige.navic.util.core.isLandscape
import paige.navic.util.ui.rememberColorSchemeFromCoverArt
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistDetailScreen(
	artistId: String
) {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()

	val viewModel = koinViewModel<ArtistDetailViewModel>(
		key = artistId,
		parameters = { parametersOf(artistId) }
	)
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()

	val selection by viewModel.selectedSong.collectAsStateWithLifecycle()
	val selectedSongIsStarred by viewModel.selectedSongIsStarred.collectAsStateWithLifecycle()
	val selectedSongRating by viewModel.selectedSongRating.collectAsStateWithLifecycle()

	val selectedAlbum by viewModel.selectedAlbum.collectAsStateWithLifecycle()
	val selectedAlbumIsStarred by viewModel.selectedAlbumIsStarred.collectAsStateWithLifecycle()
	val selectedAlbumRating by viewModel.selectedAlbumRating.collectAsStateWithLifecycle()

	val downloadManager = koinInject<DownloadManager>()
	val density = LocalDensity.current
	val backStack = LocalNavStack.current
	val layoutDirection = LocalLayoutDirection.current
	val artistState by viewModel.artistState.collectAsStateWithLifecycle()
	val starred by viewModel.starred.collectAsState()
	val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
	val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
	val downloadStatus by viewModel.collectionDownloadStatus()
		.collectAsState(DownloadStatus.NOT_DOWNLOADED)

	val snackBarManager = koinInject<SnackBarManager>()

	val scope = rememberCoroutineScope()

	val spatialSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
	val effectSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

	val scrolled by remember {
		derivedStateOf {
			with(density) { viewModel.scrollState.value.toDp() } >= 200.dp
		}
	}

	var showDownloadDialog by remember { mutableStateOf(false) }

	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }

	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }

	val colorScheme = if (preferenceManager.dynamicTheming) {
		rememberColorSchemeFromCoverArt(
			coverArtId = artistState.data?.artist?.coverArtId,
			specVersion = ColorSpec.SpecVersion.SPEC_2025
		)
	} else {
		null
	}

	NavicTheme(colorScheme) {
		Scaffold(
			topBar = {
				ArtistDetailScreenTopBar(
					scrolled = scrolled,
					artistState = artistState,
					starred = starred,
					onSetStarred = { viewModel.starArtist(it) },
				)
			},
			bottomBar = {
				val scrollManager = LocalBottomBarScrollManager.current
				val preferVisible = preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
				if (!platformContext.isLandscape() && preferVisible) {
					RootBottomBar(scrolled = scrollManager.isTriggered)
				}
			}
		) { contentPadding ->
			AnimatedContent(
				targetState = artistState,
				transitionSpec = {
					(fadeIn(
						animationSpec = effectSpec
					) + scaleIn(
						initialScale = 0.8f,
						animationSpec = spatialSpec
					)) togetherWith (fadeOut(
						animationSpec = effectSpec
					) + scaleOut(
						animationSpec = spatialSpec
					))
				},
				modifier = Modifier.fillMaxSize()
			) { artistState ->
				when (artistState) {
					is UiState.Error -> Box(Modifier.fillMaxSize().padding(contentPadding)) {
						ErrorBox(artistState)
					}

					is UiState.Loading -> Box(Modifier.fillMaxSize()) {
						ContainedLoadingIndicator(Modifier.size(80.dp).align(Alignment.Center))
					}

					is UiState.Success -> {
						val state = artistState.data
						BulkDownloadDialog(
							title = stringResource(Res.string.title_bulk_download),
							message = stringResource(
								Res.string.info_bulk_download_warning,
								state.artist.name
							),
							showDialog = showDownloadDialog,
							onDismissRequest = { showDownloadDialog = false },
							onConfirm = {
								scope.launch {
									state.albums.forEach { album ->
										downloadManager.downloadCollection(album)
									}
									snackBarManager.notify(Res.string.notice_download_started)
								}
							}
						)
						Column(
							modifier = Modifier
								.fillMaxSize()
								.verticalScroll(viewModel.scrollState),
							verticalArrangement = Arrangement.spacedBy(24.dp)
						) {
							ArtistDetailScreenHeading(
								artistName = state.artist.name,
								coverArtId = state.artist.coverArtId,
								albumCount = state.albums.size,
								subtitle = state.artist.biography,
								lastfm = state.artist.lastFmUrl,
								innerPadding = contentPadding,
								scrolled = scrolled
							)
							ArtistActionButtons(
								onPlay = { viewModel.playArtistAlbums(player) },
								onShuffle = {
									player.playNow(state.albums.flatMap { it.songs }.shuffled())
								},
								onDownload = {
									showDownloadDialog = true
								},
								onCancelDownload = {
									state.albums.forEach { album ->
										downloadManager.cancelCollectionDownload(album)
									}
								},
								onDeleteDownload = {
									state.albums.forEach { album ->
										downloadManager.deleteDownloadedCollection(album)
									}
									snackBarManager.notify(Res.string.notice_deleted_download)
								},
								downloadStatus = downloadStatus,
								playEnabled = state.albums.isNotEmpty()
							)
							Column(
								modifier = Modifier
									.fillMaxWidth()
									.padding(
										start = contentPadding.calculateStartPadding(
											layoutDirection
										)
									)
									.padding(
										end = contentPadding.calculateEndPadding(
											layoutDirection
										)
									),
								verticalArrangement = Arrangement.spacedBy(24.dp)
							) {
								state.topSongs.takeIf { state.topSongs.isNotEmpty() }
									?.let { songs ->
										Column {
											Row(
												modifier = Modifier
													.fillMaxWidth()
													.padding(horizontal = 16.dp)
													.padding(bottom = 8.dp),
												verticalAlignment = Alignment.CenterVertically,
												horizontalArrangement = Arrangement.SpaceBetween
											) {
												SvirkaSectionTitle("Popular songs")
												Text(
													stringResource(Res.string.action_see_all),
													style = SvirkaText.Body,
													color = mutedColor,
													modifier = Modifier.clickable(onClick = dropUnlessResumed {
														backStack.add(
															Screen.SongList(
																nested = true,
																listType = DomainSongListType.ByArtist(state.artist.id)
															)
														)
													})
												)
											}
											// ponytail: top 10 inline; "See all" opens the full list
											songs.take(10).forEachIndexed { index, song ->
												val download =
													allDownloads.find { it.songId == song.id }
												SongRow(
													modifier = Modifier.fillMaxWidth(),
													song = song,
													selected = selection == song,
													onClick = {
														if (playerState.currentSong?.id != song.id) {
															player.playNow(songs, index)
														} else {
															player.togglePlay()
														}
													},
													onLongClick = {
														viewModel.selectSong(song)
													},
													onDismissRequest = { viewModel.clearSelection() },
													starredState = if (selection == song) selectedSongIsStarred else song.starredAt != null,
													onAddStar = { viewModel.starSelectedSong() },
													onRemoveStar = { viewModel.unstarSelectedSong() },
													download = download,
													onDownload = { viewModel.downloadSong(song) },
													onCancelDownload = {
														viewModel.cancelDownload(
															song.id
														)
													},
													onDeleteDownload = {
														viewModel.deleteDownload(
															song.id
														)
													},
													onPlayNext = { player.playNextSingle(song) },
													onAddToQueue = { player.addToQueueSingle(song) },
													onShare = { shareId = song.id },
													isOnline = isOnline,
													rating = selectedSongRating,
													onSetRating = { viewModel.rateSelectedSong(it) }
												)
											}
										}
									}
								if (state.albums.isNotEmpty()) {
									Column(
										modifier = Modifier
											.fillMaxWidth()
											.padding(horizontal = 16.dp),
										verticalArrangement = Arrangement.spacedBy(16.dp)
									) {
										SvirkaSectionTitle(stringResource(Res.string.title_albums))
										state.albums.sortedByDescending { album -> album.playCount }
											.chunked(2)
											.forEach { rowAlbums ->
												Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
													rowAlbums.forEach { album ->
														Box(Modifier.weight(1f)) {
															val albumDownloadStatus by downloadManager
																.getCollectionDownloadStatus(album.songs.map { it.id })
																.collectAsState(initial = DownloadStatus.NOT_DOWNLOADED)
															ArtGridItem(
																onClick = dropUnlessResumed {
																	backStack.add(
																		Screen.CollectionDetail(
																			album.id,
																			"artist"
																		)
																	)
																},
																onLongClick = { viewModel.selectAlbum(album) },
																coverArtId = album.coverArtId,
																title = album.name,
																subtitle = album.year?.toString(),
																id = album.id,
																tab = "artist"
															)
															if (selectedAlbum == album) {
																CollectionSheet(
																	onDismissRequest = { viewModel.clearAlbumSelection() },
																	collection = album,
																	starred = selectedAlbumIsStarred,
																	onShare = { shareId = album.id },
																	onPlayNext = { player.playNext(album) },
																	onAddToQueue = { player.addToQueue(album) },
																	onSetStarred = { viewModel.starAlbum(!selectedAlbumIsStarred) },
																	onAddAllToPlaylist = { playlistDialogShown = true },
																	downloadStatus = albumDownloadStatus,
																	onDownloadAll = {
																		scope.launch {
																			downloadManager.downloadCollection(album)
																			snackBarManager.notify(Res.string.notice_download_started)
																		}
																	},
																	onCancelDownloadAll = {
																		scope.launch {
																			album.songs.forEach {
																				downloadManager.cancelDownload(
																					it.id
																				)
																			}
																		}
																	},
																	onDeleteDownloadAll = {
																		scope.launch {
																			downloadManager.deleteDownloadedCollection(album)
																			snackBarManager.notify(Res.string.notice_deleted_download)
																		}
																	},
																	rating = selectedAlbumRating,
																	onSetRating = { viewModel.rateSelectedAlbum(it) }
																)
															}
														}
													}
													// keep a lone last album at half width
													if (rowAlbums.size == 1) Spacer(Modifier.weight(1f))
												}
											}
									}
								}
								if (state.similarArtists.isEmpty()) return@Column
								ArtCarousel(
									stringResource(Res.string.title_similar_artists),
									state.similarArtists.toImmutableList()
								) { artist ->
									ArtCarouselItem(
										coverArtId = artist.coverArtId,
										title = artist.name,
										subtitle = pluralStringResource(
											Res.plurals.count_albums,
											artist.albumCount,
											artist.albumCount
										),
										contentDescription = null,
										onClick = dropUnlessResumed {
											backStack.add(Screen.ArtistDetail(artist.id))
										}
									)
								}
							}
							Spacer(Modifier.height(contentPadding.calculateBottomPadding()))
						}
					}
				}
			}
		}

		ShareDialog(
			id = shareId,
			onIdClear = { shareId = null; viewModel.clearSelection() },
			expiry = shareExpiry,
			onExpiryChange = { shareExpiry = it }
		)

		if (playlistDialogShown) {
			PlaylistUpdateDialog(
				songs = selectedAlbum?.songs.orEmpty().toPersistentList(),
				onDismissRequest = { playlistDialogShown = false }
			)
		}
	}
}

fun truncateText(text: String, limit: Int): String {
	return if (text.length > limit) {
		text.take(limit) + "..."
	} else {
		text
	}
}
