package com.dev.salt.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * True if the device has an active network interface (wifi or cellular).
 *
 * Uses NET_CAPABILITY_INTERNET (the network's intended purpose), not
 * NET_CAPABILITY_VALIDATED: the SALT server may be a LAN-local server
 * reachable without an internet uplink, so "has a network" must still
 * attempt the connection. This only short-circuits the genuine
 * "no network at all" case.
 */
fun hasActiveNetwork(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
