package com.battlecell.app.domain.service

import com.battlecell.app.domain.model.PlayerCharacter
import com.battlecell.app.domain.model.mission.MissionDefinition
import com.battlecell.app.domain.model.mission.MissionEvent
import com.battlecell.app.domain.model.mission.MissionPrecondition
import com.battlecell.app.domain.model.mission.MissionState
import com.battlecell.app.domain.model.mission.MissionStatus
import com.battlecell.app.domain.model.mission.MissionIds

object MissionEngine {

    val definitions: List<MissionDefinition> = listOf(
        MissionDefinition(
            id = MissionIds.SCOUT_ENEMIES,
            title = "Scout for enemies",
            description = "Sound the horn and listen for nearby banners.",
            conditionSummary = "Trigger a scan from the scouting horn.",
            rewardDescription = "Unlocks deeper war council reports."
        ),
        MissionDefinition(
            id = MissionIds.LEGENDARY_TRIAL,
            title = "Legendary trial",
            description = "Prove your discipline by winning any training game on legendary stance.",
            conditionSummary = "Win a training game on Legendary difficulty.",
            rewardDescription = "+3 variant sigils",
            preconditions = listOf(MissionPrecondition.MissionCompleted(MissionIds.SCOUT_ENEMIES))
        ),
        MissionDefinition(
            id = MissionIds.PURGE_BEGGARS,
            title = "Shut down the beggar mob",
            description = "Drive back the beggars stirring unrest near the outer wall.",
            conditionSummary = "Defeat 5 beggar opponents in battle.",
            rewardDescription = "Unlocks dragon scouting rites.",
            progressTarget = 5,
            preconditions = listOf(MissionPrecondition.MissionCompleted(MissionIds.LEGENDARY_TRIAL))
        ),
        MissionDefinition(
            id = MissionIds.FIND_DRAGON,
            title = "Find the dragon",
            description = "Locate the wyrm Mechtdor the Vengeful somewhere in the skies.",
            conditionSummary = "Reveal Mechtdor while sounding the horn.",
            rewardDescription = "Unlocks dragon hunt directives.",
            preconditions = listOf(MissionPrecondition.MissionCompleted(MissionIds.PURGE_BEGGARS))
        ),
        MissionDefinition(
            id = MissionIds.HUNT_PALADINS,
            title = "Hunt the paladins",
            description = "Paladins guard the sapphire potion you need. Vanquish them until one drops it.",
            conditionSummary = "Obtain a sapphire potion from a paladin victory.",
            rewardDescription = "Allows you to confront Mechtdor.",
            preconditions = listOf(MissionPrecondition.MissionCompleted(MissionIds.FIND_DRAGON))
        ),
        MissionDefinition(
            id = MissionIds.SLAY_DRAGON,
            title = "Slay Mechtdor",
            description = "With the sapphire potion secured, bring down the dragon.",
            conditionSummary = "Defeat Mechtdor the Vengeful in battle.",
            rewardDescription = "Legendary renown throughout the realm.",
            preconditions = listOf(MissionPrecondition.MissionCompleted(MissionIds.HUNT_PALADINS))
        )
    )

    private val definitionMap = definitions.associateBy { it.id }

    fun bootstrap(player: PlayerCharacter): PlayerCharacter {
        val states = player.missions
        var changed = false
        val updatedStates = states.toMutableMap()
        definitions.forEach { definition ->
            val current = updatedStates[definition.id]
            if (current == null) {
                updatedStates[definition.id] = MissionState(
                    status = MissionStatus.DEACTIVATED,
                    target = definition.progressTarget.coerceAtLeast(1)
                )
                changed = true
            } else if (current.target <= 0) {
                updatedStates[definition.id] = current.withTarget(definition.progressTarget.coerceAtLeast(1))
                changed = true
            }
        }
        return if (changed) {
            player.copy(
                missions = updatedStates.toMap(),
                updatedAtEpoch = System.currentTimeMillis()
            )
        } else {
            player
        }
    }

    fun process(player: PlayerCharacter, event: MissionEvent? = null): PlayerCharacter {
        var working = bootstrap(player)
        if (event != null) {
            working = applyEvent(working, event)
        }
        return activateAvailable(working)
    }

