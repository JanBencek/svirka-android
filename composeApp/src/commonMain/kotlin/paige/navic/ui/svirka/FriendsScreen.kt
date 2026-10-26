package paige.navic.ui.svirka

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.friends_not_synced
import navic.composeapp.generated.resources.jam_error
import navic.composeapp.generated.resources.title_friends
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.ConnectManager
import paige.navic.domain.manager.ConnectSong
import paige.navic.domain.manager.Friend
import paige.navic.domain.manager.Friends
import paige.navic.domain.manager.Hidden
import paige.navic.domain.manager.InboxItem
import paige.navic.domain.manager.JamView
import paige.navic.domain.manager.PermissionManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.REACTIONS
import paige.navic.domain.manager.ShareManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.domain.manager.TasteMatch
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.util.core.isLandscape
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Svirka Friends: who sees what you play, the Jam card, songs friends sent you, who you follow and what they play,
 * who follows you, and people to follow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(nested: Boolean = false) {
	val connect = koinInject<ConnectManager>()
	val shareManager = koinInject<ShareManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val snackBar = koinInject<SnackBarManager>()
	val jam by connect.jam.collectAsStateWithLifecycle()
	val lastInbox by connect.lastInbox.collectAsStateWithLifecycle()
	var comparing by remember { mutableStateOf<String?>(null) }
	var data by remember { mutableStateOf<Friends?>(null) }
	var error by remember { mutableStateOf<String?>(null) }
	var query by remember { mutableStateOf("") }
	val scope = rememberCoroutineScope()
	val lifecycle = LocalLifecycleOwner.current.lifecycle
	val permissions = koinInject<PermissionManager>()

	// Jam invites reach this phone as notifications while Svirka is in the background (Android 13+ asks first)
	LaunchedEffect(Unit) { permissions.requestNotificationPermission() }

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

	/** A friends request whose failure (the server's reason) goes to the snack bar. */
	fun act(call: suspend () -> Friends) {
		scope.launch {
			try {
				data = call()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				snackBar.notify(Res.string.jam_error, e.message ?: "Couldn't reach Svirka")
			}
		}
	}

	/** Plays or queues ([how]) a song a friend plays or sent, once it's in this phone's library. */
	fun play(song: ConnectSong, how: (DomainSong) -> Unit) {
		scope.launch { connect.librarySong(song)?.let(how) ?: snackBar.notify(Res.string.friends_not_synced) }
	}

	// Refresh every 15 s while the screen is visible, when the search changes (once typing pauses), and when a
	// friend sends something.
	LaunchedEffect(lifecycle, query, lastInbox) {
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
			data?.let { friends ->
				item { PrivateListening(friends.me.hidden) { mode -> act { connect.setPrivate(mode, query.trim()) } } }
			}
			item {
				JamCard(
					jam = jam,
					thisDevice = connect.deviceId,
					onStart = connect::startJam,
					onLeave = connect::leaveJam,
					onShare = { id -> scope.launch { shareManager.shareString("https://music.bencit.com/jam/$id") } },
					onGuestsControl = connect::jamGuestsControl,
					onRemove = connect::removeFromJam
				)
			}
			error?.let { message ->
				item { Text(message, color = MaterialTheme.colorScheme.error, style = SvirkaText.Body) }
			}
			val inbox = data?.inbox.orEmpty()
			if (inbox.isNotEmpty()) {
				item {
					SvirkaSectionTitle("Shared with you", Modifier.padding(top = 16.dp), SvirkaIcons.Send)
				}
			}
			items(inbox, key = { "inbox:" + it.id }) { item ->
				InboxRow(
					item = item,
					onPlay = { play(item.song) { player.playNow(it) } },
					onDismiss = { act { connect.dismiss(item.id, query.trim()) } }
				)
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
				val song = friend.now?.song
				FriendRow(
					friend = friend,
					inThatJam = friend.jam != null && friend.jam == jam?.id,
					onJoin = { friend.jam?.let(connect::joinJam) },
					onInvite = if (jam != null) ({ connect.inviteToJam(friend.user) }) else null,
					onUnfollow = { follow(friend.user, false) },
					onPlay = { song?.let { s -> play(s) { player.playNow(it) } } },
					onPlayNext = { song?.let { s -> play(s) { player.playNextSingle(it) } } },
					onAddToQueue = { song?.let { s -> play(s) { player.addToQueueSingle(it) } } },
					onReact = { emoji -> song?.let { connect.send(friend.user, it, emoji) } },
					onCompare = { comparing = friend.user }
				)
			}
			val followers = data?.followers.orEmpty()
			if (followers.isNotEmpty()) {
				item {
					SvirkaSectionTitle("Follow you", Modifier.padding(top = 16.dp), SvirkaIcons.UserPlus)
				}
			}
			items(followers, key = { "follower:$it" }) { user ->
				Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
					Text(user, style = SvirkaText.RowTitle, modifier = Modifier.weight(1f))
					OutlinedButton(onClick = { follow(user, true) }) { Text("Follow back") }
				}
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

	comparing?.let { user -> CompareDialog(user, connect::compare) { comparing = null } }
}

/** How your listening overlaps with [user]'s. */
@Composable
private fun CompareDialog(user: String, compare: suspend (String) -> TasteMatch, onDismiss: () -> Unit) {
	var result by remember(user) { mutableStateOf<Result<TasteMatch>?>(null) }
	LaunchedEffect(user) { result = runCatching { compare(user) } }
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("You and $user") },
		text = {
			val taste = result?.getOrNull()
			when {
				result == null -> Text("Comparing…", style = SvirkaText.Body, color = mutedColor)
				taste == null -> Text(
					result?.exceptionOrNull()?.message ?: "Couldn't compare",
					style = SvirkaText.Body,
					color = MaterialTheme.colorScheme.error
				)
				!taste.enough -> Text(
					"Not enough listening yet — it fills in as you both play music.",
					style = SvirkaText.Body,
					color = mutedColor
				)
				else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text("${taste.score}% match", style = SvirkaText.Heading)
					Text(
						if (taste.artists.isEmpty()) "No artists in common yet."
						else "You both play " + taste.artists.joinToString(", "),
						style = SvirkaText.Body,
						color = mutedColor
					)
				}
			}
		},
		confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
	)
}

