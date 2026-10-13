package paige.navic.ui.screens.importLink

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_import
import navic.composeapp.generated.resources.info_import_failed
import navic.composeapp.generated.resources.info_import_queued
import navic.composeapp.generated.resources.info_import_running
import navic.composeapp.generated.resources.title_import
import navic.composeapp.generated.resources.title_import_tab
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.ExploreResult
import paige.navic.domain.manager.ImportJob
import paige.navic.domain.manager.ImportManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.ui.components.common.PickedFile
import paige.navic.ui.components.common.rememberAudioFilePicker
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.svirka.SvirkaEmptyState
import paige.navic.ui.svirka.SvirkaIcons
import paige.navic.ui.svirka.SvirkaSectionTitle
import paige.navic.ui.svirka.SvirkaShapes
import paige.navic.ui.svirka.SvirkaText
import paige.navic.ui.svirka.mutedColor
import paige.navic.util.core.isLandscape

/** One file being uploaded from this device: uploading | done | error. */
data class UploadRow(val name: String, val status: String, val error: String? = null)

class ImportViewModel(private val manager: ImportManager) : ViewModel() {
	val jobs: StateFlow<List<ImportJob>>
		field = MutableStateFlow<List<ImportJob>>(emptyList())

	/** Last failure (server unreachable, no importer on this server, …); null when fine. */
	val error: StateFlow<String?>
		field = MutableStateFlow<String?>(null)

	val submitting: StateFlow<Boolean>
		field = MutableStateFlow(false)

	/** Explore results; null until the first search. */
	val results: StateFlow<List<ExploreResult>?>
		field = MutableStateFlow<List<ExploreResult>?>(null)

	val searching: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val uploads: StateFlow<List<UploadRow>>
		field = MutableStateFlow<List<UploadRow>>(emptyList())

	private var searchJob: Job? = null
	private var lastQuery: String? = null

	init {
		// Poll fast while something is downloading, slowly otherwise; stops with the screen.
		viewModelScope.launch {
			while (true) {
				refresh()
				delay(if (jobs.value.any { it.isActive }) 1_000 else 30_000)
			}
		}
	}

	private suspend fun refresh() {
		try {
			jobs.value = manager.jobs()
			error.value = null
		} catch (e: Exception) {
			error.value = e.message ?: "Couldn't reach the importer"
		}
	}

	/** Search YouTube Music + YouTube, or preview a single link; a newer search cancels the last. */
	fun search(query: String) {
		if (query == lastQuery) return
		lastQuery = query
		searchJob?.cancel()
		searchJob = viewModelScope.launch {
			searching.value = true
			try {
				results.value = manager.search(query)
				error.value = null
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				lastQuery = null
				error.value = e.message ?: "Search failed"
			}
			searching.value = false
		}
	}

	/** Uploads [files] one at a time straight into the library. */
	fun upload(files: List<PickedFile>) {
		uploads.value = files.map { UploadRow(it.name, "uploading") }
		viewModelScope.launch {
			files.forEachIndexed { i, file ->
				val row = try {
					manager.upload(file.name, withContext(Dispatchers.IO) { file.read() })
					UploadRow(file.name, "done")
				} catch (e: CancellationException) {
					throw e
				} catch (e: Exception) {
					UploadRow(file.name, "error", e.message ?: "Upload failed")
				}
				uploads.value = uploads.value.toMutableList().also { it[i] = row }
			}
		}
	}

