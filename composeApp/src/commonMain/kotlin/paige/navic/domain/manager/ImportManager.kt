package paige.navic.domain.manager

import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import paige.navic.domain.repositories.DbRepository
import kotlin.random.Random

@Serializable
data class ImportJob(
	val id: String,
	val url: String,
	val status: String, // queued | running | done | error
	@SerialName("items_added") val itemsAdded: Int = 0,
	val summary: String = "",
	val error: String = "",
	@SerialName("created_at") val createdAt: Long = 0,
	// library ids of the job's songs once done, also when they were already in the library
	@SerialName("song_ids") val songIds: List<String> = emptyList()
) {
	val isActive get() = status == "queued" || status == "running"
}

@Serializable
private data class ImportJobsResponse(val jobs: List<ImportJob>)

/** One Explore hit from the importer's `/search` — import it by [url]. */
@Serializable
data class ExploreResult(
	val source: String, // ytmusic | youtube | audiomack | spotify | tiktok | soundcloud | link
	val url: String,
	val title: String? = null,
	val artist: String? = null,
	val album: String? = null,
	val duration: Int? = null, // seconds
	val thumbnail: String? = null
)

@Serializable
private data class SearchResponse(val results: List<ExploreResult>)

@Serializable
private data class ImportRequest(val url: String, val play: Boolean = false)

@Serializable
private data class ImportQueued(@SerialName("job_id") val jobId: String)

@Serializable
private data class ErrorResponse(val error: String)

/**
 * Link import ("paste a YouTube/Spotify/SoundCloud link → it lands in the library").
 * Talks to the MusicBox importer mounted at `<server>/musicbox/` (homelab-only, not part of
 * Navidrome), authenticated with the same Subsonic token auth the app already uses.
 */
class ImportManager(private val settings: Settings, private val dbRepository: DbRepository) {
	/** Text shared into the app from another app (Android share sheet), consumed by App(). */
	val pendingShare = MutableStateFlow<String?>(null)

	/** Fires once imported or uploaded songs are in the local library, so song lists reload. */
	val libraryChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

	// App-wide, not the Explore screen's: the import still lands if you switch tabs meanwhile.
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private var watcher: Job? = null

	private val client = HttpClient {
		install(ContentNegotiation) {
			json(Json { ignoreUnknownKeys = true })
		}
		expectSuccess = false
	}

	private fun HttpRequestBuilder.auth() {
		val password = settings.getString("password", "")
		val salt = Random.nextBytes(6).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
		parameter("u", settings.getString("username", ""))
		parameter("s", salt)
		parameter("t", (password + salt).encodeUtf8().md5().hex())
	}

	private fun endpoint(path: String) =
		settings.getString("instanceUrl", "").trimEnd('/') + "/musicbox/" + path

	private fun HttpResponse.check(): HttpResponse {
		if (status.value == 401) throw IllegalStateException("Not authorised for link import")
		if (status.value == 404 || contentType()?.match(ContentType.Application.Json) != true)
			throw IllegalStateException("This server doesn't support link import")
		if (!status.isSuccess()) throw IllegalStateException("Import failed (HTTP ${status.value})")
		return this
	}

	suspend fun jobs(): List<ImportJob> =
		client.get(endpoint("jobs")) { auth() }.check().body<ImportJobsResponse>().jobs

	/** Search YouTube Music and YouTube together (deduplicated server-side); a link as [query] is previewed. */
	suspend fun search(query: String): List<ExploreResult> =
		client.get(endpoint("search")) {
			auth()
			parameter("q", query)
		}.check().body<SearchResponse>().results

	/** Save an audio file straight into the library; throws with the server's reason (duplicate, not audio, …). */
	suspend fun upload(name: String, bytes: ByteArray) {
		val res = client.post(endpoint("upload")) {
			auth()
			parameter("name", name)
			contentType(ContentType.Application.OctetStream)
			setBody(bytes)
		}
		if (res.status.value in listOf(400, 409, 411, 413))
			throw IllegalStateException(runCatching { res.body<ErrorResponse>().error }.getOrNull() ?: "Upload failed")
		res.check()
		syncNewSongs() // the server replies once Navidrome has scanned the file
	}

	suspend fun submit(url: String) {
		client.post(endpoint("import")) {
			auth()
			contentType(ContentType.Application.Json)
			setBody(ImportRequest(url))
		}.check()
		watch()
	}

	/**
	 * Imports [url] ahead of the queue (someone is waiting to hear it) and returns its songs' ids once
	 * they're in the local library — a few seconds for one song.
	 */
	suspend fun importForPlay(url: String): List<String> {
		val id = client.post(endpoint("import")) {
			auth()
			contentType(ContentType.Application.Json)
			setBody(ImportRequest(url, play = true))
		}.check().body<ImportQueued>().jobId
		repeat(300) { // ponytail: gives up after ~5 min; a long playlist keeps importing, it just won't autoplay
			delay(1_000)
			val job = runCatching { client.get(endpoint("jobs/$id")) { auth() }.check().body<ImportJob>() }.getOrNull()
				?: return@repeat // network blip: ask again
			if (job.status == "error") throw IllegalStateException(job.error.ifBlank { "Import failed" })
			if (job.status == "done") {
				if (job.itemsAdded > 0) syncNewSongs()
				return job.songIds
			}
		}
		throw IllegalStateException("Import is taking too long")
	}

	/** Polls until the queued imports finish, pulling each finished one into the local library. */
	private fun watch() {
		if (watcher?.isActive == true) return
		watcher = scope.launch {
			val synced = runCatching { jobs() }.getOrDefault(emptyList()).filter { !it.isActive }.map { it.id }.toMutableSet()
			while (true) {
				delay(1_000)
				val all = runCatching { jobs() }.getOrNull() ?: continue
				val finished = all.filter { !it.isActive && it.id !in synced }
				if (finished.any { it.status == "done" && it.itemsAdded > 0 }) syncNewSongs()
				synced += finished.map { it.id }
				if (all.none { it.isActive }) break
			}
		}
	}

	/** A "done" job is already scanned by Navidrome, so only the newest albums need fetching. */
	private suspend fun syncNewSongs() {
		dbRepository.syncLibrarySongs(newest = 10)
		libraryChanged.tryEmit(Unit)
	}

	companion object {
		private val URL_REGEX = Regex("""https?://[^\s<>"']+""")
		fun extractUrls(text: String) = URL_REGEX.findAll(text).map { it.value }.distinct().toList()
	}
}
