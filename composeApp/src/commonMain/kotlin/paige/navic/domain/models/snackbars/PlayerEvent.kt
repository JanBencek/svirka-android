package paige.navic.domain.models.snackbars

import org.jetbrains.compose.resources.StringResource

data class PlayerEvent(
	val resource: StringResource,
	val args: List<Any> = emptyList(),
	val action: StringResource? = null, // a button on the snack bar; tapping it runs [onAction]
	val onAction: () -> Unit = {}
)
