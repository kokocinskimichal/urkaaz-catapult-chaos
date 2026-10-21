package com.urkaaaz.contracts

sealed interface CommandResult {
    data class Accepted(
        val commandId: CommandId,
        val events: List<MatchEvent> = emptyList(),
    ) : CommandResult

    data class Rejected(
        val commandId: CommandId,
        val error: MatchError,
    ) : CommandResult
}

enum class MatchError {
    MATCH_NOT_RUNNING,
    NOT_PLAYERS_TURN,
    INSUFFICIENT_RESOURCE,
    INVALID_AIM,
    NO_AMMUNITION,
    UNKNOWN_ENTITY,
    UNKNOWN_SPELL,
    SPELL_NOT_AVAILABLE,
    SPELL_NOT_IN_LOADOUT,
    SPELL_NOT_IMPLEMENTED,
    SPELL_ALREADY_ACTIVE,
    INVALID_SPELL_TARGET,
    ALREADY_FINISHED,
    COMMAND_NOT_ALLOWED,
}