    fun entriesFor(player: PlayerCharacter): List<Pair<MissionDefinition, MissionState>> =
        definitions.mapNotNull { definition ->
            val state = player.missions[definition.id]
            state?.let { definition to it }
        }

    private fun activateAvailable(player: PlayerCharacter): PlayerCharacter {
        val states = player.missions
        var changed = false
        val updated = states.toMutableMap()
        definitions.forEach { definition ->
            val current = states[definition.id] ?: return@forEach
            if (current.status == MissionStatus.DEACTIVATED &&
                definition.preconditions.all { it.isSatisfied(player) }
            ) {
                updated[definition.id] = current.withStatus(MissionStatus.ACTIVE)
                changed = true
            }
        }
        return if (changed) {
            player.copy(missions = updated.toMap(), updatedAtEpoch = System.currentTimeMillis())
        } else {
            player
        }
    }

    private fun applyEvent(player: PlayerCharacter, event: MissionEvent): PlayerCharacter {
        var working = player
        fun updateMission(id: String, block: (MissionState) -> MissionState): Boolean {
            val current = working.missions[id] ?: return false
            val updatedState = block(current)
            if (updatedState == current) return false
            working = working.copy(
                missions = working.missions + (id to updatedState),
                updatedAtEpoch = System.currentTimeMillis()
            )
            return true
        }

        when (event) {
            MissionEvent.HornSounded -> {
                updateMission(MissionIds.SCOUT_ENEMIES) { state ->
                    when (state.status) {
                        MissionStatus.ACTIVE -> state.copy(
                            progress = state.target,
                            status = MissionStatus.DONE,
                            updatedAtEpoch = System.currentTimeMillis(),
                            notes = "First horn sounded."
                        )

                        else -> state
                    }
                }
            }

            is MissionEvent.LegendaryTrainingWin -> {
                updateMission(MissionIds.LEGENDARY_TRIAL) { state ->
                    if (state.status == MissionStatus.ACTIVE) {
                        state.copy(
                            progress = state.target,
                            status = MissionStatus.DONE,
                            notes = "Triumphed in ${event.gameId}."
                        )
                    } else state
                }
            }

            is MissionEvent.BattleVictory -> {
                if (event.title.equals("Beggar", ignoreCase = true)) {
                    updateMission(MissionIds.PURGE_BEGGARS) { state ->
                        if (state.status == MissionStatus.ACTIVE) {
                            val progressed = state.increment()
                            if (progressed.progress >= progressed.target) {
                                progressed.copy(status = MissionStatus.DONE)
                            } else progressed
                        } else state
                    }
                }
            }

            MissionEvent.DragonSighted -> {
                val updated = updateMission(MissionIds.FIND_DRAGON) { state ->
                    if (state.status == MissionStatus.ACTIVE) {
                        state.copy(
                            progress = state.target,
                            status = MissionStatus.DONE,
                            notes = "Mechtdor sighted."
                        )
                    } else state
                }
                if (!updated) {
                    // Even if mission already done, ensure notes updated
                    updateMission(MissionIds.FIND_DRAGON) { state ->
                        if (state.notes.isNullOrBlank()) state.copy(notes = "Mechtdor sighted.") else state
                    }
                }
            }

            MissionEvent.SapphirePotionFound -> {
                updateMission(MissionIds.HUNT_PALADINS) { state ->
                    if (state.status == MissionStatus.ACTIVE) {
                        state.copy(
                            progress = state.target,
                            status = MissionStatus.DONE,
                            notes = "Recovered sapphire potion."
                        )
                    } else state
                }
            }

            MissionEvent.DragonSlain -> {
                updateMission(MissionIds.SLAY_DRAGON) { state ->
                    if (state.status == MissionStatus.ACTIVE) {
                        state.copy(
                            progress = state.target,
                            status = MissionStatus.DONE,
                            notes = "Mechtdor defeated."
                        )
                    } else state
                }
            }
        }
        return working
    }

    fun definitionFor(id: String): MissionDefinition? = definitionMap[id]
}
