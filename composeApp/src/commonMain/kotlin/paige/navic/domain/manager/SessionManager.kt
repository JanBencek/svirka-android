package paige.navic.domain.manager

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import dev.zt64.subsonic.api.model.SubsonicErrorCode
import dev.zt64.subsonic.api.model.SubsonicException
import dev.zt64.subsonic.client.SubsonicAuth
import dev.zt64.subsonic.client.SubsonicClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SessionManager(
	private val settings: Settings,
	private val preferenceManager: PreferenceManager
) {
	val isLoggedIn: StateFlow<Boolean>
		field = MutableStateFlow(false)

	var api: SubsonicClient = createClient(
		instanceUrl = settings.getString("instanceUrl", ""),
		username = settings.getString("username", ""),
		password = settings.getString("password", ""),
	)
		private set

	/** Bumped when [api] is rebuilt with new credentials: queued stream URLs carry the old token. */
	val apiChanges: StateFlow<Int>
		field = MutableStateFlow(0)

	init {
		isLoggedIn.value = settings.getStringOrNull("username") != null
	}

	private fun createClient(
		instanceUrl: String,
		username: String,
		password: String,
	) = SubsonicClient.Companion(
		baseUrl = instanceUrl,
		auth = SubsonicAuth.Token(
			username = username,
			password = password,
		),
		client = "Svirka",
		clientConfig = {
			install(UserAgent) {
				agent = "Svirka"
			}

			val customHeaders = preferenceManager.customHeadersMap()
			if (customHeaders.isNotEmpty()) {
				defaultRequest {
					customHeaders.forEach { (key, value) -> header(key, value) }
				}
			}
		}
	)

	suspend fun login(
		instanceUrl: String,
		username: String,
		password: String
	) {
		val client = createClient(instanceUrl, username, password)

		try {
			client.ping()
		} catch (e: SubsonicException) {
			// The server answered, so this isn't a connection problem
			if (e.code == SubsonicErrorCode.WRONG_USERNAME_OR_PASSWORD) {
				throw Exception("Wrong username or password.", e)
			}
			throw Exception("The server refused the login: ${e.message}", e)
		} catch (e: Exception) {
			throw Exception(
				"Failed to connect to the instance. Please check the address and try again.",
				e
			)
		}

		settings["instanceUrl"] = instanceUrl
		settings["username"] = username
		settings["password"] = password

		api = client
		isLoggedIn.value = true
	}

	fun logout() {
		settings["username"] = null
		settings["password"] = null
		isLoggedIn.value = false
	}

	fun refreshClient() {
		api = createClient(
			instanceUrl = settings.getString("instanceUrl", ""),
			username = settings.getString("username", ""),
			password = settings.getString("password", ""),
		)
		apiChanges.value++
	}

	fun getCoverArtUrl(coverArtId: String, size: Int? = null) = api.getCoverArtUrl(
		coverArtId,
		auth = true,
		size = "${size ?: preferenceManager.coverArtQuality.value}"
	)
}
