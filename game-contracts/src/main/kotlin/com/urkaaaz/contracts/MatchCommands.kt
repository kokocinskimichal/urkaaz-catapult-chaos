package com.urkaaaz.contracts

sealed interface MatchCommand {
    val commandId: CommandId
    val matchId: MatchId
    val playerId: PlayerId

    data class Start(
        override val commandId: CommandId,
        override val matchId: MatchId,
        override val playerId: PlayerId,
    ) : MatchCommand

    data class SelectAmmo(
        override val commandId: CommandId,
        override val matchId: MatchId,
        override val playerId: PlayerId,
        val ammunitionType: String,
    ) : MatchCommand

    data class Aim(
        override val commandId: CommandId,
        override val matchId: MatchId,
        override val playerId: PlayerId,
        val catapultId: EntityId,
        val directionDegrees: Float,
        val power: Float,
    ) : MatchCommand

    data class Fire(
        override val commandId: CommandId,
        override val matchId: MatchId,
        override val playerId: PlayerId,
        val catapultId: EntityId,
    ) : MatchCommand

    data class DeployUnit(
        override val commandId: CommandId,
        override val matchId: MatchId,
        override val playerId: PlayerId,
        val unitType: String,
    ) : MatchCommand

    data class SendWave(
        override val commandId: CommandId,
        override val matchId: MatchId,
        override val playerId: PlayerId,
    ) : MatchCommand

    data class CastSpell(
        override val commandId: CommandId,
        override val matchId: MatchId,
        override val playerId: PlayerId,
        val spellType: String,
        val target: EntityId? = null,
    ) : MatchCommand

    data class Surrender(
        override val commandId: CommandId,
        override val matchId: MatchId,
        override val playerId: PlayerId,
    ) : MatchCommand

    data class AdvanceSimulation(
        override val commandId: CommandId,
        override val matchId: MatchId,
        override val playerId: PlayerId,
        val deltaMilliseconds: Long,
    ) : MatchCommand {
        init {
            require(deltaMilliseconds >= 0) { "deltaMilliseconds must not be negative" }
        }
    }
}
