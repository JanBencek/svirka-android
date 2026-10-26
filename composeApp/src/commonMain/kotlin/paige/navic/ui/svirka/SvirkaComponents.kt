package paige.navic.ui.svirka

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import paige.navic.LocalPlatformContext
import paige.navic.util.core.isLandscape
import paige.navic.util.ui.easedVerticalGradient
import kotlin.time.Duration

/*
 * Svirka web design system, ported (svirka/src/components, Tailwind → dp: 1 unit = 4dp).
 * Radius scale: rounded = 4dp, md = 8dp, lg = 10dp, xl = 14dp, 2xl = 18dp.
 */

object SvirkaShapes {
	val Sm = RoundedCornerShape(4.dp)
	val Md = RoundedCornerShape(8.dp)
	val Lg = RoundedCornerShape(10.dp)
	val Xl = RoundedCornerShape(14.dp)
	val Xxl = RoundedCornerShape(18.dp)
}

object SvirkaText {
	/** Page heading — text-2xl bold tracking-tight */
	val Heading: TextStyle
		@Composable get() = MaterialTheme.typography.headlineSmall.copy(
			fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.025).em
		)

	/** Detail page title (album/artist/playlist) — text-4xl bold, headlineLarge tier on phones */
	val DetailTitle: TextStyle
		@Composable get() = MaterialTheme.typography.headlineLarge.copy(
			fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.025).em
		)

	/** Section heading — text-lg semibold */
	val Section: TextStyle
		@Composable get() = MaterialTheme.typography.titleMedium.copy(
			fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold
		)

	/** "ALBUM" / "ARTIST" / "PLAYLIST" — text-xs semibold uppercase tracking-widest */
	val Eyebrow: TextStyle
		@Composable get() = MaterialTheme.typography.labelSmall.copy(
			fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.em
		)

	/** Row title — text-sm font-medium */
	val RowTitle: TextStyle
		@Composable get() = MaterialTheme.typography.bodyMedium.copy(
			fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium
		)

	/** Secondary text — text-sm muted */
	val Body: TextStyle
		@Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp)

	/** Small secondary text — text-xs */
	val Small: TextStyle
		@Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp)

	/** Durations/times — tabular-nums */
	val Tabular: TextStyle
		@Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontFeatureSettings = "tnum")
}

/** Svirka's muted-foreground. */
val mutedColor: Color
	@Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** "Play" pill: primary, rounded-full, filled play icon + label. */
@Composable
fun SvirkaPlayButton(
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	label: String = "Play",
	enabled: Boolean = true
) {
	Button(
		onClick = onClick,
		enabled = enabled,
		modifier = modifier.height(40.dp),
		shape = CircleShape,
		contentPadding = PaddingValues(horizontal = 20.dp),
		colors = ButtonDefaults.buttonColors(
			containerColor = MaterialTheme.colorScheme.primary,
			contentColor = MaterialTheme.colorScheme.onPrimary
		)
	) {
		Icon(SvirkaIcons.Play, null, Modifier.size(18.dp))
		Spacer(Modifier.width(8.dp))
		Text(label, style = SvirkaText.RowTitle)
	}
}

/** Ghost round icon button (Svirka's Shuffle next to Play, etc.). */
@Composable
fun SvirkaIconButton(
	icon: ImageVector,
	contentDescription: String?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	size: Dp = 40.dp,
	iconSize: Dp = 20.dp,
	tint: Color = MaterialTheme.colorScheme.onSurface
) {
	IconButton(onClick = onClick, modifier = modifier.size(size)) {
		Icon(icon, contentDescription, Modifier.size(iconSize), tint = tint)
	}
}

/** The Play pill + Shuffle row that heads every song list. */
@Composable
fun SvirkaPlayShuffleRow(
	onPlay: () -> Unit,
	onShuffle: (() -> Unit)?,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	trailing: @Composable RowScope.() -> Unit = {}
) {
	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		SvirkaPlayButton(onClick = onPlay, enabled = enabled)
		if (onShuffle != null) {
			SvirkaIconButton(SvirkaIcons.Shuffle, "Shuffle play", onShuffle)
		}
		Spacer(Modifier.weight(1f))
		trailing()
	}
}

