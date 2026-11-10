package paige.navic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_app_version
import navic.composeapp.generated.resources.info_update
import navic.composeapp.generated.resources.title_about
import navic.composeapp.generated.resources.title_acknowledgements
import navic.composeapp.generated.resources.title_chat
import navic.composeapp.generated.resources.title_source
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.icons.Icons
import paige.navic.icons.outlined.ChevronForward
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.sheets.UpdateButton
import paige.navic.ui.components.sheets.UpdateChecker
import paige.navic.ui.navigation.Screen
import paige.navic.util.core.PlatformType

@Composable
fun SettingsAboutScreen() {
	@Suppress("DEPRECATION")
	val clipboard = LocalClipboardManager.current
	val uriHandler = LocalUriHandler.current
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	// a newer build, offered here even when the start-up sheet was skipped or update checks are off
	val updates = koinInject<UpdateChecker>()
	val android = platformContext.platformType == PlatformType.Android
	LaunchedEffect(android) { if (android) updates.check(platformContext.appVersion) }
	val release by updates.release.collectAsStateWithLifecycle()
	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_about)) },
				hideBack = hideBack
			)
		}
	) { innerPadding ->
		Column(
			Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(top = 16.dp, end = 16.dp, start = 16.dp)
		) {
			Form {
				SelectionContainer {
					val text = buildString {
						append(platformContext.name + "\n")
						append(
							stringResource(
								Res.string.info_app_version,
								platformContext.appVersion
							)
						)
					}
					FormRow(onClick = {
						clipboard.setText(AnnotatedString(text))
					}) {
						Text(text)
					}
				}
			}
			release?.takeIf { android }?.let { newer ->
				Form {
					FormRow {
						Column(Modifier.fillMaxWidth()) {
							Text(stringResource(Res.string.info_update, newer.tag))
							UpdateButton(newer, Modifier.fillMaxWidth().padding(top = 8.dp))
						}
					}
				}
			}
			Form {
				FormRow(onClick = {
					uriHandler.openUri("https://github.com/ssalggnikool/Navic")
				}) {
					Text(stringResource(Res.string.title_source))
					Icon(Icons.Outlined.ChevronForward, null)
				}
				FormRow(onClick = {
					uriHandler.openUri("https://discord.gg/TBcnNX66PH")
				}) {
					Text(stringResource(Res.string.title_chat))
					Icon(Icons.Outlined.ChevronForward, null)
				}
				FormRow(onClick = dropUnlessResumed {
					backStack.add(Screen.Settings.Acknowledgements)
				}) {
					Text(stringResource(Res.string.title_acknowledgements))
					Icon(Icons.Outlined.ChevronForward, null)
				}
			}
		}
	}
}
