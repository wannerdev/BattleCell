package com.battlecell.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TrainingGameType {
    @SerialName("bug_hunt")
    BUG_HUNT,

    @SerialName("flappy_flight")
    FLAPPY_FLIGHT,

    @SerialName("doodle_jump")
    DOODLE_JUMP,

    @SerialName("subway_run")
    SUBWAY_RUN,

    @SerialName("tetris_siege")
    TETRIS_SIEGE,

    @SerialName("rune_match")
    RUNE_MATCH
}
