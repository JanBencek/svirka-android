package paige.navic.ui.svirka

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.domain.manager.ConnectManager
import paige.navic.ui.navigation.Screen

/** Opens Friends over whatever is open (Now Playing and the queue close first). */
fun NavBackStack<NavKey>.openFriends() {
	remove(Screen.Queue)
	remove(Screen.NowPlaying)
	if (lastOrNull() !is Screen.Friends) add(Screen.Friends(nested = true))
}

/** True while this phone plays along in a Jam whose host keeps play/pause/skip/seek to themselves. */
@Composable
fun jamLocksControls(): Boolean {
	val connect = koinInject<ConnectManager>()
	val jam by connect.jam.collectAsStateWithLifecycle()
	return jam?.locks(connect.deviceId) == true
}

/** Now Playing: who's in the Jam, and (for a guest without control) why the buttons are off. Tap: Friends. */
@Composable
fun SvirkaJamBadge(modifier: Modifier = Modifier) {
	val connect = koinInject<ConnectManager>()
	val view by connect.jam.collectAsStateWithLifecycle()
	val jam = view ?: return
	val backStack = LocalNavStack.current
	val company = jam.members.filter { it != jam.you }
	val text = buildString {
		append(if (jam.host == jam.you) "Your Jam" else "In ${jam.host}'s Jam")
		if (company.isNotEmpty()) append(" with " + company.joinToString(", "))
		if (jam.locks(connect.deviceId)) append(" · Only ${jam.host} controls playback")
	}
	Row(
		modifier = modifier
			.clip(SvirkaShapes.Md)
			.clickable { backStack.openFriends() }
			.padding(horizontal = 8.dp, vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(SvirkaIcons.Users, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
		Text(text, style = SvirkaText.Small, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
	}
}

/** Mini player: a Jam icon while in one; tap: Friends. */
@Composable
fun SvirkaJamButton() {
	val connect = koinInject<ConnectManager>()
	val jam by connect.jam.collectAsStateWithLifecycle()
	if (jam == null) return
	val backStack = LocalNavStack.current
	IconButton(onClick = { backStack.openFriends() }, modifier = Modifier.size(32.dp)) {
		Icon(SvirkaIcons.Users, "Jam", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
	}
}
