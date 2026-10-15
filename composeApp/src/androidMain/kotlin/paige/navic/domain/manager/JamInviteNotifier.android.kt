package paige.navic.domain.manager

import android.Manifest
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri

/**
 * A Jam invite while Svirka isn't on screen: a notification that opens the app on the Jam's link, which joins it
 * (MainActivity.handleJamLink). On screen, or with notifications off, [show] returns false: the snack bar has it then.
 * Called for Connect's `invite` event and for the same invite pushed by FCM (PushService): one notification per Jam.
 */
class JamInviteNotifier(private val context: Context, private val icon: Int) {
	fun show(text: String, jamId: String): Boolean {
		val link = "https://music.bencit.com/jam/$jamId"
		val app = ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState)
		if (app.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) return false
		if (Build.VERSION.SDK_INT >= 33 &&
			context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
		) return false
		val manager = NotificationManagerCompat.from(context)
		if (!manager.areNotificationsEnabled()) return false
		manager.createNotificationChannel(
			NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH).setName("Jam invites").build()
		)
		// The launcher's own entry (whichever app icon is on), so it reaches MainActivity whatever the server's address
		val open = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return false)
			.setAction(Intent.ACTION_VIEW)
			.setData(link.toUri())
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
		val tap = PendingIntent.getActivity(
			context, link.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
		)
		manager.notify(
			link, 1,
			NotificationCompat.Builder(context, CHANNEL)
				.setSmallIcon(icon)
				.setContentTitle("Jam invite")
				.setContentText(text)
				.setCategory(NotificationCompat.CATEGORY_SOCIAL)
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setContentIntent(tap)
				.addAction(0, "Join", tap)
				.setAutoCancel(true)
				.build()
		)
		return true
	}

	private companion object {
		const val CHANNEL = "jam-invites"
	}
}
