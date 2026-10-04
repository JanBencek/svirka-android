package paige.navic.ui.svirka

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Lucide icons (ISC) — the exact set the Svirka web app uses, so both look alike.
 * Lucide is stroke-based (24 viewport, stroke 2, round caps/joins); `filled`
 * variants also fill the outline (heart when liked, play/pause).
 * Colour comes from Icon's tint (paths use black, which tint replaces).
 */
object SvirkaIcons {
	private fun circle(cx: Float, cy: Float, r: Float) =
		"M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

	private fun lucide(name: String, vararg paths: String, filled: Boolean = false) =
		ImageVector.Builder(
			name = "Lucide.$name",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 24f,
			viewportHeight = 24f
		).apply {
			paths.forEach { d ->
				addPath(
					pathData = addPathNodes(d),
					fill = if (filled) SolidColor(Color.Black) else null,
					stroke = SolidColor(Color.Black),
					strokeLineWidth = 2f,
					strokeLineCap = StrokeCap.Round,
					strokeLineJoin = StrokeJoin.Round
				)
			}
		}.build()

	private const val HEART =
		"M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5"
	private const val PLAY =
		"M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z"

	val Heart by lazy { lucide("Heart", HEART) }
	val HeartFilled by lazy { lucide("HeartFilled", HEART, filled = true) }
	val Play by lazy { lucide("Play", PLAY, filled = true) }
	val Pause by lazy { lucide("Pause", "M15 4h3v16h-3z", "M6 4h3v16H6z", filled = true) }
	val Sparkles by lazy {
		lucide(
			"Sparkles",
			"M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
			"M20 2v4", "M22 4h-4", circle(4f, 20f, 2f)
		)
	}
	val ListMusic by lazy { lucide("ListMusic", "M16 5H3", "M11 12H3", "M11 19H3", "M21 16V5", circle(18f, 16f, 3f)) }
	val Download by lazy { lucide("Download", "M12 15V3", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5") }
	val Shuffle by lazy {
		lucide(
			"Shuffle",
			"m18 14 4 4-4 4", "m18 2 4 4-4 4",
			"M2 18h1.973a4 4 0 0 0 3.3-1.7l5.454-8.6a4 4 0 0 1 3.3-1.7H22",
			"M2 6h1.972a4 4 0 0 1 3.6 2.2",
			"M22 18h-6.041a4 4 0 0 1-3.3-1.8l-.359-.45"
		)
	}
	val Plus by lazy { lucide("Plus", "M5 12h14", "M12 5v14") }
	val History by lazy { lucide("History", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5", "M12 7v5l4 2") }
	val Music4 by lazy { lucide("Music4", "M9 18V5l12-2v13", "m9 9 12-2", circle(6f, 18f, 3f), circle(18f, 16f, 3f)) }
	val CircleCheck by lazy { lucide("CircleCheck", circle(12f, 12f, 10f), "m9 12 2 2 4-4") }
	val CircleX by lazy { lucide("CircleX", circle(12f, 12f, 10f), "m15 9-6 6", "m9 9 6 6") }
	val Search by lazy { lucide("Search", "m21 21-4.34-4.34", circle(11f, 11f, 8f)) }
}
