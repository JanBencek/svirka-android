package paige.navic.domain.manager

expect class UpdateManager {
	/**
	 * Downloads the APK at [url] (reporting 0..1 to [onProgress]) and opens the system installer.
	 * False where the platform can't install it itself: open the release page instead.
	 */
	suspend fun install(url: String, onProgress: (Float) -> Unit): Boolean
}
