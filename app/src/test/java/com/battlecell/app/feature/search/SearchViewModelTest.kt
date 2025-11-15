package com.battlecell.app.feature.search

import com.battlecell.app.data.nearby.BluetoothDeviceInfo
import com.battlecell.app.data.nearby.NearbyDiscoverySnapshot
import com.battlecell.app.data.nearby.WifiDeviceInfo
import com.battlecell.app.domain.service.EncounterGenerator
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchViewModelTest {

    private val generator = EncounterGenerator()

    @Test
    fun mergeCombinesWifiAndBluetoothEncounters() {
        val snapshot = NearbyDiscoverySnapshot(
            bluetoothDevices = listOf(
                BluetoothDeviceInfo(name = "Knight One", address = "AA:BB:CC:DD:01", rssi = -45),
                BluetoothDeviceInfo(name = "Knight Two", address = "AA:BB:CC:DD:02", rssi = -53)
            ),
            wifiDevices = listOf(
                WifiDeviceInfo(ssid = "Rampart", bssid = "cc:dd:ee:ff:00:01", signalLevel = -32)
            )
        )

        val encounters = EncounterMerge.merge(
            existing = emptyMap(),
            snapshot = snapshot,
            generator = generator,
            player = null
        )

        assertEquals(3, encounters.size)
        val fingerprints = encounters.map { it.deviceFingerprint }.toSet()
        assert(fingerprints.contains("aa:bb:cc:dd:01"))
        assert(fingerprints.contains("aa:bb:cc:dd:02"))
        assert(fingerprints.contains("cc:dd:ee:ff:00:01"))
    }
}