/** Page heading with optional muted subtitle ("Songs", "Playlists", "Import" …). */
@Composable
fun SvirkaPageHeading(
	title: String,
	modifier: Modifier = Modifier,
	subtitle: String? = null,
	trailing: @Composable RowScope.() -> Unit = {}
) {
	Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		Column(Modifier.weight(1f)) {
			Text(title, style = SvirkaText.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
			if (subtitle != null) {
				Text(subtitle, style = SvirkaText.Body, color = mutedColor, modifier = Modifier.padding(top = 4.dp))
			}
		}
		trailing()
	}
}

/**
 * Album / artist / playlist header, Spotify mobile style: a big centred cover, then the eyebrow label, a big title,
 * an optional clickable subtitle (artist) and a muted meta line. Landscape keeps the cover beside the text.
 * [cover] receives the sized modifier (add the shared-element modifier after it).
 */
@Composable
fun SvirkaDetailHeader(
	eyebrow: String,
	title: String,
	modifier: Modifier = Modifier,
	subtitle: String? = null,
	onSubtitleClick: (() -> Unit)? = null,
	meta: String? = null,
	cover: @Composable (Modifier) -> Unit
) {
	if (LocalPlatformContext.current.isLandscape()) {
		Row(
			modifier = modifier.fillMaxWidth(),
			verticalAlignment = Alignment.Bottom,
			horizontalArrangement = Arrangement.spacedBy(16.dp)
		) {
			cover(Modifier.size(140.dp))
			SvirkaDetailHeaderText(eyebrow, title, subtitle, onSubtitleClick, meta, Modifier.weight(1f))
		}
	} else {
		Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
			cover(Modifier.size(200.dp))
			Spacer(Modifier.height(20.dp))
			SvirkaDetailHeaderText(eyebrow, title, subtitle, onSubtitleClick, meta, Modifier.fillMaxWidth())
		}
	}
}

@Composable
private fun SvirkaDetailHeaderText(
	eyebrow: String,
	title: String,
	subtitle: String?,
	onSubtitleClick: (() -> Unit)?,
	meta: String?,
	modifier: Modifier
) {
	Column(modifier) {
		Text(eyebrow.uppercase(), style = SvirkaText.Eyebrow, color = mutedColor)
		Spacer(Modifier.height(4.dp))
		Text(title, style = SvirkaText.DetailTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
		if (subtitle != null) {
			Text(
				subtitle,
				style = SvirkaText.Body.copy(fontWeight = FontWeight.Medium),
				color = if (onSubtitleClick != null) MaterialTheme.colorScheme.onSurface else mutedColor,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier
					.padding(top = 8.dp)
					.then(if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick) else Modifier)
			)
		}
		if (meta != null) {
			Text(meta, style = SvirkaText.Body, color = mutedColor, modifier = Modifier.padding(top = 2.dp))
		}
	}
}

/** Alpha of the cover colour at the top of a detail header's gradient (the web's `from-<cover>/50`). */
private const val HEADER_TINT_ALPHA = 0.5f

/**
 * Spotify-style wash behind a detail header: [color] at 50% at the top, easing into nothing at the bottom
 * of the element (or after [height]). Put it before any padding so it's full-bleed.
 */
fun Modifier.svirkaHeaderGradient(color: Color, height: Dp? = null): Modifier = drawBehind {
	drawRect(
		brush = Brush.easedVerticalGradient(
			easing = FastOutSlowInEasing,
			startY = 0f,
			endY = height?.toPx() ?: size.height,
			color = color.copy(alpha = HEADER_TINT_ALPHA)
		)
	)
}

/**
 * Top bar colour over a [svirkaHeaderGradient]: the gradient's top colour, turning solid surface as
 * [scrolled] goes from 0 to 1.
 */
@Composable
fun svirkaHeaderBarColor(color: Color, scrolled: Float): Color =
	lerp(MaterialTheme.colorScheme.surface, color, HEADER_TINT_ALPHA * (1f - scrolled.coerceIn(0f, 1f)))

