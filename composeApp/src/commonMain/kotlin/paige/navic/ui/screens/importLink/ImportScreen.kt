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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_import
import navic.composeapp.generated.resources.info_import_failed
import navic.composeapp.generated.resources.info_import_queued
import navic.composeapp.generated.resources.info_import_running
import navic.composeapp.generated.resources.title_import
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.ImportJob
import paige.navic.domain.manager.ImportManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.settings.BottomBarVisibilityMode
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

class ImportViewModel(private val manager: ImportManager) : ViewModel() {
	val jobs: StateFlow<List<ImportJob>>
		field = MutableStateFlow<List<ImportJob>>(emptyList())

	/** Last failure (server unreachable, no importer on this server, …); null when fine. */
	val error: StateFlow<String?>
		field = MutableStateFlow<String?>(null)

	val submitting: StateFlow<Boolean>
		field = MutableStateFlow(false)

	init {
		// Poll fast while something is downloading, slowly otherwise; stops with the screen.
		viewModelScope.launch {
			while (true) {
				refresh()
				delay(if (jobs.value.any { it.isActive }) 3_000 else 30_000)
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
	var text by rememberSaveable { mutableStateOf(initialText.orEmpty()) }
	val hasLinks = ImportManager.extractUrls(text).isNotEmpty()

	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	Scaffold(
		topBar = {
			if (!nested) {
				RootTopBar(
					title = { Text(stringResource(Res.string.action_import)) },
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
					text = "Add music to your library from a link — paste a song or playlist link.",
					style = SvirkaText.Body,
					color = mutedColor
				)
			}
			item {
				Column(Modifier.padding(top = 16.dp)) {
					Text("Paste music links", style = SvirkaText.RowTitle)
					Spacer(Modifier.height(8.dp))
					OutlinedTextField(
						value = text,
						onValueChange = { text = it },
						modifier = Modifier.fillMaxWidth(),
						placeholder = { Text("One song or playlist link per line…", style = SvirkaText.Body) },
						textStyle = SvirkaText.Body,
						shape = SvirkaShapes.Lg,
						minLines = 5,
						maxLines = 8
					)
					Spacer(Modifier.height(12.dp))
					Button(
						onClick = { viewModel.submit(text) { ok -> if (ok) text = "" } },
						enabled = hasLinks && !submitting,
						shape = CircleShape,
						colors = ButtonDefaults.buttonColors(
							containerColor = MaterialTheme.colorScheme.primary,
							contentColor = MaterialTheme.colorScheme.onPrimary
						),
						modifier = Modifier
							.fillMaxWidth()
							.height(40.dp)
					) {
						if (submitting) {
							CircularProgressIndicator(
								Modifier.size(18.dp),
								color = MaterialTheme.colorScheme.onPrimary,
								strokeWidth = 2.dp
							)
						} else {
							Icon(SvirkaIcons.Download, null, Modifier.size(16.dp))
							Spacer(Modifier.width(8.dp))
							Text(stringResource(Res.string.action_import), style = SvirkaText.RowTitle)
						}
					}
				}
			}
			error?.let { message ->
				item {
					Text(
						text = message,
						color = MaterialTheme.colorScheme.error,
						style = SvirkaText.Body
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
						hint = "Paste a song or playlist link above to add it to your library.",
						icon = SvirkaIcons.Download
					)
				}
			}
		}
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
