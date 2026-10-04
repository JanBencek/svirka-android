package paige.navic.ui.screens.queue.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_remove_from_queue
import navic.composeapp.generated.resources.action_reorder
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.settings.ExplicitContentPlayback
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Delete
import paige.navic.icons.outlined.DragHandle
import paige.navic.ui.navigation.Screen
import paige.navic.ui.svirka.SvirkaShapes
import paige.navic.ui.svirka.SvirkaText
import paige.navic.ui.svirka.mutedColor
import paige.navic.ui.svirka.rows.SvirkaRowCover
import paige.navic.ui.svirka.rows.SvirkaRowIndicators
import paige.navic.ui.svirka.rows.SvirkaTrackRow
import paige.navic.util.core.buildSongInfoString
import paige.navic.util.core.toHoursMinutesSeconds
import paige.navic.util.ui.DraggableListState
import paige.navic.util.ui.dragHandle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QueueScreenItem(
	index: Int,
	count: Int,
	song: DomainSong,
	isPlaying: Boolean,
	isSelected: Boolean,
	isDragging: Boolean,
	draggableState: DraggableListState,
	onClick: () -> Unit,
	onRemove: () -> Unit,
	isOffline: Boolean = false,
	isDownloaded: Boolean = false
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val isExplicit = song.explicitStatus == DomainExplicitStatus.Explicit
		&& preferenceManager.explicitContentPlayback != ExplicitContentPlayback.Allowed
	val maybeUnavailable = isOffline && !isDownloaded

	val elevation by animateDpAsState(
		targetValue = if (isDragging) 8.dp else 0.dp,
		animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
	)

	val dismissState = rememberSwipeToDismissBoxState()
	val scope = rememberCoroutineScope()

	val backStack = LocalNavStack.current

	SwipeToDismissBox(
		state = dismissState,
		onDismiss = {
			onRemove()
			scope.launch {
				dismissState.reset()
			}
		},
		backgroundContent = {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.clip(SvirkaShapes.Md)
					.background(MaterialTheme.colorScheme.errorContainer)
					.padding(horizontal = 20.dp)
			) {
				Icon(
					imageVector = Icons.Outlined.Delete,
					contentDescription = stringResource(Res.string.action_remove_from_queue),
					tint = MaterialTheme.colorScheme.onErrorContainer,
					modifier = Modifier.align(
						when (dismissState.dismissDirection) {
							SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
							else -> Alignment.CenterEnd
						}
					)
				)
			}
		},
		content = {
			SvirkaTrackRow(
				modifier = Modifier.shadow(elevation, SvirkaShapes.Md),
				title = AnnotatedString(song.title),
				subtitle = buildSongInfoString(
					song = song,
					onClickArtist = {
						backStack.remove(Screen.Queue)
						backStack.remove(Screen.NowPlaying)
						backStack.add(Screen.ArtistDetail(it))
					},
					showAlbum = false,
					showYear = false
				),
				isCurrent = isSelected,
				onClick = onClick,
				onLongClick = null,
				enabled = !isExplicit,
				background = if (isDragging) MaterialTheme.colorScheme.surfaceContainer
				else MaterialTheme.colorScheme.surface,
				leading = {
					SvirkaRowCover(song.coverArtId, if (isSelected) !isPlaying else null)
				},
				trailing = {
					SvirkaRowIndicators(
						starred = false,
						download = null,
						explicitLocked = isExplicit,
						maybeUnavailable = maybeUnavailable
					)
					Text(
						text = song.duration.toHoursMinutesSeconds(),
						style = SvirkaText.Small.copy(fontFeatureSettings = "tnum"),
						color = mutedColor,
						maxLines = 1
					)
					IconButton(
						modifier = Modifier
							.size(32.dp)
							.dragHandle(
								state = draggableState,
								index = index
							),
						onClick = {}
					) {
						Icon(
							Icons.Outlined.DragHandle,
							contentDescription = stringResource(Res.string.action_reorder),
							modifier = Modifier.size(20.dp),
							tint = mutedColor
						)
					}
				}
			)
		}
	)
}
