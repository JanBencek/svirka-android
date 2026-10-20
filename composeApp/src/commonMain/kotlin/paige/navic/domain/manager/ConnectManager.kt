package paige.navic.domain.manager

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.jam_added
import navic.composeapp.generated.resources.jam_enqueued
import navic.composeapp.generated.resources.jam_error
import navic.composeapp.generated.resources.jam_invite
import navic.composeapp.generated.resources.jam_invite_to
import navic.composeapp.generated.resources.jam_invited
import navic.composeapp.generated.resources.jam_join
import okio.ByteString.Companion.encodeUtf8
import org.jetbrains.compose.resources.getString
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.settings.ThemeMode
import paige.navic.domain.repositories.CollectionRepository
import paige.navic.shared.MediaPlayerViewModel
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class ConnectSong(
	val id: String,
	val title: String,
	val artist: String,
	val album: String,
	val coverArt: String? = null,
	val duration: Double
)

/** What a device plays: [queue] in play order, [index] into it, [position] seconds at server time [at] (ms). */
@Serializable
data class ConnectState(
	val queue: List<ConnectSong>,
	val index: Int,
	val playing: Boolean,
	val position: Double,
	val at: Long = 0,
	val volume: Double? // no default: Svirka wants the key even when null
) {
	val song get() = queue.getOrNull(index)
}

@Serializable
data class ConnectDevice(
	val id: String,
	val name: String,
	val kind: String,
	val state: ConnectState? = null
)

/** A Jam as user [you] sees it: [listener] is the device of theirs that plays along (null for the host). */
@Serializable
data class JamView(
	val id: String,
	val host: String,
	val you: String? = null,
	val members: List<String> = emptyList(),
	val listener: String? = null,
	val state: ConnectState? = null
)

@Serializable
data class FriendNow(val song: ConnectSong, val playing: Boolean, val at: Long)

/** Someone this user follows: what they play now (or last played) and the Jam they are in. */
@Serializable
data class Friend(val user: String, val online: Boolean = false, val now: FriendNow? = null, val jam: String? = null)

/** [others]: users this user could follow. */
@Serializable
data class Friends(val friends: List<Friend> = emptyList(), val others: List<String> = emptyList())

/** User [from] invites this user to Jam [id], hosted by [host]. */
@Serializable
private data class JamInvite(val id: String, val from: String, val host: String)

@Serializable
private data class JamResponse(val jam: JamView? = null, val error: String? = null)

@Serializable
private data class ApiError(val error: String? = null)

@Serializable
private data class Me(val admin: Boolean = false)

@Serializable
private data class FollowBody(val user: String, val follow: Boolean)

@Serializable
private data class HubTheme(val palette: String = "ocean", val mode: String = "")

@Serializable
private data class ConnectCommand(
	val cmd: String,
	val value: Double? = null,
	val state: ConnectState? = null,
	val songs: List<ConnectSong>? = null, // enqueue: a Jam guest (`from`) added these
	val from: String? = null
)

@Serializable
private data class StateBody(val device: String, val state: ConnectState)

@Serializable
private data class CommandBody(val target: String, val cmd: String, val value: Double? = null)

/**
 * Svirka Connect, like Spotify Connect: this phone and the user's Svirka web players see each other and hand
 * playback around. Svirka (the web app at the same server URL) keeps the device list: we hold an SSE stream to
 * `<server>/api/connect` open, report what plays here, and follow the commands pushed down that stream.
 * Authenticated with the Subsonic token auth the app already uses. Runs while the app process lives.
 */
