package paige.navic.ui.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.capsule.ContinuousCapsule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_open_release_page
import navic.composeapp.generated.resources.action_skip_update
import navic.composeapp.generated.resources.action_update_to
import navic.composeapp.generated.resources.info_downloading_update
import navic.composeapp.generated.resources.info_update
import navic.composeapp.generated.resources.info_update_failed
import navic.composeapp.generated.resources.title_update
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.UpdateManager
import paige.navic.ui.components.common.Markdown
import paige.navic.ui.theme.defaultFont
import paige.navic.util.core.Logger

@Serializable
data class GitHubRelease(
	@SerialName("tag_name") val tag: String,
	@SerialName("html_url") val url: String,
	@SerialName("body") val body: String
)

/**
 * Svirka self-update, shared by the update sheet at start-up and Settings → About: the newest release when it's newer
 * than this build, and the in-app download of it.
 */
class UpdateChecker(private val updateManager: UpdateManager) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	private val updateClient = HttpClient {
		install(ContentNegotiation) {
			json(Json { ignoreUnknownKeys = true })
		}
	}

	/** The latest release when it's newer than this build; null otherwise (or not checked yet). */
	val release: StateFlow<GitHubRelease?>
		field = MutableStateFlow(null)

	/** 0..1 while the update downloads; null otherwise. */
	val progress: StateFlow<Float?>
		field = MutableStateFlow(null)

	/** Why the last in-app update failed (then the release page is the way); null when it didn't. */
	val error: StateFlow<String?>
		field = MutableStateFlow(null)

	private var checking = false

	/** Asks GitHub once per app run (the sheet and About both call it). */
	fun check(currentVersion: String) {
		if (checking) return
		checking = true
		scope.launch {
			release.value = try {
				val release: GitHubRelease =
					updateClient.get("https://api.github.com/repos/JanBencek/svirka-android/releases/latest")
						.body()
				val remoteVersion = release.tag
					.filter { it.isDigit() }
					.toIntOrNull()
				val localVersion = currentVersion
					.filter { it.isDigit() }
					.toIntOrNull()
				if (remoteVersion != null && localVersion != null &&
					(remoteVersion > localVersion || "$remoteVersion".length != "$localVersion".length)
				) release else null
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Logger.e("UpdateChecker", "couldn't check for updates", e)
				checking = false // try again next time
				null
			}
		}
	}

	/** Download the new APK and hand it to the installer; [openReleasePage] where the platform can't (iOS). */
	fun update(openReleasePage: () -> Unit) {
		if (progress.value != null) return
		scope.launch {
			progress.value = 0f
			error.value = null
			try {
				if (!updateManager.install(APK_URL) { progress.value = it }) openReleasePage()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Logger.e("UpdateChecker", "couldn't download the update", e)
				error.value = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "unknown error"
			}
			progress.value = null
		}
	}

	private companion object {
		const val APK_URL = "https://github.com/JanBencek/svirka-android/releases/latest/download/Svirka.apk"
	}
}

/** The Update button and what goes with it: progress, why it failed, then the release page instead. */
@Composable
fun UpdateButton(release: GitHubRelease, modifier: Modifier = Modifier) {
	val checker = koinInject<UpdateChecker>()
	val uriHandler = LocalUriHandler.current
	val progress by checker.progress.collectAsStateWithLifecycle()
	val error by checker.error.collectAsStateWithLifecycle()
	Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
		Button(
			onClick = {
				if (error != null) uriHandler.openUri(release.url)
				else checker.update { uriHandler.openUri(release.url) }
			},
			enabled = progress == null,
			modifier = Modifier.fillMaxWidth(),
			shape = ContinuousCapsule
		) {
			Text(
				text = progress?.let {
					stringResource(Res.string.info_downloading_update, "${(it * 100).toInt()}%")
				} ?: if (error != null) stringResource(Res.string.action_open_release_page)
				else stringResource(Res.string.action_update_to, release.tag),
				fontFamily = defaultFont(100)
			)
		}
		error?.let {
			Text(
				stringResource(Res.string.info_update_failed, it),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.error,
				modifier = Modifier.padding(top = 4.dp)
			)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogSheet() {
	val preferenceManager = koinInject<PreferenceManager>()
	val platformContext = LocalPlatformContext.current
	val checker = koinInject<UpdateChecker>()
	LaunchedEffect(Unit) { checker.check(platformContext.appVersion) }
	val release by checker.release.collectAsStateWithLifecycle()
	var dismissed by rememberSaveable { mutableStateOf(false) }

	release?.takeIf { !dismissed && it.tag != preferenceManager.skippedUpdate }?.let { release ->
		ModalBottomSheet(
			onDismissRequest = { dismissed = true },
			sheetState = rememberBottomSheetState(
				initialValue = SheetValue.Hidden,
				enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
			)
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp)
			) {
				Column(
					modifier = Modifier.fillMaxWidth(),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					Text(
						text = stringResource(Res.string.title_update),
						style = MaterialTheme.typography.titleLarge,
						fontFamily = defaultFont(round = 100f)
					)
					Text(
						stringResource(Res.string.info_update, release.tag),
						style = MaterialTheme.typography.bodyMedium
					)
				}

				Spacer(Modifier.height(8.dp))

				Markdown(
					text = release.body,
					modifier = Modifier
						.heightIn(max = 400.dp)
						.fillMaxWidth()
						.clip(MaterialTheme.shapes.large)
						.background(MaterialTheme.colorScheme.surfaceContainerHigh)
						.verticalScroll(rememberScrollState())
						.padding(10.dp)
				)

				Spacer(Modifier.height(8.dp))
				Spacer(Modifier.weight(1f))

				UpdateButton(release, Modifier.fillMaxWidth())

				// skips this build only: the next one asks again, and Settings → About still offers this one
				OutlinedButton(
					onClick = {
						preferenceManager.skippedUpdate = release.tag
						dismissed = true
					},
					modifier = Modifier.fillMaxWidth(),
					shape = ContinuousCapsule
				) {
					Text(
						text = stringResource(Res.string.action_skip_update),
						fontFamily = defaultFont(100)
					)
				}
			}
		}
	}
}
