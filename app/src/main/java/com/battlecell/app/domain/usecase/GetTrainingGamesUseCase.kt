package com.battlecell.app.domain.usecase

import com.battlecell.app.domain.model.AttributeType
import com.battlecell.app.domain.model.Difficulty
import com.battlecell.app.domain.model.TrainingGameBehavior
import com.battlecell.app.domain.model.TrainingGameDefinition
import com.battlecell.app.domain.model.TrainingGameType

class GetTrainingGamesUseCase {

    operator fun invoke(): List<TrainingGameDefinition> = listOf(
        TrainingGameDefinition(
            id = "gauntlet-drill",
            title = "Gauntlet Drill",
            description = "Clench the war-gauntlet around the ember sprite before it spirals into the keep's heartfire.",
            attributeReward = AttributeType.POWER,
            difficulty = Difficulty.EASY,
            baseReward = 50,
            icon = "fist",
            gameType = TrainingGameType.BUG_HUNT,
            behavior = TrainingGameBehavior(
                totalDurationMillis = 8500,
                flickerEnabled = true,
                bugRadiusDp = 34f
            )
        ),
        TrainingGameDefinition(
            id = "falcon-wind-course",
            title = "Falcon Wind Course",
            description = "Guide the mews falcon through arrow slits and pennants without grazing the warded battlements.",
            attributeReward = AttributeType.AGILITY,
            difficulty = Difficulty.EASY,
            baseReward = 70,
            icon = "falcon",
            gameType = TrainingGameType.FLAPPY_FLIGHT,
            behavior = TrainingGameBehavior(
                totalDurationMillis = 52000,
                bugRadiusDp = 28f,
                targetScore = 18
            )
        ),
        TrainingGameDefinition(
            id = "oracle-spire-ascent",
            title = "Oracle's Spire Ascent",
            description = "Leap from rune dais to rune dais and keep your gaze steady as the spire shudders beneath you.",
            attributeReward = AttributeType.FOCUS,
            difficulty = Difficulty.HARD,
            baseReward = 90,
            icon = "eye",
            gameType = TrainingGameType.DOODLE_JUMP,
            behavior = TrainingGameBehavior(
                totalDurationMillis = 0,
                bugRadiusDp = 24f,
                targetScore = 520
            )
        ),
        TrainingGameDefinition(
            id = "sentinel-rampart-run",
            title = "Sentinel Rampart Run",
            description = "Charge along the rampart lanes, shift shields, and outlast the tireless sentry constructs.",
            attributeReward = AttributeType.ENDURANCE,
            difficulty = Difficulty.NORMAL,
            baseReward = 80,
            icon = "shield",
            gameType = TrainingGameType.SUBWAY_RUN,
            behavior = TrainingGameBehavior(
                totalDurationMillis = 64000,
                bugRadiusDp = 30f
            )
        ),
        TrainingGameDefinition(
            id = "tetris-siege",
            title = "Siegebreak Stack",
            description = "Stack wardstones to plug the breach while choosing which sigil tiles drop next.",
            attributeReward = AttributeType.FOCUS,
            difficulty = Difficulty.NORMAL,
            baseReward = 95,
            icon = "tower",
            gameType = TrainingGameType.TETRIS_SIEGE,
            behavior = TrainingGameBehavior(
                totalDurationMillis = 700,
                targetScore = 12,
                boardWidth = 10,
                boardHeight = 18
            )
        ),
        TrainingGameDefinition(
            id = "rune-harvest",
            title = "Rune Harvest",
            description = "Swap autumn runes to collect enough essence before the war council's sandglass runs out.",
            attributeReward = AttributeType.AGILITY,
            difficulty = Difficulty.NORMAL,
            baseReward = 85,
            icon = "runes",
            gameType = TrainingGameType.RUNE_MATCH,
            behavior = TrainingGameBehavior(
                totalDurationMillis = 0,
                targetScore = 800,
                boardWidth = 7,
                boardHeight = 7,
                moveLimit = 24
            )
        )
    )
}
