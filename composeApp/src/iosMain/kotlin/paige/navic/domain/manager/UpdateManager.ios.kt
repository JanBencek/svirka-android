package paige.navic.domain.manager

actual class UpdateManager {
	actual suspend fun install(url: String, onProgress: (Float) -> Unit) = false
}
