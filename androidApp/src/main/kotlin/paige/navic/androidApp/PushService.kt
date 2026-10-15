package paige.navic.androidApp

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.koin.android.ext.android.inject
import paige.navic.domain.manager.ConnectManager
import paige.navic.domain.manager.JamInviteNotifier

/** FCM (builds with GOOGLE_SERVICES_JSON): Svirka pushes Jam invites here, so they reach the app while it's closed. */
class PushService : FirebaseMessagingService() {
	private val connect: ConnectManager by inject()
	private val invites: JamInviteNotifier by inject()

	override fun onNewToken(token: String) {
		connect.pushToken = token
	}

	override fun onMessageReceived(message: RemoteMessage) {
		val data = message.data
		if (data["type"] == "jam-invite") invites.show(data["text"] ?: return, data["id"] ?: return)
	}
}
