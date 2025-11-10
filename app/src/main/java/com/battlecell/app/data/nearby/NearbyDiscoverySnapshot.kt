package com.battlecell.app.data.nearby

data class NearbyDiscoverySnapshot(
    val wifiDevices: List<WifiDeviceInfo> = emptyList(),
    val bluetoothDevices: List<BluetoothDeviceInfo> = emptyList()
)
