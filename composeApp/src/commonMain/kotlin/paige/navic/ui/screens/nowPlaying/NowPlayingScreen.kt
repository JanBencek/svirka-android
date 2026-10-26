package paige.navic.ui.screens.nowPlaying

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_lyrics
import navic.composeapp.generated.resources.action_navigate_back
import navic.composeapp.generated.resources.action_queue
import navic.composeapp.generated.resources.connect_devices
import navic.composeapp.generated.resources.title_now_playing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.settings.NowPlayingBackgroundStyle
import paige.navic.domain.models.settings.ToolbarPosition
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Devices
import paige.navic.icons.outlined.KeyboardArrowDown
import paige.navic.icons.outlined.List
import paige.navic.icons.outlined.Lyrics
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.BlendBackground
import paige.navic.ui.components.layouts.SheetScaffold
import paige.navic.ui.components.layouts.TopBarButton
import paige.navic.ui.components.sheets.DevicesSheet
import paige.navic.ui.components.toolbars.SheetActionButton
import paige.navic.ui.components.toolbars.SheetToolbar
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.nowPlaying.components.controls.NowPlayingArtworkPager
import paige.navic.ui.screens.nowPlaying.components.rows.NowPlayingControlsRow
import paige.navic.ui.screens.nowPlaying.viewmodels.NowPlayingViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NowPlayingScreen() {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val backStack = LocalNavStack.current

	// Only the Lyrics sheet hides the player under it. While Now Playing closes, the screen below is already on top
	// of the back stack, and the artwork has to stay to morph back into the mini player.
	val isPlayerCurrent = backStack.lastOrNull() !is Screen.Lyrics

	val playerState by player.uiState.collectAsStateWithLifecycle()
	val song = playerState.currentSong

	val viewModel = koinViewModel<NowPlayingViewModel> { parametersOf(player) }
	val songIsStarred by viewModel.songIsStarred.collectAsStateWithLifecycle()
	val songRating by viewModel.songRating.collectAsStateWithLifecycle()

	var devicesOpen by remember { mutableStateOf(false) }
	if (devicesOpen) DevicesSheet(onDismissRequest = { devicesOpen = false })

	val animateToDismiss = dropUnlessResumed {
		backStack.remove(Screen.NowPlaying)
	}

	// Swipe down to close is the back gesture turned on its side: it drives the same seekable transition, so the
	// screen below shows through and the artwork shrinks back toward the mini player under the finger. Let go past
	// a third of the way (or flick) and it closes; otherwise it springs back.
	val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
	val swipeInput = remember { DirectNavigationEventInput() }
	DisposableEffect(dispatcher) {
		dispatcher?.addInput(swipeInput)
		onDispose { dispatcher?.removeInput(swipeInput) }
	}
	var pageHeight by remember { mutableIntStateOf(1) }
	var dragged by remember { mutableFloatStateOf(0f) }
	var swiping by remember { mutableStateOf(false) }

	Box(
		Modifier
			.fillMaxSize()
			.onSizeChanged { pageHeight = it.height.coerceAtLeast(1) }
			.background(MaterialTheme.colorScheme.surface)
			.draggable(
				orientation = Orientation.Vertical,
				enabled = dispatcher != null,
				state = rememberDraggableState { delta ->
					dragged = (dragged + delta).coerceAtLeast(0f)
					if (!swiping && dragged > 0f) {
						swiping = true
						swipeInput.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_NONE))
					}
					if (swiping) {
						swipeInput.backProgressed(NavigationEvent(progress = (dragged / pageHeight).coerceIn(0f, 1f)))
					}
				},
				onDragStopped = { velocity ->
					if (swiping) {
						if (dragged / pageHeight > 0.3f || velocity > 2000f) swipeInput.backCompleted()
						else swipeInput.backCancelled()
					}
					swiping = false
					dragged = 0f
				}
			)
	) {
		SheetScaffold(
			toolbar = { windowInsets ->
				SheetToolbar(
					modifier = Modifier.alpha(if (isPlayerCurrent) 1f else 0f),
					windowInsets = windowInsets,
					title = {
						Text(stringResource(Res.string.title_now_playing))
					},
					navigationIcon = {
						TopBarButton(
							onClick = animateToDismiss,
							content = {
								Icon(
									imageVector = Icons.Outlined.KeyboardArrowDown,
									contentDescription = stringResource(Res.string.action_navigate_back)
								)
							}
						)
					},
					actions = {
						SheetActionButton(
							icon = Icons.Outlined.Lyrics,
							contentDescription = stringResource(Res.string.action_lyrics),
							onClick = dropUnlessResumed { backStack.add(Screen.Lyrics) },
							isStartRounded = true
						)
						SheetActionButton(
							icon = Icons.Outlined.List,
							contentDescription = stringResource(Res.string.action_queue),
							onClick = dropUnlessResumed { backStack.add(Screen.Queue) }
						)
						SheetActionButton(
							icon = Icons.Outlined.Devices,
							contentDescription = stringResource(Res.string.connect_devices),
							onClick = { devicesOpen = true },
							isEndRounded = true
						)
					}
				)
			}
		) { contentPadding ->
			Box(Modifier.fillMaxSize()) {
				if (preferenceManager.nowPlayingBackgroundStyle
					== NowPlayingBackgroundStyle.Dynamic
				) {
					BlendBackground(
						coverArtId = song?.coverArtId,
						isPaused = playerState.isPaused
					)
				}
				if (!isPlayerCurrent) return@Box
				BoxWithConstraints(
					modifier = Modifier
						.padding(horizontal = 8.dp)
						.fillMaxSize()
				) {
					val isLandscape = maxWidth > maxHeight
					val toolbarPosition = preferenceManager.nowPlayingToolbarPosition
					val padding = when {
						isLandscape -> contentPadding
						toolbarPosition == ToolbarPosition.Top -> contentPadding.plus(
							PaddingValues(
								bottom = 40.dp
							)
						)

						toolbarPosition == ToolbarPosition.Bottom -> contentPadding.plus(
							PaddingValues(
								top = 40.dp
							)
						)

						else -> contentPadding
					}
					if (isLandscape) {
						Row(
							modifier = Modifier.fillMaxSize().padding(padding),
							horizontalArrangement = Arrangement.SpaceEvenly,
							verticalAlignment = Alignment.CenterVertically
						) {
							NowPlayingArtworkPager(
								modifier = Modifier.weight(1f).fillMaxHeight(),
								isLandscape = true
							)
							NowPlayingControlsRow(
								modifier = Modifier.weight(1f).fillMaxHeight(),
								isLandscape = true,
								songIsStarred = songIsStarred,
								onSetSongIsStarred = { viewModel.starSong(it) },
								songRating = songRating,
								onSetSongRating = { viewModel.rateSong(it) }
							)
						}
					} else {
						Column(
							modifier = Modifier.fillMaxSize().padding(padding),
							horizontalAlignment = Alignment.CenterHorizontally,
							verticalArrangement = Arrangement.Center
						) {
							NowPlayingArtworkPager(
								modifier = Modifier.weight(1f).fillMaxWidth(),
								isLandscape = false
							)
							NowPlayingControlsRow(
								modifier = Modifier.weight(1f),
								isLandscape = false,
								songIsStarred = songIsStarred,
								onSetSongIsStarred = { viewModel.starSong(it) },
								songRating = songRating,
								onSetSongRating = { viewModel.rateSong(it) }
							)
						}
					}
				}
			}
		}
	}
}
