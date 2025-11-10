package com.battlecell.app.domain.usecase

import com.battlecell.app.domain.model.AttributeType
import com.battlecell.app.domain.model.Difficulty
import com.battlecell.app.domain.model.TrainingGameBehavior
import com.battlecell.app.domain.model.TrainingGameDefinition

class GetTrainingGamesUseCase {

    operator fun invoke(): List<TrainingGameDefinition> = listOf(
        TrainingGameDefinition(
            id = "bug-hunt-basic",
            title = "Bug Hunt: Sprint",
            description = "Catch the rogue nanobot before it reaches the reactor core.",
            attributeReward = AttributeType.POWER,
            difficulty = Difficulty.EASY,
            baseReward = 50,
            icon = "bug",
            behavior = TrainingGameBehavior(
                totalDurationMillis = 6000,
                flickerEnabled = false,
                bugRadiusDp = 32f
            )
        ),
        TrainingGameDefinition(
            id = "bug-hunt-stealth",
            title = "Bug Hunt: Stealth",
            description = "The bot flickers in and out. Track its pattern and strike at the right moment.",
            attributeReward = AttributeType.AGILITY,
            difficulty = Difficulty.HARD,
            baseReward = 90,
            icon = "ghost",
            behavior = TrainingGameBehavior(
                totalDurationMillis = 4500,
                flickerEnabled = true,
                visibleWindowMillis = 200L,
                invisibleWindowMillis = 500L,
                bugRadiusDp = 28f
            )
        )
    )
}
