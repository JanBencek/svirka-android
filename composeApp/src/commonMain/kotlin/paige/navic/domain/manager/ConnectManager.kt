package paige.navic.domain.manager

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import paige.navic.domain.models.settings.ThemeMode
import paige.navic.domain.repositories.CollectionRepository
import paige.navic.shared.MediaPlayerViewModel
import kotlin.math.abs
import kotlin.random.Random
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

@Serializable
private data class HubTheme(val palette: String = "ocean", val mode: String = "")

@Serializable
private data class ConnectCommand(val cmd: String, val value: Double? = null, val state: ConnectState? = null)

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
	private val deviceName: String
) {
	val deviceId: String = settings.getStringOrNull("connectDeviceId")
		?: Uuid.random().toString().also { settings["connectDeviceId"] = it }

	/** All of the user's devices (this one included) while connected; empty otherwise. */
	val devices: StateFlow<List<ConnectDevice>>
		field = MutableStateFlow(emptyList())

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
			val salt = Random.nextBytes(6).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
			base = url
			auth = "u=${username.encodeURLParameter()}&t=${(password + salt).encodeUtf8().md5().hex()}&s=$salt"
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
			delay(backoff.seconds)
			backoff = (backoff * 2).coerceAtMost(60)
		}
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
		player.playFrom(
			songs,
			index.coerceAtLeast(0),
			if (index >= 0) (state.position * 1000).toLong() else 0L
		)
	}

	private fun currentState(): ConnectState {
		val s = player.uiState.value
		val song = s.currentSong
		return ConnectState(
			queue = s.queue.map {
				ConnectSong(
					id = it.id,
					title = it.title,
					artist = it.artistName,
					album = it.albumTitle.orEmpty(),
					coverArt = it.coverArtId,
					duration = it.duration.inWholeMilliseconds / 1000.0
				)
			},
			index = s.currentIndex,
			playing = song != null && !s.isPaused,
			position = (song?.duration?.inWholeMilliseconds ?: 0) / 1000.0 * s.progress,
			volume = player.volume?.toDouble()
		)
	}

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
