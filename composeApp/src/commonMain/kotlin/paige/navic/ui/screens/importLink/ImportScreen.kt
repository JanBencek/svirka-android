package paige.navic.ui.screens.importLink

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import navic.composeapp.generated.resources.info_import_hint
import navic.composeapp.generated.resources.info_import_queued
import navic.composeapp.generated.resources.info_import_running
import navic.composeapp.generated.resources.info_no_imports
import navic.composeapp.generated.resources.title_import
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.domain.manager.ImportJob
import paige.navic.domain.manager.ImportManager
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Check
import paige.navic.icons.outlined.Error
import paige.navic.icons.outlined.Link
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.layouts.NestedTopBar

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

	Scaffold(
		topBar = { NestedTopBar({ Text(stringResource(Res.string.title_import)) }) }
	) { contentPadding ->
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				top = contentPadding.calculateTopPadding() + 8.dp,
				bottom = contentPadding.calculateBottomPadding() + 16.dp
			),
			verticalArrangement = Arrangement.spacedBy(4.dp)
		) {
			item {
				OutlinedTextField(
					value = text,
					onValueChange = { text = it },
					modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
					label = { Text(stringResource(Res.string.info_import_hint)) },
					minLines = 2,
					maxLines = 6
				)
			}
			item {
				Button(
					onClick = { viewModel.submit(text) { ok -> if (ok) text = "" } },
					enabled = hasLinks && !submitting,
					modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
				) {
					if (submitting) {
						CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
					} else {
						Text(stringResource(Res.string.action_import))
					}
				}
			}
			error?.let { message ->
				item {
					Text(
						text = message,
						color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.bodyMedium,
						modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
					)
				}
			}
			items(jobs, key = { it.id }) { job -> ImportJobRow(job) }
			if (jobs.isEmpty() && error == null) {
				item {
					ContentUnavailable(
						icon = Icons.Outlined.Link,
						label = stringResource(Res.string.info_no_imports)
					)
				}
			}
		}
	}
}

@Composable
private fun ImportJobRow(job: ImportJob) {
	ListItem(
		leadingContent = {
			when (job.status) {
				"done" -> Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
				"error" -> Icon(Icons.Outlined.Error, null, tint = MaterialTheme.colorScheme.error)
				else -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
			}
		},
		headlineContent = {
			Text(
				text = job.summary.ifBlank { job.url },
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
		},
		supportingContent = {
			Text(
				text = when (job.status) {
					"queued" -> stringResource(Res.string.info_import_queued)
					"running" -> stringResource(Res.string.info_import_running)
					"error" -> job.error.ifBlank { stringResource(Res.string.info_import_failed) }
					else -> job.url
				},
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
		}
	)
}
