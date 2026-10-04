package paige.navic.ui.screens.login.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_open_settings
import navic.composeapp.generated.resources.notice_local_network_denied
import navic.composeapp.generated.resources.option_custom_headers
import navic.composeapp.generated.resources.subtitle_local_network_denied
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.domain.manager.LoginManager
import paige.navic.domain.manager.PermissionManager
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Error
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.dialogs.FormDialog
import paige.navic.ui.core.LoginUiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.svirka.SvirkaIcons
import paige.navic.ui.svirka.SvirkaShapes
import paige.navic.ui.svirka.SvirkaText
import paige.navic.ui.svirka.mutedColor

@Composable
fun LoginScreenContent(innerPadding: PaddingValues) {
	val viewModel = koinInject<LoginManager>()
	val loginState by viewModel.loginState.collectAsStateWithLifecycle()

	val instanceState = viewModel.instanceState
	val usernameState = viewModel.usernameState
	val passwordState = viewModel.passwordState

	val isBusy = loginState is LoginUiState.Loading || loginState is LoginUiState.Syncing

	val haptics = LocalHapticFeedback.current
	val backStack = LocalNavStack.current
	val focusManager = LocalFocusManager.current

	val instanceFocusRequester = remember { FocusRequester() }
	val usernameFocusRequester = remember { FocusRequester() }
	val passwordFocusRequester = remember { FocusRequester() }

	val permissionManager = koinInject<PermissionManager>()
	val loginScope = rememberCoroutineScope()
	var localNetworkDenied by rememberSaveable { mutableStateOf(false) }
	val login: () -> Unit = {
		loginScope.launch {
			if (!permissionManager.requestLocalNetworkPermission()) {
				localNetworkDenied = true
				return@launch
			}

			if (!viewModel.login()) {
				haptics.performHapticFeedback(HapticFeedbackType.Reject)
				when {
					viewModel.instanceError -> instanceFocusRequester.requestFocus()
					viewModel.usernameError -> usernameFocusRequester.requestFocus()
					viewModel.passwordError -> passwordFocusRequester.requestFocus()
				}
			}
		}
	}

	LaunchedEffect(loginState) {
		if (loginState is LoginUiState.Success) {
			backStack.clear()
			backStack.add(Screen.SongList())
		}
	}

	Box(Modifier.fillMaxSize()) {
		LoginScreenProgress(
			modifier = Modifier
				.align(Alignment.TopCenter)
				.padding(top = innerPadding.calculateTopPadding()),
			isBusy = isBusy,
			loginUiState = loginState
		)

		// Svirka web login: centred card, max-w-sm, rounded-2xl, p-8.
		BoxWithConstraints(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding)
				.consumeWindowInsets(innerPadding)
				.imePadding()
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.heightIn(min = maxHeight)
					.padding(16.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center
			) {
				Column(
					modifier = Modifier
						.widthIn(max = 384.dp)
						.fillMaxWidth()
						.background(MaterialTheme.colorScheme.surfaceContainer, SvirkaShapes.Xxl)
						.padding(32.dp)
				) {
					Row(
						modifier = Modifier.align(Alignment.CenterHorizontally),
						verticalAlignment = Alignment.CenterVertically
					) {
						Icon(
							SvirkaIcons.Sparkles,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(32.dp)
						)
						Spacer(Modifier.width(8.dp))
						Text("Svirka", style = SvirkaText.Heading)
					}

					Spacer(Modifier.height(24.dp))

					LoginScreenError(loginUiState = loginState)

					LoginScreenFields(
						isBusy = isBusy,
						instanceState = instanceState,
						instanceError = viewModel.instanceError,
						instanceFocusRequester = instanceFocusRequester,
						onInstanceFocusChanged = { viewModel.validateInstance() },
						usernameState = usernameState,
						usernameError = viewModel.usernameError,
						usernameFocusRequester = usernameFocusRequester,
						onUsernameFocusChanged = { viewModel.validateUsername() },
						passwordState = passwordState,
						passwordError = viewModel.passwordError,
						passwordFocusRequester = passwordFocusRequester,
						onPasswordFocusChanged = { viewModel.validatePassword() },
						onLogin = login
					)

					Spacer(Modifier.height(16.dp))

					LoginScreenSyncStatus(loginUiState = loginState)
					Button(
						modifier = Modifier
							.fillMaxWidth()
							.height(40.dp),
						onClick = {
							login()
						},
						enabled = !isBusy,
						shape = CircleShape,
						colors = ButtonDefaults.buttonColors(
							containerColor = MaterialTheme.colorScheme.primary,
							contentColor = MaterialTheme.colorScheme.onPrimary
						)
					) {
						Text(text = "Sign in", style = SvirkaText.RowTitle)
					}

					Spacer(Modifier.height(16.dp))

					Text(
						text = "Sign in with your music server account",
						style = SvirkaText.Small,
						color = mutedColor,
						textAlign = TextAlign.Center,
						modifier = Modifier.fillMaxWidth()
					)

					Spacer(Modifier.height(8.dp))

					Text(
						text = stringResource(Res.string.option_custom_headers),
						style = SvirkaText.Small,
						color = mutedColor,
						textDecoration = TextDecoration.Underline,
						modifier = Modifier
							.align(Alignment.CenterHorizontally)
							.clickable(onClick = dropUnlessResumed {
								backStack.lastOrNull()?.let {
									if (it is Screen.Login) {
										backStack.add(Screen.Settings.CustomHeaders)
										focusManager.clearFocus(true)
									}
								}
							})
					)
				}
			}
		}
	}

	if (localNetworkDenied) {
		FormDialog(
			onDismissRequest = { localNetworkDenied = false },
			icon = { Icon(Icons.Outlined.Error, null) },
			title = { Text(stringResource(Res.string.notice_local_network_denied)) },
			content = { Text(stringResource(Res.string.subtitle_local_network_denied)) },
			buttons = {
				FormButton(
					onClick = {
						localNetworkDenied = false
						permissionManager.openPermissionsSettings()
					}
				) {
					Text(stringResource(Res.string.action_open_settings))
				}
			}
		)
	}
}
