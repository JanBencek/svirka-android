package paige.navic.domain.manager

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/** Self-update: every CI build is signed with the same key, so the new APK installs over this one. */
actual class UpdateManager(
	private val context: Context
) {
	actual suspend fun install(url: String, onProgress: (Float) -> Unit): Boolean {
		if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
			// allow "Install unknown apps" for Svirka first; back in the app, Update again
			context.startActivity(
				Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			)
			return true
		}

		val file = File(context.cacheDir, "updates/Svirka.apk")
		withContext(Dispatchers.IO) {
			file.parentFile?.mkdirs()
			val connection = URL(url).openConnection() // follows GitHub's redirect to the asset
			val total = connection.contentLengthLong
			connection.getInputStream().use { input ->
				file.outputStream().use { output ->
					val buffer = ByteArray(64 * 1024)
					var done = 0L
					while (true) {
						val read = input.read(buffer)
						if (read < 0) break
						output.write(buffer, 0, read)
						done += read
						if (total > 0) onProgress(done.toFloat() / total)
					}
				}
			}
		}

		val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
		context.startActivity(
			Intent(Intent.ACTION_VIEW)
				.setDataAndType(uri, "application/vnd.android.package-archive")
				.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
		)
		return true
	}
}