	/** Queues every link in [text]; returns true when all were accepted. */
	fun submit(text: String, onDone: (Boolean) -> Unit) {
		val urls = ImportManager.extractUrls(text)
		if (urls.isEmpty()) return onDone(false)
		viewModelScope.launch {
			submitting.value = true
			var ok = true
			for (url in urls) {
				try {
					manager.submit(url)
				} catch (e: Exception) {
					ok = false
					error.value = e.message
				}
			}
			refresh()
			submitting.value = false
			onDone(ok)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(initialText: String?, nested: Boolean = true) {
	val viewModel = koinViewModel<ImportViewModel>()
	val jobs by viewModel.jobs.collectAsStateWithLifecycle()
	val error by viewModel.error.collectAsStateWithLifecycle()
	val submitting by viewModel.submitting.collectAsStateWithLifecycle()
	val results by viewModel.results.collectAsStateWithLifecycle()
	val searching by viewModel.searching.collectAsStateWithLifecycle()
	val uploads by viewModel.uploads.collectAsStateWithLifecycle()
	var text by rememberSaveable { mutableStateOf(initialText.orEmpty().replace('\n', ' ').trim()) }
	val keyboard = LocalSoftwareKeyboardController.current
	val pickFiles = rememberAudioFilePicker { viewModel.upload(it) }

	// Several links import straight away; one link is previewed; anything else is a search.
	fun go() {
		val urls = ImportManager.extractUrls(text)
		when {
			urls.size > 1 -> viewModel.submit(text) { ok -> if (ok) text = "" }
			urls.size == 1 -> viewModel.search(urls.first())
			text.isNotBlank() -> viewModel.search(text.trim())
		}
		keyboard?.hide()
	}

	// Search as you type, once typing pauses; several pasted links still wait for the search key.
	LaunchedEffect(text) {
		val query = text.trim()
		if (query.isEmpty() || ImportManager.extractUrls(query).size > 1) return@LaunchedEffect
		delay(300)
		viewModel.search(ImportManager.extractUrls(query).singleOrNull() ?: query)
	}

	// Shared from another app (share sheet): show it right away.
	LaunchedEffect(Unit) { if (!initialText.isNullOrBlank()) go() }

	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	Scaffold(
		topBar = {
			if (!nested) {
				RootTopBar(
					title = { Text(stringResource(Res.string.title_import_tab)) },
					scrollBehavior = scrollBehavior
				)
			} else {
				NestedTopBar({ Text(stringResource(Res.string.title_import)) })
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
				Text(
					text = "Find songs on YouTube Music and YouTube — or paste a Spotify, TikTok, SoundCloud or Audiomack link — and import them into your library.",
					style = SvirkaText.Body,
					color = mutedColor
				)
			}
			item {
				Column(Modifier.padding(top = 16.dp)) {
					OutlinedTextField(
						value = text,
						onValueChange = { text = it },
						modifier = Modifier.fillMaxWidth(),
						placeholder = { Text("Search songs or paste a link…", style = SvirkaText.Body) },
						leadingIcon = { Icon(SvirkaIcons.Search, null, Modifier.size(18.dp)) },
						trailingIcon = if (searching || submitting) {
							{ CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
						} else null,
						singleLine = true,
						keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
						keyboardActions = KeyboardActions(onSearch = { go() }),
						textStyle = SvirkaText.Body,
						shape = SvirkaShapes.Lg
					)
					if (pickFiles != null) {
						OutlinedButton(
							onClick = pickFiles,
							enabled = uploads.none { it.status == "uploading" },
							modifier = Modifier.padding(top = 8.dp)
						) {
							Icon(SvirkaIcons.Upload, null, Modifier.size(18.dp))
							Spacer(Modifier.width(8.dp))
							Text("Upload files from this phone")
						}
					}
				}
			}
			itemsIndexed(uploads, key = { i, _ -> "upload:$i" }) { _, row -> UploadRowView(row) }
			error?.let { message ->
				item {
					Text(
						text = message,
						color = MaterialTheme.colorScheme.error,
						style = SvirkaText.Body
					)
				}
			}
			results?.let { found ->
				item {
					SvirkaSectionTitle(
						text = "Results",
						icon = SvirkaIcons.Search,
						modifier = Modifier.padding(top = 8.dp)
					)
				}
				if (found.isEmpty()) {
					item {
						SvirkaEmptyState(
							title = "No results",
							hint = "Try different words, or paste a link.",
							icon = SvirkaIcons.Search
						)
					}
				}
				items(found, key = { "result:" + it.url }) { result ->
					ExploreResultRow(
						result = result,
						// jobs are newest first, so this is the latest attempt at this link
						status = jobs.firstOrNull { it.url == result.url }?.status,
						onImport = { viewModel.submit(result.url) {} }
					)
				}
			}
			item {
				SvirkaSectionTitle(
					text = "Recent imports",
					icon = SvirkaIcons.History,
					modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
				)
			}
			items(jobs, key = { it.id }) { job -> ImportJobCard(job) }
			if (jobs.isEmpty() && error == null) {
				item {
					SvirkaEmptyState(
						title = "No imports yet",
						hint = "Search above, or paste a song or playlist link, to add it to your library.",
						icon = SvirkaIcons.Download
					)
				}
			}
		}
	}
}

private val SOURCE_LABELS = mapOf(
	"ytmusic" to "YouTube Music",
	"youtube" to "YouTube",
	"spotify" to "Spotify",
	"tiktok" to "TikTok",
	"soundcloud" to "SoundCloud",
	"audiomack" to "Audiomack",
	"link" to "Link"
)

@Composable
private fun ExploreResultRow(result: ExploreResult, status: String?, onImport: () -> Unit) {
	val busy = status == "queued" || status == "running"
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp)
	) {
		AsyncImage(
			model = result.thumbnail,
			contentDescription = null,
			contentScale = ContentScale.Crop,
			modifier = Modifier
				.size(48.dp)
				.clip(SvirkaShapes.Sm)
				.background(MaterialTheme.colorScheme.surfaceContainer)
		)
		Column(Modifier.weight(1f)) {
			Text(
				text = result.title ?: result.url,
				style = SvirkaText.RowTitle,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = listOfNotNull(
					SOURCE_LABELS[result.source] ?: result.source,
					result.artist,
					result.album,
					result.duration?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }
				).joinToString(" · "),
				style = SvirkaText.Small,
				color = mutedColor,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
		IconButton(onClick = onImport, enabled = !busy && status != "done") {
			when {
				busy -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
				status == "done" -> Icon(
					SvirkaIcons.CircleCheck, "Imported", Modifier.size(20.dp),
					tint = MaterialTheme.colorScheme.primary
				)
				status == "error" -> Icon(
					SvirkaIcons.CircleX, "Retry import", Modifier.size(20.dp),
					tint = MaterialTheme.colorScheme.error
				)
				else -> Icon(SvirkaIcons.Download, stringResource(Res.string.action_import), Modifier.size(20.dp))
			}
		}
	}
}

@Composable
private fun UploadRowView(row: UploadRow) {
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		when (row.status) {
			"uploading" -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
			"done" -> Icon(SvirkaIcons.CircleCheck, "Uploaded", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
			else -> Icon(SvirkaIcons.CircleX, "Upload failed", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
		}
		Text(
			text = row.name + (row.error?.let { " — $it" } ?: ""),
			style = SvirkaText.Small,
			color = if (row.status == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis
		)
	}
}

/** Svirka job card: rounded-lg, 1dp border, bg-background, p-3. */
@Composable
private fun ImportJobCard(job: ImportJob) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.border(1.dp, MaterialTheme.colorScheme.outlineVariant, SvirkaShapes.Lg)
			.background(MaterialTheme.colorScheme.background, SvirkaShapes.Lg)
			.padding(12.dp),
		horizontalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
			when (job.status) {
				"done" -> Icon(SvirkaIcons.CircleCheck, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
				"error" -> Icon(SvirkaIcons.CircleX, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
				else -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
			}
		}
		Column(Modifier.weight(1f)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(
					text = job.url,
					style = SvirkaText.RowTitle,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f)
				)
				Spacer(Modifier.width(8.dp))
				ImportStatusBadge(job.status)
			}
			val result = when (job.status) {
				"done" -> buildString {
					append("Added ${job.itemsAdded} ${if (job.itemsAdded == 1) "track" else "tracks"}")
					if (job.summary.isNotBlank()) append(" — ${job.summary}")
				}
				"error" -> job.error.ifBlank { stringResource(Res.string.info_import_failed) }
				else -> null
			}
			if (result != null) {
				Text(
					text = result,
					style = SvirkaText.Small,
					color = if (job.status == "error") MaterialTheme.colorScheme.error else mutedColor,
					maxLines = 3,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.padding(top = 4.dp)
				)
			}
		}
	}
}

@Composable
private fun ImportStatusBadge(status: String) {
	val (label, color) = when (status) {
		"queued" -> stringResource(Res.string.info_import_queued) to mutedColor
		"running" -> stringResource(Res.string.info_import_running) to MaterialTheme.colorScheme.onSurface
		"done" -> "Done" to MaterialTheme.colorScheme.primary
		"error" -> stringResource(Res.string.info_import_failed) to MaterialTheme.colorScheme.error
		else -> status to mutedColor
	}
	Text(
		text = label,
		style = SvirkaText.Small.copy(fontWeight = FontWeight.Medium),
		color = color,
		maxLines = 1,
		modifier = Modifier
			.background(color.copy(alpha = 0.15f), CircleShape)
			.padding(horizontal = 8.dp, vertical = 2.dp)
	)
}
