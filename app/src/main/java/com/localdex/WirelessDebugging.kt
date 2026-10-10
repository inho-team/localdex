package com.localdex

import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Reads the system's Wireless debugging switch so the UI can direct the user to Settings.
 */
object WirelessDebugging {
    private const val TAG = "WirelessDebugging"

    // Settings.Global.ADB_WIFI_ENABLED is @hide, so the key is spelled out.
    private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"

    fun isEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, ADB_WIFI_ENABLED, 0) == 1
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $ADB_WIFI_ENABLED", e)
            false
        }
    }
}
