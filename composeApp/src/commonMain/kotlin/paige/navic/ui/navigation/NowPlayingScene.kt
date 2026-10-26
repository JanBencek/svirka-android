@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("UNCHECKED_CAST")

package paige.navic.ui.navigation

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay.popTransitionSpec
import androidx.navigation3.ui.NavDisplay.predictivePopTransitionSpec
import androidx.navigation3.ui.NavDisplay.transitionSpec
import com.kyant.capsule.ContinuousRoundedRectangle
import paige.navic.LocalSharedTransitionScope
import paige.navic.ui.components.sheets.ModalBottomSheet
import paige.navic.ui.theme.NavicTheme
import paige.navic.util.ui.EmphasizedAccelerateEasing
import paige.navic.util.ui.EmphasizedDecelerateEasing
import paige.navic.util.ui.LocalSheetState
import paige.navic.util.ui.rememberColorSchemeForCurrentSong
import paige.navic.util.ui.rememberScreenCornerRadius

/**
 * Now Playing itself is a full-screen page, not this sheet: a ModalBottomSheet draws in its own window, where
 * the mini player's cover can't be a shared element with the artwork. It slides up over the screen it was opened
 * from (which stays put underneath) while the cover morphs; back, predictive back or a swipe down reverse it.
 * The sheet below is still used for Lyrics.
 */
@OptIn(ExperimentalAnimationApi::class) // KeepUntilTransitionsFinished
val nowPlayingTransitions = transitionSpec {
	ContentTransform(
		slideInVertically(tween(400, easing = EmphasizedDecelerateEasing)) { it },
		ExitTransition.KeepUntilTransitionsFinished
	)
} + popTransitionSpec {
	ContentTransform(
		EnterTransition.None,
		slideOutVertically(tween(400, easing = EmphasizedAccelerateEasing)) { it }
	)
} + predictivePopTransitionSpec {
	ContentTransform(EnterTransition.None, slideOutVertically { it })
}

/** The mini player's cover and the Now Playing artwork of the same queue row: one element that morphs between them. */
@Composable
fun Modifier.nowPlayingCover(songId: String): Modifier = with(LocalSharedTransitionScope.current) {
	this@nowPlayingCover.sharedElement(
		sharedContentState = rememberSharedContentState("now-playing-$songId-cover"),
		boundsTransform = BoundsTransform { _, _ ->
			tween(durationMillis = 400, easing = EmphasizedDecelerateEasing)
		},
		animatedVisibilityScope = LocalNavAnimatedContentScope.current
	)
}

class NowPlayingScene<T : Any>(
	override val key: Any,
	private val entry: NavEntry<T>,
	override val previousEntries: List<NavEntry<T>>,
	override val overlaidEntries: List<NavEntry<T>>,
	private val maxWidth: Dp,
	private val isTransparent: Boolean,
	private val onBack: () -> Unit
) : OverlayScene<T> {

	override val entries = listOf(entry)

	lateinit var sheetState: SheetState

	override val content = @Composable {
		sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
		val lifecycleOwner = rememberLifecycleOwner()
		val screenCornerRadius = rememberScreenCornerRadius()

		// TODO: see if there's a way to do this w out private api
		@Suppress("INVISIBLE_REFERENCE")
		val expandProgress = sheetState.anchoredDraggableState.progress(
			from = SheetValue.Hidden,
			to = SheetValue.Expanded
		)
		val shape = remember(expandProgress, screenCornerRadius) {
			if (expandProgress == 1f) {
				RectangleShape
			} else {
				ContinuousRoundedRectangle(
					topStart = screenCornerRadius,
					topEnd = screenCornerRadius
				)
			}
		}

		NavicTheme(rememberColorSchemeForCurrentSong()) {
			ModalBottomSheet(
				containerColor = if (isTransparent) {
					Color.Transparent
				} else {
					MaterialTheme.colorScheme.surface
				},
				onDismissRequest = onBack,
				sheetState = sheetState,
				sheetMaxWidth = maxWidth,
				contentWindowInsets = { WindowInsets() },
				dragHandle = null,
				shape = shape
			) {
				CompositionLocalProvider(
					LocalLifecycleOwner provides lifecycleOwner,
					LocalSheetState provides sheetState
				) {
					Box(Modifier.fillMaxSize()) {
						entry.Content()
					}
				}
			}
		}
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other == null || this::class != other::class) return false

		other as NowPlayingScene<*>

		return key == other.key
			&& entry == other.entry
			&& maxWidth == other.maxWidth
			&& isTransparent == other.isTransparent
	}

	override fun hashCode() = key.hashCode() * 31 +
		entry.hashCode() * 31 +
		maxWidth.hashCode() * 31 +
		isTransparent.hashCode() * 31

	// onRemove is intentionally not used, see the comment
	// on LocalSheetState
}

class NowPlayingSceneStrategy<T : Any> : SceneStrategy<T> {

	override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
		val entry = entries.lastOrNull() ?: return null
		val maxWidth = entry.metadata[MaxWidthKey] ?: return null
		val isTransparent = entry.metadata[IsTransparentKey] ?: return null

		return NowPlayingScene(
			key = entry.contentKey as T,
			entry = entry,
			previousEntries = entries.dropLast(1),
			overlaidEntries = entries.dropLast(1),
			maxWidth = maxWidth,
			isTransparent = isTransparent,
			onBack = onBack
		)
	}

	companion object {
		object MaxWidthKey : NavMetadataKey<Dp>
		object IsTransparentKey : NavMetadataKey<Boolean>

		fun bottomSheet(
			maxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
			isTransparent: Boolean = false
		) = metadata {
			put(MaxWidthKey, maxWidth)
			put(IsTransparentKey, isTransparent)
		}
	}
}
