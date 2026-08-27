package com.rhythm.app.optimem

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs

/** Reads real, legitimate, permission-free device signals for DecisionEngine. */
object DeviceContextProvider {

    fun read(context: Context): DeviceContext {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPercent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val stat = StatFs(Environment.getDataDirectory().path)
        val availableMB = (stat.availableBytes / (1024 * 1024))

        return DeviceContext(
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            isWifi = isWifi,
            availableStorageMB = availableMB
        )
    }
}
