package com.battlecell.app.domain.model.mission

data class MissionDefinition(
    val id: String,
    val title: String,
    val description: String,
    val conditionSummary: String,
    val rewardDescription: String? = null,
    val progressTarget: Int = 1,
    val preconditions: List<MissionPrecondition> = emptyList()
)
