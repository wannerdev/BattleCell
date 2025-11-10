package com.battlecell.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TrainingGameBehavior(
    val totalDurationMillis: Int,
    val flickerEnabled: Boolean = false,
    val visibleWindowMillis: Long = 300L,
    val invisibleWindowMillis: Long = 600L,
    val bugRadiusDp: Float = 28f
)
