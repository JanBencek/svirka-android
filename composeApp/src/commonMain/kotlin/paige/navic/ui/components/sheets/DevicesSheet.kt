package paige.navic.ui.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_next_song
import navic.composeapp.generated.resources.action_pause
import navic.composeapp.generated.resources.action_play
import navic.composeapp.generated.resources.action_previous_song
import navic.composeapp.generated.resources.connect_alone
import navic.composeapp.generated.resources.connect_idle
import navic.composeapp.generated.resources.connect_offline
import navic.composeapp.generated.resources.connect_paused
import navic.composeapp.generated.resources.connect_play_on
import navic.composeapp.generated.resources.connect_playing
import navic.composeapp.generated.resources.connect_this_phone
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.domain.manager.ConnectDevice
import paige.navic.domain.manager.ConnectManager
import paige.navic.icons.Icons
import paige.navic.icons.filled.Pause
import paige.navic.icons.filled.Play
import paige.navic.icons.filled.SkipNext
import paige.navic.icons.filled.SkipPrevious
import paige.navic.icons.outlined.Devices

/** Svirka Connect: tap a device to move playback there; the buttons control it from here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesSheet(onDismissRequest: () -> Unit) {
	val connect = koinInject<ConnectManager>()
	val devices by connect.devices.collectAsStateWithLifecycle()

	ModalBottomSheet(
		onDismissRequest = onDismissRequest,
		sheetState = rememberBottomSheetState(
			initialValue = SheetValue.Hidden,
			enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
		),
		contentWindowInsets = {
			BottomSheetDefaults.modalWindowInsets.add(WindowInsets(left = 8.dp, right = 8.dp))
		}
	) {
		Column(
			modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
			verticalArrangement = Arrangement.spacedBy(4.dp)
		) {
			Text(
				text = stringResource(Res.string.connect_play_on),
				style = MaterialTheme.typography.titleLarge,
				modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
			)
			devices.sortedBy { it.id != connect.deviceId }.forEach { device ->
				DeviceRow(
					device = device,
					isThisPhone = device.id == connect.deviceId,
					connect = connect,
					onTransfer = {
						connect.transferTo(device.id)
						onDismissRequest()
					}
				)
			}
			val hint = when {
				devices.isEmpty() -> Res.string.connect_offline
				devices.size == 1 -> Res.string.connect_alone
				else -> null
			}
			hint?.let {
				Text(
					text = stringResource(it),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
				)
			}
		}
	}
}

@Composable
private fun DeviceRow(
	device: ConnectDevice,
	isThisPhone: Boolean,
	connect: ConnectManager,
	onTransfer: () -> Unit
) {
	val state = device.state
	val song = state?.song
	val playing = state?.playing == true
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onTransfer)
			.padding(horizontal = 16.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(
			imageVector = Icons.Outlined.Devices,
			contentDescription = null,
			tint = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
		)
		Column(Modifier.weight(1f).padding(start = 16.dp)) {
			Text(
				text = if (isThisPhone) stringResource(Res.string.connect_this_phone) else device.name,
				style = MaterialTheme.typography.bodyLarge,
				color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = when {
					song == null -> stringResource(Res.string.connect_idle)
					playing -> stringResource(Res.string.connect_playing, "${song.title} · ${song.artist}")
					else -> stringResource(Res.string.connect_paused, "${song.title} · ${song.artist}")
				},
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
		if (!isThisPhone && song != null) {
			IconButton(onClick = { connect.command(device.id, "prev") }) {
				Icon(Icons.Filled.SkipPrevious, stringResource(Res.string.action_previous_song))
			}
			IconButton(onClick = { connect.command(device.id, if (playing) "pause" else "play") }) {
				if (playing) Icon(Icons.Filled.Pause, stringResource(Res.string.action_pause))
				else Icon(Icons.Filled.Play, stringResource(Res.string.action_play))
			}
			IconButton(onClick = { connect.command(device.id, "next") }) {
				Icon(Icons.Filled.SkipNext, stringResource(Res.string.action_next_song))
			}
		}
	}
}
