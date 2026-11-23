package com.example.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observes real Android network connectivity via [ConnectivityManager]
 * (using the already-declared normal permission `ACCESS_NETWORK_STATE`).
 *
 * Used to strictly gate upstream comparisons against original Magic Set Editor
 * and DataEditorX repositories so comparisons only run when the device is officially online.
 */
class NetworkConnectivityMonitor(context: Context) {

    private val connectivityManager: ConnectivityManager? =
        runCatching {
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }.getOrNull()

    private val _isOnline = MutableStateFlow(checkIsOnlineNow())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        registerCallbackSafely()
    }

    fun checkIsOnlineNow(): Boolean {
        val cm = connectivityManager ?: return false
        return runCatching {
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            hasInternet && isValidated
        }.getOrDefault(false)
    }

    private fun registerCallbackSafely() {
        val cm = connectivityManager ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _isOnline.value = checkIsOnlineNow()
                    }

                    override fun onLost(network: Network) {
                        _isOnline.value = checkIsOnlineNow()
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        _isOnline.value = hasInternet && isValidated
                    }
                }
            )
        }
    }
}
