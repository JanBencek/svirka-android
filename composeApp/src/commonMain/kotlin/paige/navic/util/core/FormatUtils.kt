package paige.navic.util.core

import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.Duration

/**
 * Formats a `Duration` as a `String` in HH:MM:SS
 * format. If there is no hours, the 00: at the
 * beginning will be omitted
 */
fun Duration.toHoursMinutesSeconds(): String {
	val totalSeconds = inWholeSeconds

	val hours = totalSeconds / 3600
	val minutes = (totalSeconds % 3600) / 60
	val seconds = totalSeconds % 60

	fun Long.twoDigits() = toString().padStart(2, '0')

	// Svirka/Spotify style: leading unit unpadded — 0:20, 3:55, 1:02:03.
	return if (hours > 0) {
		"$hours:${minutes.twoDigits()}:${seconds.twoDigits()}"
	} else {
		"$minutes:${seconds.twoDigits()}"
	}
}

/**
 * Formats a `Long` as a human-readable file size
 * string, e.g. 1 GB
 */
fun Long.toFileSize(): String {
	if (this <= 0) return "0 B"
	val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
	val digitGroups = (log10(this.toDouble()) / log10(1024.0)).toInt()

	val size = this / 1024.0.pow(digitGroups.toDouble())
	val roundedSize = (size * 100).toInt() / 100.0

	return "$roundedSize ${units[digitGroups]}"
}
