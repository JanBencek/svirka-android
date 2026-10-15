package paige.navic.ui.svirka

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.title_friends
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.ConnectManager
import paige.navic.domain.manager.Friend
import paige.navic.domain.manager.Friends
import paige.navic.domain.manager.JamView
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.ShareManager
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.util.core.isLandscape
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Svirka Friends: the Jam card, who you follow and what they play, and people to follow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(nested: Boolean = false) {
	val connect = koinInject<ConnectManager>()
	val shareManager = koinInject<ShareManager>()
	val jam by connect.jam.collectAsStateWithLifecycle()
	var data by remember { mutableStateOf<Friends?>(null) }
	var error by remember { mutableStateOf<String?>(null) }
	var query by remember { mutableStateOf("") }
	val scope = rememberCoroutineScope()
	val lifecycle = LocalLifecycleOwner.current.lifecycle

	suspend fun update(call: suspend () -> Friends) {
		try {
			data = call()
			error = null
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			error = "Couldn't load your friends (${e.message ?: "offline"})"
		}
	}

	fun follow(user: String, on: Boolean) {
		scope.launch { update { connect.follow(user, on, query.trim()) } }
	}

	// Refresh every 15 s while the screen is visible, and when the search changes (once typing pauses).
	LaunchedEffect(lifecycle, query) {
		if (query.isNotEmpty()) delay(250)
		lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
			while (true) {
				update { connect.friends(query.trim()) }
				delay(15.seconds)
			}
		}
	}

	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	Scaffold(
		topBar = {
			if (!nested) {
				RootTopBar(
					title = { Text(stringResource(Res.string.title_friends)) },
					scrollBehavior = scrollBehavior
				)
			} else {
				NestedTopBar({ Text(stringResource(Res.string.title_friends)) })
			}
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			val preferVisible = preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
			if (!nested || (!platformContext.isLandscape() && preferVisible)) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { contentPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.then(
					if (!nested) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
					else Modifier
				),
			contentPadding = PaddingValues(
				start = 16.dp,
				end = 16.dp,
				top = contentPadding.calculateTopPadding() + 4.dp,
				bottom = contentPadding.calculateBottomPadding() + 16.dp
			),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			item {
				JamCard(
					jam = jam,
					thisDevice = connect.deviceId,
					onStart = connect::startJam,
					onLeave = connect::leaveJam,
					onShare = { id -> scope.launch { shareManager.shareString("https://music.bencit.com/jam/$id") } }
				)
			}
			error?.let { message ->
				item { Text(message, color = MaterialTheme.colorScheme.error, style = SvirkaText.Body) }
			}
			item {
				SvirkaSectionTitle("Friends", Modifier.padding(top = 16.dp), SvirkaIcons.Users)
			}
			val friends = data?.friends.orEmpty()
			if (data != null && friends.isEmpty()) {
				item {
					SvirkaEmptyState(
						title = "No friends yet",
						hint = "Search for someone below and follow them to see what they listen to.",
						icon = SvirkaIcons.Users
					)
				}
			}
			items(friends, key = { "friend:" + it.user }) { friend ->
				FriendRow(
					friend = friend,
					inThatJam = friend.jam != null && friend.jam == jam?.id,
					onJoin = { friend.jam?.let(connect::joinJam) },
					onInvite = if (jam != null) ({ connect.inviteToJam(friend.user) }) else null,
					onUnfollow = { follow(friend.user, false) }
				)
			}
			item {
				SvirkaSectionTitle("Add friend", Modifier.padding(top = 16.dp), SvirkaIcons.Plus)
			}
			item {
				OutlinedTextField(
					value = query,
					onValueChange = { query = it },
					modifier = Modifier.fillMaxWidth(),
					singleLine = true,
					shape = SvirkaShapes.Lg,
					textStyle = SvirkaText.Body,
					placeholder = { Text("Search by username", style = SvirkaText.Body, color = mutedColor) },
					leadingIcon = { Icon(SvirkaIcons.Search, null, Modifier.size(16.dp), tint = mutedColor) },
					trailingIcon = if (query.isNotEmpty()) {
						{
							IconButton(onClick = { query = "" }) {
								Icon(SvirkaIcons.CircleX, "Clear search", Modifier.size(16.dp), tint = mutedColor)
							}
						}
					} else null
				)
			}
			val others = data?.others.orEmpty()
			if (query.trim().length >= 2 && data != null && others.isEmpty()) {
				item { Text("Nobody found.", style = SvirkaText.Body, color = mutedColor) }
			}
			items(others, key = { "other:$it" }) { user ->
				Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
					Text(user, style = SvirkaText.RowTitle, modifier = Modifier.weight(1f))
					OutlinedButton(onClick = { follow(user, true) }) { Text("Follow") }
				}
			}
		}
	}
}

