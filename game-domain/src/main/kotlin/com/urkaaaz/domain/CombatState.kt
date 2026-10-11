package com.urkaaaz.domain

import com.urkaaaz.contracts.EntityId

@JvmInline
value class AttackGroupId(val value: String)

enum class UnitActionState {
    GARRISONED,
    MOVING,
    SEEKING_TARGET,
    WINDING_UP,
    ATTACKING,
    RECOVERING,
    RETURNING,
    DEAD,
}

data class UnitCombatState(
    val action: UnitActionState = UnitActionState.GARRISONED,
    val targetId: EntityId? = null,
    val attackGroupId: AttackGroupId? = null,
    val attackCycleId: Long = 0L,
    val attackProgress: Float = 0f,
    val attackCooldownRemainingSeconds: Float = 0f,
)

data class AttackGroup(
    val id: AttackGroupId,
    val team: com.urkaaaz.contracts.Team,
    val targetId: EntityId,
    val memberIds: List<EntityId>,
    val attackType: AttackType,
) {
    init {
        require(memberIds.isNotEmpty()) { "attack group must have members" }
    }
}
