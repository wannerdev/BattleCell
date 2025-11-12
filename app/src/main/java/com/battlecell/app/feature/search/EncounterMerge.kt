package com.battlecell.app.feature.search

import com.battlecell.app.data.nearby.NearbyDiscoverySnapshot
import com.battlecell.app.domain.model.EncounterProfile
import com.battlecell.app.domain.service.EncounterGenerator

internal object EncounterMerge {
    fun merge(
        existing: Map<String, EncounterProfile>,
        snapshot: NearbyDiscoverySnapshot,
        generator: EncounterGenerator
    ): List<EncounterProfile> {
        val merged = mutableMapOf<String, EncounterProfile>()

        snapshot.wifiDevices.forEach { device ->
            val incoming = generator.fromWifi(device)
            val current = merged[incoming.deviceFingerprint] ?: existing[incoming.deviceFingerprint]
            merged[incoming.deviceFingerprint] = generator.merge(current, incoming)
        }

        snapshot.bluetoothDevices.forEach { device ->
            val incoming = generator.fromBluetooth(device)
            val current = merged[incoming.deviceFingerprint] ?: existing[incoming.deviceFingerprint]
            merged[incoming.deviceFingerprint] = generator.merge(current, incoming)
        }

        return merged.values.sortedByDescending { it.powerScore }
    }
}