/** Svirka card: rounded-lg, 1dp border, bg-background, p-4. */
@Composable
private fun JamCard(
	jam: JamView?,
	thisDevice: String,
	onStart: () -> Unit,
	onLeave: () -> Unit,
	onShare: (String) -> Unit
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.border(1.dp, MaterialTheme.colorScheme.outlineVariant, SvirkaShapes.Lg)
			.background(MaterialTheme.colorScheme.background, SvirkaShapes.Lg)
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		when {
			jam == null -> {
				Text("Jam", style = SvirkaText.Section)
				Text(
					"Listen together: friends who join hear what you play and can add songs to your queue.",
					style = SvirkaText.Body,
					color = mutedColor
				)
				Button(onClick = onStart) { Text("Start a Jam") }
			}

			jam.host == jam.you -> {
				Text("Your Jam", style = SvirkaText.Section)
				Text(
					if (jam.members.isEmpty()) "Nobody has joined yet. Invite friends below or share the link."
					else "With " + jam.members.joinToString(", "),
					style = SvirkaText.Body,
					color = mutedColor
				)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					Button(onClick = { onShare(jam.id) }) {
						Icon(SvirkaIcons.Share, null, Modifier.size(18.dp))
						Spacer(Modifier.width(8.dp))
						Text("Share")
					}
					OutlinedButton(onClick = onLeave) { Text("End Jam") }
				}
			}

			else -> {
				Text("In ${jam.host}'s Jam", style = SvirkaText.Section)
				Text(
					listOfNotNull(
						"With " + (listOf(jam.host) + jam.members).joinToString(", "),
						"Playing along on another device".takeIf { jam.listener != thisDevice }
					).joinToString(" · "),
					style = SvirkaText.Body,
					color = mutedColor
				)
				OutlinedButton(onClick = onLeave) { Text("Leave") }
			}
		}
	}
}

@Composable
private fun FriendRow(
	friend: Friend,
	inThatJam: Boolean,
	onJoin: () -> Unit,
	onInvite: (() -> Unit)?, // null: not in a Jam
	onUnfollow: () -> Unit
) {
	val now = friend.now
	var menu by remember { mutableStateOf(false) }
	Box {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.combinedClickable(onClick = {}, onLongClick = { menu = true })
				.padding(vertical = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp)
		) {
			CoverArt(
				coverArtId = now?.song?.coverArt,
				thumbnail = true,
				shape = SvirkaShapes.Sm,
				modifier = Modifier.size(48.dp)
			)
			Column(Modifier.weight(1f)) {
				Text(friend.user, style = SvirkaText.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
				now?.let {
					Text(
						"${it.song.title} — ${it.song.artist}",
						style = SvirkaText.Small,
						color = mutedColor,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
				val playing = now?.playing == true && friend.online
				Text(
					text = when {
						playing -> "Playing now"
						now != null -> ago(now.at)
						friend.online -> "Online"
						else -> "Offline"
					},
					style = SvirkaText.Small,
					color = if (playing) MaterialTheme.colorScheme.primary else mutedColor
				)
			}
			if (friend.jam != null && !inThatJam) {
				TextButton(onClick = onJoin) { Text("Join Jam") }
			} else if (onInvite != null && !inThatJam) {
				TextButton(onClick = onInvite) { Text("Invite") }
			}
		}
		DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
			DropdownMenuItem(
				text = { Text("Unfollow ${friend.user}") },
				onClick = {
					menu = false
					onUnfollow()
				}
			)
		}
	}
}

private fun ago(at: Long): String {
	val minutes = (Clock.System.now().toEpochMilliseconds() - at) / 60_000
	return when {
		minutes < 1 -> "Just now"
		minutes < 60 -> "$minutes min ago"
		minutes < 24 * 60 -> "${minutes / 60} h ago"
		else -> "${minutes / (24 * 60)} d ago"
	}
}
