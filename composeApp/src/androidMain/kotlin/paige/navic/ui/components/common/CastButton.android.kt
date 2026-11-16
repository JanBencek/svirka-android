package paige.navic.ui.components.common

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.media3.cast.MediaRouteButton
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
@Composable
actual fun CastButton() = MediaRouteButton()
