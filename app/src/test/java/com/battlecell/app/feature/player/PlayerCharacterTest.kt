package com.battlecell.app.feature.player

import com.battlecell.app.domain.model.PlayerCharacter
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerCharacterTest {

    @Test
    fun gainStatusPointsOnlyIncreasesStatusCurrency() {
        val character = PlayerCharacter(level = 5, statusPoints = 10, skillPoints = 6)

        val updated = character.gainStatusPoints(12)

        assertEquals(5, updated.level)
        assertEquals(22, updated.statusPoints)
        assertEquals(6, updated.skillPoints)
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
