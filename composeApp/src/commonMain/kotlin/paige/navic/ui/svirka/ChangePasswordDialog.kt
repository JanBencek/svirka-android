package paige.navic.ui.svirka

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_cancel
import navic.composeapp.generated.resources.password_changed
import navic.composeapp.generated.resources.password_current
import navic.composeapp.generated.resources.password_error
import navic.composeapp.generated.resources.password_mismatch
import navic.composeapp.generated.resources.password_new
import navic.composeapp.generated.resources.password_repeat
import navic.composeapp.generated.resources.password_too_short
import navic.composeapp.generated.resources.title_change_password
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.domain.manager.ConnectManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.dialogs.FormDialog

/** Settings → Change password (`POST /api/auth/password`); the stored login switches to the new one right away. */
@Composable
fun ChangePasswordDialog(onDismissRequest: () -> Unit) {
	val connect = koinInject<ConnectManager>()
	val session = koinInject<SessionManager>()
	val snackBar = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()
	val current = rememberTextFieldState()
	val new = rememberTextFieldState()
	val again = rememberTextFieldState()
	var sending by remember { mutableStateOf(false) }
	var error by remember { mutableStateOf<String?>(null) }
	val genericError = stringResource(Res.string.password_error)

	val tooShort = new.text.isNotEmpty() && new.text.length < 8
	val mismatch = again.text.isNotEmpty() && again.text.toString() != new.text.toString()
	val valid = current.text.isNotEmpty() && new.text.length >= 8 && again.text.toString() == new.text.toString()

	FormDialog(
		onDismissRequest = onDismissRequest,
		title = { Text(stringResource(Res.string.title_change_password)) },
		buttons = {
			FormButton(
				onClick = {
					sending = true
					error = null
					scope.launch {
						val result = try {
							connect.changePassword(current.text.toString(), new.text.toString())
						} catch (e: CancellationException) {
							throw e
						} catch (_: Exception) {
							""
						}
						sending = false
						if (result == null) {
							session.refreshClient() // Subsonic calls (and queued stream URLs) use the new password
							snackBar.notify(Res.string.password_changed)
							onDismissRequest()
						} else error = result.ifBlank { genericError }
					}
				},
				enabled = !sending && valid,
				color = MaterialTheme.colorScheme.primary
			) {
				if (sending) {
					CircularProgressIndicator(modifier = Modifier.size(20.dp))
				} else {
					Text(stringResource(Res.string.title_change_password))
				}
			}
			FormButton(
				onClick = onDismissRequest,
				enabled = !sending,
				content = { Text(stringResource(Res.string.action_cancel)) }
			)
		},
		content = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
				SecureTextField(
					state = current,
					label = { Text(stringResource(Res.string.password_current)) },
					enabled = !sending
				)
				SecureTextField(
					state = new,
					label = { Text(stringResource(Res.string.password_new)) },
					isError = tooShort,
					supportingText = if (tooShort) {
						{ Text(stringResource(Res.string.password_too_short)) }
					} else null,
					enabled = !sending
				)
				SecureTextField(
					state = again,
					label = { Text(stringResource(Res.string.password_repeat)) },
					isError = mismatch,
					supportingText = if (mismatch) {
						{ Text(stringResource(Res.string.password_mismatch)) }
					} else null,
					enabled = !sending
				)
			}
		}
	)
}