/** Whether friends see what you play: [hidden] null shares it; otherwise hidden for a while or until turned off. */
@Composable
private fun PrivateListening(hidden: Hidden?, onSet: (mode: String) -> Unit) {
	var menu by remember { mutableStateOf(false) }
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Icon(if (hidden == null) SvirkaIcons.Users else SvirkaIcons.EyeOff, null, Modifier.size(18.dp), tint = mutedColor)
		Column(Modifier.weight(1f)) {
			Text(if (hidden == null) "Sharing what you play" else "Listening privately", style = SvirkaText.RowTitle)
			Text(
				when {
					hidden == null -> "Friends see what you play"
					hidden.until == null -> "Hidden from friends until you turn it off"
					else -> "Hidden from friends until ${clock(hidden.until)}"
				},
				style = SvirkaText.Small,
				color = mutedColor
			)
		}
		Box {
			TextButton(onClick = { menu = true }) { Text("Change") }
			DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
				listOf("hour" to "Hide for 1 hour", "on" to "Hide until I turn it off", "off" to "Share again")
					.filter { (mode, _) -> mode != "off" || hidden != null }
					.forEach { (mode, label) ->
						DropdownMenuItem(
							text = { Text(label) },
							onClick = {
								menu = false
								onSet(mode)
							}
						)
					}
			}
		}
	}
}

/** A song a friend sent, or their reaction to one you played. Tap: play it. */
@Composable
private fun InboxRow(item: InboxItem, onPlay: () -> Unit, onDismiss: () -> Unit) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onPlay)
			.padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp)
	) {
		CoverArt(
			coverArtId = item.song.coverArt,
			thumbnail = true,
			shape = SvirkaShapes.Sm,
			modifier = Modifier.size(48.dp)
		)
		Column(Modifier.weight(1f)) {
			Text(
				if (item.emoji == null) "${item.from} sent you" else "${item.from} reacted ${item.emoji} to",
				style = SvirkaText.Small,
				color = mutedColor
			)
			Text(
				"${item.song.title} — ${item.song.artist}",
				style = SvirkaText.RowTitle,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(ago(item.at), style = SvirkaText.Small, color = mutedColor)
		}
		IconButton(onClick = onDismiss) {
			Icon(SvirkaIcons.CircleX, "Dismiss", Modifier.size(16.dp), tint = mutedColor)
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
	onShare: (String) -> Unit,
	onGuestsControl: (Boolean) -> Unit,
	onRemove: (user: String) -> Unit
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
				if (jam.members.isEmpty()) Text(
					"Nobody has joined yet. Invite friends below or share the link.",
					style = SvirkaText.Body,
					color = mutedColor
				)
				jam.members.forEach { member ->
					Row(verticalAlignment = Alignment.CenterVertically) {
						Text(member, style = SvirkaText.RowTitle, modifier = Modifier.weight(1f))
						TextButton(onClick = { onRemove(member) }) { Text("Remove") }
					}
				}
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text("Guests can control playback", style = SvirkaText.Body, modifier = Modifier.weight(1f))
					Switch(checked = jam.guestsControl, onCheckedChange = onGuestsControl)
				}
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
						"Playing along on another device".takeIf { jam.listener != thisDevice },
						"Only ${jam.host} controls playback; you can still add songs".takeIf { !jam.guestsControl }
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
	onUnfollow: () -> Unit,
	onPlay: () -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	onReact: (emoji: String) -> Unit,
	onCompare: () -> Unit
) {
	val now = friend.now
	var menu by remember { mutableStateOf(false) }
	Box {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.combinedClickable(onClick = { menu = true }, onLongClick = { menu = true })
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
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
					Text(
						friend.user,
						style = SvirkaText.RowTitle,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false)
					)
					if (friend.followsYou) Text(
						"Follows you",
						style = SvirkaText.Small,
						color = mutedColor,
						modifier = Modifier
							.border(1.dp, MaterialTheme.colorScheme.outlineVariant, SvirkaShapes.Sm)
							.padding(horizontal = 6.dp)
					)
				}
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
						friend.hidden -> "Listening privately"
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
			if (now != null) {
				listOf("Play" to onPlay, "Play next" to onPlayNext, "Add to queue" to onAddToQueue).forEach { (label, action) ->
					DropdownMenuItem(
						text = { Text(label) },
						onClick = {
							menu = false
							action()
						}
					)
				}
				Text(
					"React to ${now.song.title}",
					style = SvirkaText.Small,
					color = mutedColor,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp).widthIn(max = 240.dp)
				)
				Row(Modifier.padding(horizontal = 4.dp)) {
					REACTIONS.forEach { emoji ->
						Text(
							emoji,
							fontSize = 20.sp,
							modifier = Modifier
								.clip(SvirkaShapes.Md)
								.clickable {
									menu = false
									onReact(emoji)
								}
								.padding(8.dp)
						)
					}
				}
				HorizontalDivider()
			}
			DropdownMenuItem(
				text = { Text("Compare tastes") },
				onClick = {
					menu = false
					onCompare()
				}
			)
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

/** HH:MM, local time. */
private fun clock(ms: Long): String {
	val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
	return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}
