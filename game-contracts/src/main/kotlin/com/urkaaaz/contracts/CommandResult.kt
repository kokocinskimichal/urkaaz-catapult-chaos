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
    ALREADY_FINISHED,
    COMMAND_NOT_ALLOWED,
}
