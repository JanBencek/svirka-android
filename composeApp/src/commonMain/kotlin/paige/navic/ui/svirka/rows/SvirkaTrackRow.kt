package paige.navic.ui.svirka.rows

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_download_failed
import navic.composeapp.generated.resources.info_downloaded
import navic.composeapp.generated.resources.info_explicit
import navic.composeapp.generated.resources.info_not_available_offline
import org.jetbrains.compose.resources.stringResource
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Check
import paige.navic.icons.outlined.DownloadOff
import paige.navic.icons.outlined.Lock
import paige.navic.icons.outlined.Offline
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.svirka.SvirkaEqualizer
import paige.navic.ui.svirka.SvirkaIcons
import paige.navic.ui.svirka.SvirkaShapes
import paige.navic.ui.svirka.SvirkaText
import paige.navic.ui.svirka.currentRowColor
import paige.navic.ui.svirka.mutedColor
import paige.navic.util.core.InlineExplicitIcon
import paige.navic.util.core.toHoursMinutesSeconds
import kotlin.time.Duration

/**
 * Svirka's flat song row (web SongRow): 8dp radius, 12/8dp padding,
 * leading cell, title + muted subtitle, trailing indicators + tabular duration.
 * Opaque surface background so swipe backgrounds never show through.
 */
@Composable
fun SvirkaTrackRow(
	title: AnnotatedString,
	subtitle: AnnotatedString,
	isCurrent: Boolean,
	onClick: () -> Unit,
	onLongClick: (() -> Unit)?,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	duration: Duration? = null,
	durationStyle: TextStyle = SvirkaText.Tabular,
	background: Color = MaterialTheme.colorScheme.surface,
	leading: @Composable () -> Unit,
	trailing: @Composable () -> Unit = {}
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.clip(SvirkaShapes.Md)
			.background(background)
			.then(if (isCurrent) Modifier.background(currentRowColor) else Modifier)
			.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
			.alpha(if (enabled) 1f else .5f)
			.padding(horizontal = 12.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		leading()
		Spacer(Modifier.width(12.dp))
		Column(Modifier.weight(1f)) {
			Text(
				text = title,
				inlineContent = InlineExplicitIcon,
				style = SvirkaText.RowTitle,
				color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = subtitle,
				style = SvirkaText.Small,
				color = mutedColor,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
		Row(
			modifier = Modifier.padding(start = 8.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			trailing()
			if (duration != null) {
				Text(
					text = duration.toHoursMinutesSeconds(),
					style = durationStyle,
					color = mutedColor,
					textAlign = TextAlign.End,
					maxLines = 1,
					modifier = Modifier.widthIn(min = 40.dp)
				)
			}
		}
	}
}

/** 40dp cover thumb; on the current track a scrim + equaliser sits over it. */
@Composable
fun SvirkaRowCover(coverArtId: String?, playbackState: Boolean?) {
	Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
		CoverArt(
			coverArtId = coverArtId,
			modifier = Modifier.size(40.dp),
			thumbnail = true,
			shape = SvirkaShapes.Sm
		)
		if (playbackState != null) {
			Box(
				Modifier
					.matchParentSize()
					.background(Color.Black.copy(alpha = .55f), SvirkaShapes.Sm),
				contentAlignment = Alignment.Center
			) {
				SvirkaEqualizer(playing = !playbackState)
			}
		}
	}
}

/** Album-page leading cell: muted tabular track number, equaliser when current. */
@Composable
fun SvirkaRowTrackNumber(number: Int, playbackState: Boolean?) {
	Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
		if (playbackState != null) {
			SvirkaEqualizer(playing = !playbackState)
		} else {
			Text("$number", style = SvirkaText.Tabular, color = mutedColor, maxLines = 1)
		}
	}
}

/** Trailing indicators: liked heart, explicit lock, offline, download status. */
@Composable
fun SvirkaRowIndicators(
	starred: Boolean,
	download: DownloadEntity?,
	explicitLocked: Boolean = false,
	maybeUnavailable: Boolean = false
) {
	if (starred) {
		Icon(SvirkaIcons.HeartFilled, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
	}
	if (explicitLocked) {
		Icon(Icons.Outlined.Lock, stringResource(Res.string.info_explicit), Modifier.size(16.dp), tint = mutedColor)
	}
	if (maybeUnavailable) {
		Icon(Icons.Outlined.Offline, stringResource(Res.string.info_not_available_offline), Modifier.size(16.dp), tint = mutedColor)
	}
	when (download?.status) {
		DownloadStatus.DOWNLOADING -> CircularProgressIndicator(
			progress = { download?.progress ?: 0f },
			modifier = Modifier.size(16.dp),
			strokeWidth = 2.dp
		)

		DownloadStatus.DOWNLOADED -> Icon(
			Icons.Outlined.Check,
			contentDescription = stringResource(Res.string.info_downloaded),
			modifier = Modifier.size(16.dp),
			tint = MaterialTheme.colorScheme.primary
		)

		DownloadStatus.FAILED -> Icon(
			Icons.Outlined.DownloadOff,
			contentDescription = stringResource(Res.string.info_download_failed),
			modifier = Modifier.size(16.dp),
			tint = MaterialTheme.colorScheme.error
		)

		else -> {}
	}
}
