package com.battlecell.app.domain.service

import com.battlecell.app.data.nearby.BluetoothDeviceInfo
import com.battlecell.app.data.nearby.WifiDeviceInfo
import com.battlecell.app.domain.model.CharacterAttributes
import com.battlecell.app.domain.model.EncounterProfile
import com.battlecell.app.domain.model.EncounterSource
import com.battlecell.app.domain.model.PlayerCharacter
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

class EncounterGenerator {
    private val givenNames = listOf(
        "Lenore", "Rowan", "Cassia", "Torren", "Nyra", "Bram", "Isolde", "Aldric", "Sabin", "Mira", "Jorah", "Sylas"
    )
    private val outlawTitles = listOf("Beggar", "Thief", "Cutpurse", "Vagabond", "Scoundrel")
    private val knightTitles = listOf("Knight", "Sentinel", "Dragoon", "Vanguard", "Swornblade")
    private val paladinTitles = listOf("Paladin", "Justicar", "Templar", "Lightwarden", "Sunhammer")
    private val dragonNames = listOf("Ashwyrm", "Cinderfang", "Nightmaw", "Verdigris", "Stormscale")
    private val dragonTitles = listOf("Dragon", "Ancient Wyrm", "Sky Tyrant", "World-Eater", "Ember Queen")

    fun fromWifi(info: WifiDeviceInfo, player: PlayerCharacter? = null): EncounterProfile {
        val fingerprint = info.bssid.lowercase()
        val hash = hashBytes(fingerprint)
        val styled = stylizeEncounter(hash, buildAttributes(hash), player)
        val powerBoost = max(0, info.signalLevel + 100)
        return EncounterProfile(
            id = fingerprint,
            displayName = styled.displayName,
            title = styled.title,
            isPlayer = false,
            attributes = styled.attributes,
            powerScore = styled.attributes.combatRating + powerBoost / 4,
            source = EncounterSource.WIFI,
            deviceFingerprint = fingerprint
        )
    }

    fun fromBluetooth(info: BluetoothDeviceInfo, player: PlayerCharacter? = null): EncounterProfile {
        val fingerprint = info.address.lowercase()
        val hash = hashBytes(fingerprint)
        val styled = stylizeEncounter(hash, buildAttributes(hash.rotate()), player)
        val signalInfluence = max(0, info.rssi + 100)

        return EncounterProfile(
            id = fingerprint,
            displayName = styled.displayName,
            title = styled.title,
            isPlayer = false,
            attributes = styled.attributes,
            powerScore = styled.attributes.combatRating + signalInfluence / 5,
            source = EncounterSource.BLUETOOTH,
            deviceFingerprint = fingerprint
        )
    }

    fun merge(primary: EncounterProfile?, secondary: EncounterProfile): EncounterProfile {
        return if (primary == null) {
            secondary
        } else {
            primary.copy(
                displayName = secondary.displayName,
                title = secondary.title,
                attributes = primary.attributes.mergeWith(secondary.attributes),
                powerScore = max(primary.powerScore, secondary.powerScore),
                lastSeenEpoch = System.currentTimeMillis(),
                source = secondary.source
            )
        }
    }

    private fun stylizeEncounter(
        hash: ByteArray,
        baseAttributes: CharacterAttributes,
        player: PlayerCharacter?
    ): StyledEncounter {
        val archetype = determineArchetype(hash, baseAttributes, player)
        val tunedAttributes = tuneAttributesFor(archetype, baseAttributes, player, hash)
        val title = selectTitle(archetype, hash)
        val displayName = buildDisplayName(archetype, hash, title)
        return StyledEncounter(
            displayName = displayName,
            title = title,
            attributes = tunedAttributes
        )
    }

