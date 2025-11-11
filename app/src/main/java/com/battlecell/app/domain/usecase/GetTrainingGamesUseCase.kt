package com.battlecell.app.domain.usecase

import com.battlecell.app.domain.model.AttributeType
import com.battlecell.app.domain.model.Difficulty
import com.battlecell.app.domain.model.TrainingGameBehavior
import com.battlecell.app.domain.model.TrainingGameDefinition
import com.battlecell.app.domain.model.TrainingGameType

class GetTrainingGamesUseCase {

    operator fun invoke(): List<TrainingGameDefinition> = listOf(
        TrainingGameDefinition(
            id = "bug-hunt-sprint",
            title = "Bug Hunt: Sprint",
            description = "Catch the rogue nanobot before it reaches the reactor core.",
            attributeReward = AttributeType.POWER,
            difficulty = Difficulty.EASY,
            baseReward = 50,
            icon = "bug",
            gameType = TrainingGameType.BUG_HUNT,
            behavior = TrainingGameBehavior(
                totalDurationMillis = 6000,
                flickerEnabled = false,
                bugRadiusDp = 32f
            )
        ),
        TrainingGameDefinition(
            id = "winged-firewall",
            title = "Winged Firewall",
            description = "Guide the drone through security gaps without touching the electrified pylons.",
            attributeReward = AttributeType.AGILITY,
            difficulty = Difficulty.NORMAL,
            baseReward = 70,
            icon = "flappy",
            gameType = TrainingGameType.FLAPPY_FLIGHT,
            behavior = TrainingGameBehavior(
                totalDurationMillis = 45000,
                flickerEnabled = false,
                bugRadiusDp = 26f
            )
        ),
        TrainingGameDefinition(
            id = "quantum-pillar-ascent",
            title = "Quantum Pillar Ascent",
            description = "Bounce across shifting pillars and climb as high as you can before the abyss swallows you.",
            attributeReward = AttributeType.FOCUS,
            difficulty = Difficulty.HARD,
            baseReward = 90,
            icon = "doodle",
            gameType = TrainingGameType.DOODLE_JUMP,
            behavior = TrainingGameBehavior(
                totalDurationMillis = 0,
                bugRadiusDp = 24f,
                targetScore = 650
            )
        ),
        TrainingGameDefinition(
            id = "neon-rail-run",
            title = "Neon Rail Run",
            description = "Dash along the mag-lev rails, swap lanes, and avoid crashing security drones.",
            attributeReward = AttributeType.ENDURANCE,
            difficulty = Difficulty.NORMAL,
            baseReward = 80,
            icon = "runner",
            gameType = TrainingGameType.SUBWAY_RUN,
            behavior = TrainingGameBehavior(
                totalDurationMillis = 60000,
                bugRadiusDp = 30f
            )
        )
    )
}
