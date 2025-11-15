package com.battlecell.app.domain.model.mission

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MissionState(
    @SerialName("status") val status: MissionStatus = MissionStatus.DEACTIVATED,
    @SerialName("progress") val progress: Int = 0,
    @SerialName("target") val target: Int = 1,
    @SerialName("updated_at") val updatedAtEpoch: Long = 0L,
    @SerialName("notes") val notes: String? = null
) {
    val isComplete: Boolean
        get() = status == MissionStatus.DONE

    val progressFraction: Float
        get() = if (target <= 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)

    fun increment(by: Int = 1): MissionState {
        if (by <= 0) return this
        val newProgress = (progress + by).coerceAtMost(target)
        return copy(progress = newProgress, updatedAtEpoch = System.currentTimeMillis())
    }

    fun withStatus(newStatus: MissionStatus, notes: String? = this.notes): MissionState =
        copy(status = newStatus, updatedAtEpoch = System.currentTimeMillis(), notes = notes)

    fun withTarget(newTarget: Int): MissionState =
        copy(target = newTarget.coerceAtLeast(1))
}
