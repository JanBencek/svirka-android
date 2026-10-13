package paige.navic.ui.components.common

import androidx.compose.runtime.Composable

@Composable
actual fun rememberAudioFilePicker(onPicked: (List<PickedFile>) -> Unit): (() -> Unit)? = null
