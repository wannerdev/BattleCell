package com.battlecell.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
data class EncounterProfile(
    @SerialName("id") val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("is_player") val isPlayer: Boolean,
    @SerialName("attributes") val attributes: CharacterAttributes,
    @SerialName("power_score") val powerScore: Int,
    @SerialName("source") val source: EncounterSource,
    @SerialName("device_fingerprint") val deviceFingerprint: String,
    @SerialName("last_seen_epoch") val lastSeenEpoch: Long = System.currentTimeMillis()
) {
    val adjustedPower: Int
        get() = max(powerScore, attributes.combatRating)
}
