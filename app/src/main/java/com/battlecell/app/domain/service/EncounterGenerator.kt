package com.battlecell.app.domain.service

import com.battlecell.app.data.nearby.BluetoothDeviceInfo
import com.battlecell.app.data.nearby.WifiDeviceInfo
import com.battlecell.app.domain.model.CharacterAttributes
import com.battlecell.app.domain.model.EncounterProfile
import com.battlecell.app.domain.model.EncounterSource
import java.security.MessageDigest
import kotlin.math.max

class EncounterGenerator {
    private val adjectives = listOf(
        "Shadow", "Iron", "Crimson", "Echo", "Static", "Silver", "Obsidian", "Nova", "Phantom", "Azure"
    )
    private val nouns = listOf(
        "Warden", "Blade", "Specter", "Hunter", "Sentinel", "Rogue", "Drifter", "Cipher", "Vanguard", "Revenant"
    )

    fun fromWifi(info: WifiDeviceInfo): EncounterProfile {
        val fingerprint = info.bssid.lowercase()
        val hash = hashBytes(fingerprint)
        val codename = codename(hash)
        val attributes = buildAttributes(hash)
        val displayName = info.ssid?.takeIf { it.isNotBlank() } ?: codename

        val powerBoost = max(0, info.signalLevel + 100)
        return EncounterProfile(
            id = fingerprint,
            displayName = displayName,
            isPlayer = false,
            attributes = attributes,
            powerScore = attributes.combatRating + powerBoost / 4,
            source = EncounterSource.WIFI,
            deviceFingerprint = fingerprint
        )
    }

    fun fromBluetooth(info: BluetoothDeviceInfo): EncounterProfile {
        val fingerprint = info.address.lowercase()
        val hash = hashBytes(fingerprint)
        val codename = codename(hash)
        val attributes = buildAttributes(hash.rotate())
        val displayName = info.name?.takeIf { it.isNotBlank() } ?: codename
        val signalInfluence = max(0, info.rssi + 100)

        return EncounterProfile(
            id = fingerprint,
            displayName = displayName,
            isPlayer = false,
            attributes = attributes,
            powerScore = attributes.combatRating + signalInfluence / 5,
            source = EncounterSource.BLUETOOTH,
            deviceFingerprint = fingerprint
        )
    }

    fun merge(primary: EncounterProfile?, secondary: EncounterProfile): EncounterProfile {
        return if (primary == null) {
            secondary
        } else {
            primary.copy(
                attributes = primary.attributes.mergeWith(secondary.attributes),
                powerScore = max(primary.powerScore, secondary.powerScore),
                lastSeenEpoch = System.currentTimeMillis(),
                source = secondary.source
            )
        }
    }

    private fun buildAttributes(hash: ByteArray): CharacterAttributes {
        val power = 20 + (hash[0].toPositiveInt() % 40)
        val agility = 15 + (hash[1].toPositiveInt() % 35)
        val endurance = 15 + (hash[2].toPositiveInt() % 35)
        val focus = 10 + (hash[3].toPositiveInt() % 30)
        return CharacterAttributes(
            power = power,
            agility = agility,
            endurance = endurance,
            focus = focus
        )
    }

    private fun codename(hash: ByteArray): String {
        val adjIndex = hash[4].toPositiveInt() % adjectives.size
        val nounIndex = hash[5].toPositiveInt() % nouns.size
        return "${adjectives[adjIndex]} ${nouns[nounIndex]}"
    }

    private fun hashBytes(input: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))

    private fun Byte.toPositiveInt(): Int = toInt() and 0xFF

    private fun ByteArray.rotate(): ByteArray {
        if (isEmpty()) return this
        val rotated = clone()
        val first = rotated[0]
        for (i in 0 until size - 1) {
            rotated[i] = rotated[i + 1]
        }
        rotated[lastIndex] = first
        return rotated
    }
}
