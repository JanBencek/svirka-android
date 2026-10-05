package paige.navic.domain.manager

import android.annotation.SuppressLint
import android.content.Context
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import paige.navic.domain.models.settings.OfflineMode
import android.net.ConnectivityManager as AndroidConnectivityManager

private data class NetworkStatus(
	val isOnline: Boolean = false,
	val isCellular: Boolean = false
) {
	companion object {
		fun fromCaps(
			caps: NetworkCapabilities
		) = NetworkStatus(
			// Not NET_CAPABILITY_VALIDATED: validation = reaching Google's connectivity
			// check, which fails behind Pi-hole/VPN/locked-down Wi-Fi even though the
			// music server is reachable — the app then refused to stream. Only a
			// captive portal (Wi-Fi login page) really means "can't reach the server".
			isOnline = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
				&& !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
			isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
				|| !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
		)
	}
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalCoroutinesApi::class)
actual class ConnectivityManager(
	context: Context,
	private val preferenceManager: PreferenceManager
) {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val dispatcher = Dispatchers.IO
	private val started = SharingStarted.WhileSubscribed(5000)
	private val connectivityManager =
		context.getSystemService(Context.CONNECTIVITY_SERVICE) as AndroidConnectivityManager

	private val networkStatus = callbackFlow {
		val callback = object : AndroidConnectivityManager.NetworkCallback() {
			override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
				super.onCapabilitiesChanged(network, caps)
				trySend(NetworkStatus.fromCaps(caps))
			}

			override fun onLost(network: Network) {
				super.onLost(network)
				trySend(NetworkStatus())
			}
		}

		// Default network only: a per-network callback let any other network (e.g. an
		// unvalidated mobile connection in the background) flip the status.
		connectivityManager.registerDefaultNetworkCallback(callback)

		trySend(
			connectivityManager
				.getNetworkCapabilities(connectivityManager.activeNetwork)
				?.let { NetworkStatus.fromCaps(it) }
				?: NetworkStatus()
		)

		awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
	}
		.flowOn(dispatcher)
		.conflate()
		.stateIn(scope, started, NetworkStatus())

	actual val isCellular = networkStatus
		.map { it.isCellular }
		.distinctUntilChanged()
		.flowOn(dispatcher)
		.stateIn(scope, started, false)

	actual val isOnline = networkStatus
		.mapLatest { status ->
			when (preferenceManager.offlineMode) {
				OfflineMode.Forced -> false
				OfflineMode.NoWiFi -> status.isOnline && !status.isCellular
				else -> status.isOnline
			}
		}
		.distinctUntilChanged()
		.flowOn(dispatcher)
		.stateIn(scope, started, true)
}
