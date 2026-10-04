package paige.navic.androidApp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.koin.android.ext.android.inject
import paige.navic.App
import paige.navic.domain.manager.ImportManager
import paige.navic.domain.manager.PermissionManager

class MainActivity : ComponentActivity() {
	private val permissionManager: PermissionManager by inject()
	private val importManager: ImportManager by inject()
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		permissionManager.registerLauncher(this)
		if (savedInstanceState == null) handleShare(intent)
		enableEdgeToEdge()
		setContent { App() }
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		handleShare(intent)
	}

	/** "Share → Navic" from YouTube/Spotify/…: hand the text to the link-import screen. */
	private fun handleShare(intent: Intent?) {
		if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
		intent.getStringExtra(Intent.EXTRA_TEXT)?.let { importManager.pendingShare.value = it }
	}
}
