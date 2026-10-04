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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import kotlin.random.Random

@Serializable
data class ImportJob(
	val id: String,
	val url: String,
	val status: String, // queued | running | done | error
	@SerialName("items_added") val itemsAdded: Int = 0,
	val summary: String = "",
	val error: String = "",
	@SerialName("created_at") val createdAt: Long = 0
) {
	val isActive get() = status == "queued" || status == "running"
}

@Serializable
private data class ImportJobsResponse(val jobs: List<ImportJob>)

@Serializable
private data class ImportRequest(val url: String)

/**
 * Link import ("paste a YouTube/Spotify/SoundCloud link → it lands in the library").
 * Talks to the MusicBox importer mounted at `<server>/musicbox/` (homelab-only, not part of
 * Navidrome), authenticated with the same Subsonic token auth the app already uses.
 */
class ImportManager(private val settings: Settings) {
	/** Text shared into the app from another app (Android share sheet), consumed by App(). */
	val pendingShare = MutableStateFlow<String?>(null)

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

	suspend fun submit(url: String) {
		client.post(endpoint("import")) {
			auth()
			contentType(ContentType.Application.Json)
			setBody(ImportRequest(url))
		}.check()
	}

	companion object {
		private val URL_REGEX = Regex("""https?://[^\s<>"']+""")
		fun extractUrls(text: String) = URL_REGEX.findAll(text).map { it.value }.distinct().toList()
	}
}
