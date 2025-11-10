package com.battlecell.app.data.nearby

data class WifiDeviceInfo(
    val ssid: String?,
    val bssid: String,
    val signalLevel: Int
)
