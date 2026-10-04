package paige.navic.ui.screens.login.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_error
import org.jetbrains.compose.resources.stringResource
import paige.navic.ui.core.LoginUiState
import paige.navic.ui.svirka.SvirkaText
import paige.navic.util.core.Logger

@Composable
fun LoginScreenError(
	loginUiState: LoginUiState
) {
	val spatialSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
	val effectSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
	AnimatedContent(
		(loginUiState as? LoginUiState.Error),
		modifier = Modifier.fillMaxWidth(),
		transitionSpec = {
			(fadeIn(
				animationSpec = effectSpec
			) + scaleIn(
				initialScale = 0.8f,
				animationSpec = spatialSpec
			)) togetherWith (fadeOut(
				animationSpec = effectSpec
			) + scaleOut(
				animationSpec = spatialSpec
			))
		}
	) {
		if (it != null) {
			LaunchedEffect(it.error) {
				Logger.e("LoginScreenError", "Login failed", it.error)
			}
			// Svirka: plain text-sm in the destructive colour.
			Text(
				text = it.error.message?.takeIf { msg -> msg.isNotBlank() }
					?: stringResource(Res.string.info_error),
				style = SvirkaText.Body,
				color = MaterialTheme.colorScheme.error,
				textAlign = TextAlign.Center,
				modifier = Modifier
					.fillMaxWidth()
					.padding(bottom = 12.dp)
			)
		}
	}
}
