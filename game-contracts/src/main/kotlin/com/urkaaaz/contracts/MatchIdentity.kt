package com.urkaaaz.contracts

@JvmInline
value class MatchId(val value: String)

@JvmInline
value class PlayerId(val value: String)

@JvmInline
value class EntityId(val value: String)

@JvmInline
value class CommandId(val value: String)

@JvmInline
value class EventId(val value: String)

enum class Team {
    BLUE,
    RED,
}

enum class MatchPhase {
    CREATED,
    PLAYER_TURN,
    PROJECTILE_IN_FLIGHT,
    RESOLVING_IMPACT,
    AI_TURN,
    FINISHED,
}

enum class MatchStatus {
    NOT_STARTED,
    RUNNING,
    FINISHED,
}

enum class MatchOutcome {
    BLUE_WIN,
    RED_WIN,
    DRAW,
    SURRENDER,
}
