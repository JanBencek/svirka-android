package paige.navic.ui.svirka.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import paige.navic.ui.svirka.SvirkaIcons
import paige.navic.ui.svirka.SvirkaShapes

/** Web playlist artwork: primary/30 → primary/10 gradient square with a centred ListMusic glyph. */
@Composable
fun SvirkaPlaylistCover(iconSize: Dp, modifier: Modifier = Modifier) {
	val primary = MaterialTheme.colorScheme.primary
	Box(
		modifier = modifier
			.clip(SvirkaShapes.Md)
			.background(Brush.linearGradient(listOf(primary.copy(alpha = 0.3f), primary.copy(alpha = 0.1f)))),
		contentAlignment = Alignment.Center
	) {
		Icon(SvirkaIcons.ListMusic, null, Modifier.size(iconSize), tint = primary)
	}
}
