package paige.navic.ui.components.common

import androidx.compose.runtime.Composable

/** A file the user picked; [read] loads its bytes (call it off the main thread). */
class PickedFile(val name: String, val read: () -> ByteArray)

/** Returns a function that opens the system picker for audio files, or null where unsupported. */
@Composable
expect fun rememberAudioFilePicker(onPicked: (List<PickedFile>) -> Unit): (() -> Unit)?