/** Total length for a header's meta line: "42 min", "1 hr 5 min". */
fun Duration.svirkaLength(): String {
	val hours = inWholeHours
	val minutes = inWholeMinutes % 60
	return when {
		hours == 0L -> "${maxOf(1L, minutes)} min"
		minutes == 0L -> "$hours hr"
		else -> "$hours hr $minutes min"
	}
}

/** Action row under a detail header: [leading] actions (download …), then Shuffle and a big round Play. */
@Composable
fun SvirkaHeroPlayRow(
	onPlay: () -> Unit,
	onShuffle: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	leading: @Composable RowScope.() -> Unit = {}
) {
	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		leading()
		Spacer(Modifier.weight(1f))
		SvirkaIconButton(SvirkaIcons.Shuffle, "Shuffle play", onShuffle, size = 48.dp, iconSize = 24.dp)
		FilledIconButton(
			onClick = onPlay,
			enabled = enabled,
			shape = CircleShape,
			colors = IconButtonDefaults.filledIconButtonColors(
				containerColor = MaterialTheme.colorScheme.primary,
				contentColor = MaterialTheme.colorScheme.onPrimary
			),
			modifier = Modifier.size(56.dp)
		) {
			Icon(SvirkaIcons.Play, "Play", Modifier.size(26.dp))
		}
	}
}

/** Section heading inside a page ("Popular songs", "Albums", "Recent imports"). */
@Composable
fun SvirkaSectionTitle(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
	Row(modifier, verticalAlignment = Alignment.CenterVertically) {
		if (icon != null) {
			Icon(icon, null, Modifier.size(18.dp), tint = mutedColor)
			Spacer(Modifier.width(8.dp))
		}
		Text(text, style = SvirkaText.Section)
	}
}

/** Centred empty/error state: muted Music4 glyph, title, hint. */
@Composable
fun SvirkaEmptyState(
	title: String,
	modifier: Modifier = Modifier,
	hint: String? = null,
	icon: ImageVector = SvirkaIcons.Music4
) {
	Column(
		modifier = modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = 24.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Icon(icon, null, Modifier.size(48.dp), tint = mutedColor.copy(alpha = 0.4f))
		Spacer(Modifier.height(16.dp))
		Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
		if (hint != null) {
			Text(
				hint,
				style = SvirkaText.Body,
				color = mutedColor,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(top = 4.dp).widthIn(max = 384.dp)
			)
		}
	}
}

/** Heart toggle: outline + muted, or filled + primary when liked. */
@Composable
fun SvirkaLikeButton(
	liked: Boolean,
	onToggle: () -> Unit,
	modifier: Modifier = Modifier,
	size: Dp = 36.dp
) {
	SvirkaIconButton(
		icon = if (liked) SvirkaIcons.HeartFilled else SvirkaIcons.Heart,
		contentDescription = if (liked) "Unlike" else "Like",
		onClick = onToggle,
		modifier = modifier,
		size = size,
		iconSize = 18.dp,
		tint = if (liked) MaterialTheme.colorScheme.primary else mutedColor
	)
}

/** 3-bar pulsing equaliser shown on the playing row (2dp bars, 16dp tall, primary). */
@Composable
fun SvirkaEqualizer(playing: Boolean, modifier: Modifier = Modifier) {
	val transition = rememberInfiniteTransition(label = "eq")
	val bars = listOf(0 to 1f, 150 to 0.7f, 300 to 0.9f).map { (delay, rest) ->
		val anim by transition.animateFloat(
			initialValue = 0.35f,
			targetValue = 1f,
			animationSpec = infiniteRepeatable(tween(450, delayMillis = delay), RepeatMode.Reverse),
			label = "bar$delay"
		)
		if (playing) anim else rest
	}
	Row(
		modifier = modifier.height(16.dp),
		horizontalArrangement = Arrangement.spacedBy(2.dp),
		verticalAlignment = Alignment.Bottom
	) {
		bars.forEach { fraction ->
			Box(
				Modifier
					.width(2.dp)
					.fillMaxHeight(fraction)
					.background(MaterialTheme.colorScheme.primary, SvirkaShapes.Sm)
			)
		}
	}
}

/** Background for the currently playing row — bg-primary/10. */
val currentRowColor: Color
	@Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