    private fun determineArchetype(
        hash: ByteArray,
        attributes: CharacterAttributes,
        player: PlayerCharacter?
    ): Archetype {
        val rating = attributes.combatRating
        val dragonRoll = hash[6].toPositiveInt() % 100
        val dragonEligible = player != null && dragonRoll < DRAGON_CHANCE_PERCENT
        return when {
            dragonEligible -> Archetype.DRAGON
            rating >= PALADIN_THRESHOLD -> Archetype.PALADIN
            rating >= KNIGHT_THRESHOLD -> Archetype.KNIGHT
            else -> Archetype.OUTLAW
        }
    }

    private fun tuneAttributesFor(
        archetype: Archetype,
        attributes: CharacterAttributes,
        player: PlayerCharacter?,
        hash: ByteArray
    ): CharacterAttributes = when (archetype) {
        Archetype.OUTLAW -> attributes.copy(
            power = attributes.power.coerceAtMost(95),
            agility = attributes.agility.coerceAtMost(90),
            endurance = attributes.endurance.coerceAtMost(90),
            focus = attributes.focus.coerceAtMost(85)
        )

        Archetype.KNIGHT -> elevateAttributes(attributes, KNIGHT_THRESHOLD)
        Archetype.PALADIN -> elevateAttributes(attributes, PALADIN_THRESHOLD)
        Archetype.DRAGON -> forgeDragonAttributes(attributes, player, hash)
    }

    private fun elevateAttributes(attributes: CharacterAttributes, desiredRating: Int): CharacterAttributes {
        val current = attributes.combatRating
        if (current >= desiredRating) return attributes
        val scale = desiredRating.toDouble() / current.toDouble()
        return CharacterAttributes(
            power = (attributes.power * scale).roundToInt(),
            agility = (attributes.agility * scale).roundToInt(),
            endurance = (attributes.endurance * scale).roundToInt(),
            focus = (attributes.focus * scale).roundToInt()
        )
    }

    private fun forgeDragonAttributes(
        attributes: CharacterAttributes,
        player: PlayerCharacter?,
        hash: ByteArray
    ): CharacterAttributes {
        val playerAttrs = player?.attributes
        fun boost(base: Int, playerValue: Int?, extra: Int): Int =
            max(base + extra, (playerValue ?: 0) + extra)

        val powerBonus = 60 + (hash[7].toPositiveInt() % 30)
        val agilityBonus = 40 + (hash[8].toPositiveInt() % 25)
        val enduranceBonus = 50 + (hash[9].toPositiveInt() % 25)
        val focusBonus = 30 + (hash[10].toPositiveInt() % 20)

        return CharacterAttributes(
            power = boost(attributes.power, playerAttrs?.power, powerBonus),
            agility = boost(attributes.agility, playerAttrs?.agility, agilityBonus),
            endurance = boost(attributes.endurance, playerAttrs?.endurance, enduranceBonus),
            focus = boost(attributes.focus, playerAttrs?.focus, focusBonus)
        )
    }

    private fun selectTitle(archetype: Archetype, hash: ByteArray): String {
        val pool = when (archetype) {
            Archetype.OUTLAW -> outlawTitles
            Archetype.KNIGHT -> knightTitles
            Archetype.PALADIN -> paladinTitles
            Archetype.DRAGON -> dragonTitles
        }
        val index = hash[11].toPositiveInt() % pool.size
        return pool[index]
    }

    private fun buildDisplayName(archetype: Archetype, hash: ByteArray, title: String): String {
        val pool = if (archetype == Archetype.DRAGON) dragonNames else givenNames
        val index = hash[12].toPositiveInt() % pool.size
        val name = pool[index]
        return "$name the $title"
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

    private data class StyledEncounter(
        val displayName: String,
        val title: String,
        val attributes: CharacterAttributes
    )

    private enum class Archetype {
        OUTLAW,
        KNIGHT,
        PALADIN,
        DRAGON
    }

    companion object {
        private const val KNIGHT_THRESHOLD = 100
        private const val PALADIN_THRESHOLD = 200
        private const val DRAGON_CHANCE_PERCENT = 4
    }
}
