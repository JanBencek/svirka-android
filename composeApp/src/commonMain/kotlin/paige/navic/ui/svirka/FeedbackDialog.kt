package paige.navic.ui.svirka

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import navic.composeapp.generated.resources.action_send
import navic.composeapp.generated.resources.feedback_error
import navic.composeapp.generated.resources.feedback_hint
import navic.composeapp.generated.resources.feedback_sent
import navic.composeapp.generated.resources.title_feedback
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.ConnectManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.dialogs.FormDialog

/** Settings → Send feedback: a message to the Svirka server's admin (`POST /api/feedback`). */
@Composable
fun FeedbackDialog(onDismissRequest: () -> Unit) {
	val connect = koinInject<ConnectManager>()
	val snackBar = koinInject<SnackBarManager>()
	val version = LocalPlatformContext.current.appVersion
	val scope = rememberCoroutineScope()
	val message = rememberTextFieldState()
	var sending by remember { mutableStateOf(false) }
	var failed by remember { mutableStateOf(false) }

	FormDialog(
		onDismissRequest = onDismissRequest,
		title = { Text(stringResource(Res.string.title_feedback)) },
		buttons = {
			FormButton(
				onClick = {
					sending = true
					failed = false
					scope.launch {
						val sent = try {
							connect.sendFeedback(message.text.toString().trim(), version)
						} catch (e: CancellationException) {
							throw e
						} catch (_: Exception) {
							false
						}
						sending = false
						if (sent) {
							snackBar.notify(Res.string.feedback_sent)
							onDismissRequest()
						} else failed = true
					}
				},
				enabled = !sending && message.text.isNotBlank(),
				color = MaterialTheme.colorScheme.primary
			) {
				if (sending) {
					CircularProgressIndicator(modifier = Modifier.size(20.dp))
				} else {
					Text(stringResource(Res.string.action_send))
				}
			}
			FormButton(
				onClick = onDismissRequest,
				enabled = !sending,
				content = { Text(stringResource(Res.string.action_cancel)) }
			)
		},
		content = {
			if (failed) {
				Text(
					stringResource(Res.string.feedback_error),
					color = MaterialTheme.colorScheme.error
				)
			}
			TextField(
				state = message,
				placeholder = { Text(stringResource(Res.string.feedback_hint)) },
				inputTransformation = InputTransformation.maxLength(4000),
				lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 4, maxHeightInLines = 8)
			)
		}
	)
}