@OptIn(ExperimentalUuidApi::class)
class ConnectManager(
	private val settings: Settings,
	private val player: MediaPlayerViewModel,
	private val collections: CollectionRepository,
	private val preferences: PreferenceManager,
	private val snackBar: SnackBarManager,
	private val deviceName: String,
	/** Shows a Jam invite ([text], the Jam's [link]) as a system notification; false: not now, the snack bar has it. */
	private val notifyInvite: (text: String, link: String) -> Boolean = { _, _ -> false }
) {
	val deviceId: String = settings.getStringOrNull("connectDeviceId")
		?: Uuid.random().toString().also { settings["connectDeviceId"] = it }

	/** All of the user's devices (this one included) while connected; empty otherwise. */
	val devices: StateFlow<List<ConnectDevice>>
		field = MutableStateFlow(emptyList())

	/** The Jam this user is in (hosting or as a guest), kept current by the `jam` event; null when in none. */
	val jam: StateFlow<JamView?>
		field = MutableStateFlow<JamView?>(null)

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	private val json = Json { ignoreUnknownKeys = true }
	private val client = HttpClient {
		install(SSE)
		install(HttpTimeout) { socketTimeoutMillis = 60_000 } // the server pings every 25 s
		install(ContentNegotiation) { json(json) }
		expectSuccess = false
	}

	private var base = ""
	private var auth = ""
	private var connected = false

	fun start() {
		scope.launch { connectLoop() }
		scope.launch { reportLoop() }
		scope.launch { jam.collectLatest { followJam(it) } }
	}

	/* ---- Friends ---- */

	/** [query]: search for people to follow; without one (2+ letters) the server lists nobody in [Friends.others]. */
	suspend fun friends(query: String = ""): Friends {
		awaitAuth()
		return client.get("$base/api/connect/friends?$auth&q=${query.encodeURLParameter()}").body()
	}

	suspend fun follow(user: String, on: Boolean, query: String = ""): Friends {
		awaitAuth()
		return client.post("$base/api/connect/friends?$auth&q=${query.encodeURLParameter()}") {
			contentType(ContentType.Application.Json)
			setBody(FollowBody(user, on))
		}.body()
	}

	/** Svirka feedback (Settings): [message] goes to the server's admin. True when the server took it. */
	suspend fun sendFeedback(message: String, version: String): Boolean {
		awaitAuth()
		return client.post("$base/api/feedback?$auth") {
			contentType(ContentType.Application.Json)
			setBody(buildJsonObject {
				put("message", message.take(4000))
				put("app", "android")
				put("version", version)
			})
		}.status.isSuccess()
	}

	/**
	 * Svirka password change (Settings). Null when changed: the stored login switches to [password] right away
	 * (callers rebuild the Subsonic client); otherwise the server's reason, or "" when it gave none.
	 */
	suspend fun changePassword(current: String, password: String): String? {
		awaitAuth()
		val res = client.post("$base/api/auth/password?$auth") {
			contentType(ContentType.Application.Json)
			setBody(buildJsonObject {
				put("current", current)
				put("password", password)
			})
		}
		if (!res.status.isSuccess()) return runCatching { res.body<ApiError>().error }.getOrNull().orEmpty()
		settings["password"] = password
		auth = tokenAuth(settings.getString("username", ""), password) // the open stream stays; reconnects use it too
		return null
	}

	/** Navidrome admin (the server owner): may delete songs from the library. False when unsure. */
	suspend fun isAdmin(): Boolean {
		awaitAuth()
		val res = client.get("$base/api/auth/me?$auth")
		return res.status.isSuccess() && runCatching { res.body<Me>().admin }.getOrDefault(false)
	}

	/** Deletes the song's file from the library (admin only). Null when deleted, otherwise the server's reason or "". */
	suspend fun deleteSong(songId: String): String? {
		awaitAuth()
		val res = client.delete("$base/api/library/songs/${songId.encodeURLPathPart()}?$auth")
		if (res.status.isSuccess()) return null
		return runCatching { res.body<ApiError>().error }.getOrNull().orEmpty()
	}

	/* ---- Jam: listen along with friends, like Spotify Jam ---- */

	fun startJam() = jamAction("start")

	/** Join Jam [id]; this phone plays along. */
	fun joinJam(id: String) = jamAction("join") {
		put("id", id)
		put("device", deviceId)
	}

	/** Anyone in a Jam can invite [user]: every device of theirs that's open offers to join. */
	fun inviteToJam(user: String) = jamAction("invite", onDone = { snackBar.notify(Res.string.jam_invited, user) }) {
		put("user", user)
	}

	/** A guest leaves; the host ends the Jam for everyone. */
	fun leaveJam() = jamAction("leave")

	/** While this phone plays along: play/pause/next/prev/seek (seconds) go to the host. */
	fun jamControl(cmd: String, value: Double? = null) = jamAction("control") {
		put("cmd", cmd)
		value?.let { put("value", it) }
	}

	/** While this phone plays along: what the user plays or queues is added to the host's queue. */
	fun jamAdd(songs: List<DomainSong>) {
		if (songs.isEmpty()) return
		jamAction("add", onDone = { snackBar.notify(Res.string.jam_added) }) {
			put("songs", json.encodeToJsonElement(songs.take(200).map { it.toConnect() }))
		}
	}

	private fun jamAction(action: String, onDone: () -> Unit = {}, fields: JsonObjectBuilder.() -> Unit = {}) {
		scope.launch {
			awaitAuth()
			val res = try {
				client.post("$base/api/connect/jam?$auth") {
					contentType(ContentType.Application.Json)
					setBody(buildJsonObject {
						put("action", action)
						fields()
					})
				}.body<JamResponse>()
			} catch (e: CancellationException) {
				throw e
			} catch (_: Exception) {
				JamResponse(error = "Couldn't reach Svirka")
			}
			val error = res.error
			if (error != null) {
				snackBar.notify(Res.string.jam_error, error)
			} else {
				jam.value = res.jam
				onDone()
			}
		}
	}

	private suspend fun awaitAuth() {
		while (auth.isEmpty()) delay(1.seconds)
	}

	/**
	 * When this phone is the Jam's listener, play what the host plays: same song and queue, within 3 s of the
	 * host's position, playing or paused alike. The user's own controls go to the host meanwhile (player.jam).
	 */
	private suspend fun followJam(view: JamView?) {
		val listening = view != null && view.listener == deviceId
		player.jam = if (listening) this else null
		val state = view?.state ?: return
		if (!listening) return
		val target = state.song ?: return
		// ponytail: trusts this phone's clock against the server's; fine with NTP, off by the skew otherwise
		val elapsed = (Clock.System.now().toEpochMilliseconds() - state.at) / 1000.0
		val live = state.position + if (state.playing) elapsed.coerceAtLeast(0.0) else 0.0
		val now = player.uiState.value
		val localIds = now.queue.map { it.id }
		val ids = state.queue.map { it.id }
		if (now.currentSong?.id != target.id || (localIds == ids && now.currentIndex != state.index)) {
			load(state.copy(position = live))
			if (!state.playing) local { player.pause() }
			return
		}
		if (localIds != ids) {
			val songs = state.queue.mapNotNull { collections.getSongById(it.id) }
			val index = songs.indexOfFirst { it.id == target.id }
			if (index >= 0 && songs.map { it.id } != localIds) local { player.replaceQueue(songs, index) }
		}
		val position = (now.currentSong?.duration?.inWholeMilliseconds ?: 0L) / 1000.0 * now.progress
		if (abs(position - live) > 3) local { player.seekToSeconds(live) }
		if (state.playing == now.isPaused) local { if (state.playing) player.resume() else player.pause() }
	}

	/** Player calls made to follow the Jam, not by the user: they act here instead of going back to the host. */
	private inline fun local(block: () -> Unit) {
		val saved = player.jam
		player.jam = null
		try {
			block()
		} finally {
			player.jam = saved
		}
	}

	fun transferTo(target: String) = command(target, "transfer")

	fun command(target: String, cmd: String, value: Double? = null) {
		if (!connected) return
		scope.launch {
			runCatching {
				client.post("$base/api/connect/command?$auth") {
					contentType(ContentType.Application.Json)
					setBody(CommandBody(target, cmd, value))
				}
			}
		}
	}

	private suspend fun connectLoop() {
		var backoff = 1
		while (true) {
			val url = settings.getString("instanceUrl", "").trimEnd('/')
			val username = settings.getString("username", "")
			val password = settings.getString("password", "")
			if (url.isEmpty() || username.isEmpty()) {
				delay(10.seconds)
				continue
			}
			base = url
			auth = tokenAuth(username, password)
			try {
				client.sse(
					"$base/api/connect?$auth&kind=phone&device=$deviceId&name=${deviceName.encodeURLParameter()}"
				) {
					connected = true
					backoff = 1
					lastReport = null // report right away so the others see what plays here
					incoming.collect { event ->
						val data = event.data ?: return@collect
						when (event.event) {
							"devices" -> devices.value = json.decodeFromString(data)
							"command" -> handle(json.decodeFromString(data))
							"theme" -> applyHubTheme(json.decodeFromString(data))
							"jam" -> jam.value = json.decodeFromString<JamView?>(data)
							"invite" -> invited(json.decodeFromString(data))
						}
					}
				}
			} catch (e: CancellationException) {
				throw e
			} catch (_: Exception) {
				// offline, server without Svirka Connect, or logged out: retry with backoff
			}
			connected = false
			devices.value = emptyList()
			jam.value = null // the server sends it again on reconnect
			delay(backoff.seconds)
			backoff = (backoff * 2).coerceAtMost(60)
		}
	}

	/** Subsonic token auth query: u, t = md5(password + salt), s. */
	private fun tokenAuth(username: String, password: String): String {
		val salt = Random.nextBytes(6).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
		return "u=${username.encodeURLParameter()}&t=${(password + salt).encodeUtf8().md5().hex()}&s=$salt"
	}

	private fun handle(c: ConnectCommand) {
		when (c.cmd) {
			"play" -> player.resume()
			"pause" -> player.pause()
			"next" -> player.next()
			"prev" -> player.previous()
			"seek" -> c.value?.let { player.seekToSeconds(it) }
			"volume" -> c.value?.let { player.setVolume(it.toFloat()) }
			"skipTo" -> c.value?.let { player.playAt(it.toInt()) }
			"load" -> c.state?.let { scope.launch { load(it) } }
			"enqueue" -> scope.launch { enqueue(c.songs.orEmpty(), c.from.orEmpty()) }
		}
	}

	/**
	 * The BencIT hub's colour theme and light/dark (picked in the hub or any BencIT app). The colours always follow;
	 * light/dark only when the hub's choice changes, so a mode set here in Settings holds until then.
	 */
	private fun applyHubTheme(theme: HubTheme) {
		preferences.hubPalette = theme.palette
		val applied = "${theme.palette}|${theme.mode}"
		if (preferences.hubThemeApplied == applied) return
		preferences.hubThemeApplied = applied
		preferences.themeMode = when (theme.mode) {
			"light" -> ThemeMode.Light
			"dark" -> ThemeMode.Dark
			else -> ThemeMode.System
		}
	}

	/** Playback handed over from another device: same queue, same song, same second. */
	private suspend fun load(state: ConnectState) {
		val target = state.song?.id
		// ponytail: songs the phone hasn't synced yet are skipped; fetch them from the API if that bites
		val songs = state.queue.mapNotNull { collections.getSongById(it.id) }
		if (songs.isEmpty()) return
		val index = songs.indexOfFirst { it.id == target }
		local {
			player.playFrom(
				songs,
				index.coerceAtLeast(0),
				if (index >= 0) (state.position * 1000).toLong() else 0L
			)
		}
	}

	private fun invited(i: JamInvite) {
		val (text, args) = if (i.from == i.host) Res.string.jam_invite to arrayOf(i.from)
		else Res.string.jam_invite_to to arrayOf(i.from, i.host)
		scope.launch {
			if (!notifyInvite(getString(text, *args), "$base/jam/${i.id}"))
				snackBar.notifyWithAction(text, Res.string.jam_join, { joinJam(i.id) }, *args)
		}
	}

	/** Hosting a Jam: a guest added [added] to our queue. */
	private suspend fun enqueue(added: List<ConnectSong>, from: String) {
		val songs = added.mapNotNull { collections.getSongById(it.id) }
		if (songs.isEmpty()) return
		player.addToQueue(songs, notify = false)
		val title = added.first().title + if (added.size > 1) " +${added.size - 1}" else ""
		snackBar.notify(Res.string.jam_enqueued, from, title)
	}

	private fun currentState(): ConnectState {
		val s = player.uiState.value
		val song = s.currentSong
		return ConnectState(
			queue = s.queue.map { it.toConnect() },
			index = s.currentIndex,
			playing = song != null && !s.isPaused,
			position = (song?.duration?.inWholeMilliseconds ?: 0) / 1000.0 * s.progress,
			volume = player.volume?.toDouble()
		)
	}

	private fun DomainSong.toConnect() = ConnectSong(
		id = id,
		title = title,
		artist = artistName,
		album = albumTitle.orEmpty(),
		coverArt = coverArtId,
		duration = duration.inWholeMilliseconds / 1000.0
	)

	private var lastReport: Pair<List<Any?>, ConnectState>? = null

	/** Report on every change, and when the position jumps (a seek) rather than just running on. */
	private suspend fun reportLoop() {
		var lastAt = TimeSource.Monotonic.markNow()
		while (true) {
			delay(1.seconds)
			if (!connected) continue
			val state = currentState()
			val key = listOf(state.queue.map { it.id }, state.index, state.playing, state.volume)
			val last = lastReport
			val expected = last?.second?.let {
				it.position + if (it.playing) lastAt.elapsedNow().inWholeMilliseconds / 1000.0 else 0.0
			}
			if (last != null && last.first == key && expected != null && abs(state.position - expected) < 3) continue
			val ok = runCatching {
				client.post("$base/api/connect?$auth") {
					contentType(ContentType.Application.Json)
					setBody(StateBody(deviceId, state))
				}.status.isSuccess()
			}.getOrDefault(false)
			if (ok) {
				lastReport = key to state
				lastAt = TimeSource.Monotonic.markNow()
			}
		}
	}
}
