package com.battlecell.app.feature.player

import com.battlecell.app.domain.model.PlayerCharacter
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerCharacterTest {

    @Test
    fun gainStatusPointsRaisesLevelAndAwardsSkillPoints() {
        val character = PlayerCharacter(level = 1, statusPoints = 0, skillPoints = 0)

        val updated = character.gainStatusPoints(12)

        assertEquals(3, updated.level)
        assertEquals(12, updated.statusPoints)
        assertEquals(4, updated.skillPoints)
    }

    @Test
    fun gainStatusPointsDoesNotDecreaseLevelWhenRewardIsZero() {
        val character = PlayerCharacter(level = 2, statusPoints = 6, skillPoints = 6)

        val updated = character.gainStatusPoints(0)

        assertEquals(character.level, updated.level)
        assertEquals(character.statusPoints, updated.statusPoints)
        assertEquals(character.skillPoints, updated.skillPoints)
    }
}
