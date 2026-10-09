package com.urkaaaz.contracts

data class MatchScenario(
    val scenarioId: String,
    val battlefield: BattlefieldConfig,
    val players: List<PlayerConfig>,
    val initialResources: ResourceSnapshot = ResourceSnapshot(),
    val availableAmmunition: List<String> = emptyList(),
    val availableSpells: List<String> = emptyList(),
    val initialUnits: List<UnitConfig> = emptyList(),
    val wind: WindSnapshot = WindSnapshot(),
    val victoryRules: VictoryRulesConfig = VictoryRulesConfig(),
) {
    init {
        require(scenarioId.isNotBlank()) { "scenarioId must not be blank" }
        require(players.isNotEmpty()) { "scenario must contain at least one player" }
    }
}

data class BattlefieldConfig(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0) { "battlefield width must be positive" }
        require(height > 0) { "battlefield height must be positive" }
    }
}

data class PlayerConfig(
    val playerId: PlayerId,
    val team: Team,
)

data class UnitConfig(
    val entityId: EntityId,
    val unitType: String,
    val team: Team,
)

data class VictoryRulesConfig(
    val defeatAllOpponents: Boolean = true,
)
