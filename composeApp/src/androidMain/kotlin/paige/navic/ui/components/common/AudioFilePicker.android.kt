package paige.navic.ui.components.common

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberAudioFilePicker(onPicked: (List<PickedFile>) -> Unit): (() -> Unit)? {
	val context = LocalContext.current
	val callback by rememberUpdatedState(onPicked)
	val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
		if (uris.isNotEmpty()) callback(uris.map { uri ->
			PickedFile(displayName(context, uri)) {
				context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
					?: error("Couldn't read the file")
			}
		})
	}
	return { launcher.launch(arrayOf("audio/*")) }
}

private fun displayName(context: Context, uri: Uri): String =
	context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
		if (it.moveToFirst()) it.getString(0) else null
	} ?: uri.lastPathSegment ?: "audio.mp3"
