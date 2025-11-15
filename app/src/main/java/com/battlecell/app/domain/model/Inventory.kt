package com.battlecell.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Inventory(
    @SerialName("sapphire_potions") val sapphirePotions: Int = 0
) {
    val hasSapphirePotion: Boolean
        get() = sapphirePotions > 0

    fun addSapphirePotions(amount: Int): Inventory {
        if (amount <= 0) return this
        return copy(sapphirePotions = sapphirePotions + amount)
    }

    fun consumeSapphirePotion(): Inventory {
        if (!hasSapphirePotion) return this
        return copy(sapphirePotions = sapphirePotions - 1)
    }
}
