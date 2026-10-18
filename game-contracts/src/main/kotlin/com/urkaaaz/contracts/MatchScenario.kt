package com.urkaaaz.contracts

data class MatchScenario(
    val scenarioId: String,
    val battlefield: BattlefieldConfig,
    val players: List<PlayerConfig>,
    val initialResources: ResourceSnapshot = ResourceSnapshot(),
    val availableUnits: List<String> = emptyList(),
    val availableAmmunition: List<String> = emptyList(),
    val availableSpells: List<String> = emptyList(),
    val playerLoadout: PlayerLoadoutConfig? = null,
    val initialUnits: List<UnitConfig> = emptyList(),
    val wind: WindSnapshot = WindSnapshot(),
    val victoryRules: VictoryRulesConfig = VictoryRulesConfig(),
    val mapDefinition: MapDefinition? = null,
) {
    init {
        require(scenarioId.isNotBlank()) { "scenarioId must not be blank" }
        require(players.isNotEmpty()) { "scenario must contain at least one player" }
        require(availableUnits.all(String::isNotBlank)) {
            "available unit identifiers must not be blank"
        }
        require(availableUnits.distinct().size == availableUnits.size) {
            "available unit identifiers must be unique"
        }
        playerLoadout?.let { loadout ->
            require(players.any { it.playerId == loadout.playerId && it.team == Team.BLUE }) {
                "loadout player must be a blue player in the scenario"
            }
            require(availableUnits.containsAll(loadout.unitTypes)) {
                "loadout contains units unavailable in the scenario"
            }
            require(availableAmmunition.containsAll(loadout.ammunitionSlots.filterNotNull())) {
                "loadout contains ammunition unavailable in the scenario"
            }
        }
    }
}

/** Immutable selection for one player, separate from scenario availability. */
data class PlayerLoadoutConfig(
    val playerId: PlayerId,
    val unitTypes: List<String>,
    val ammunitionSlots: List<String?>,
    val spellTypes: List<String>,
) {
    init {
        require(unitTypes.isNotEmpty()) { "loadout must contain at least one unit" }
        require(unitTypes.size <= MAX_UNITS) { "loadout may contain at most $MAX_UNITS units" }
        require(unitTypes.all(String::isNotBlank)) { "unit identifiers must not be blank" }
        require(unitTypes.distinct().size == unitTypes.size) {
            "loadout unit identifiers must be unique"
        }
        require(ammunitionSlots.size == AMMUNITION_SLOT_COUNT) {
            "loadout must contain exactly $AMMUNITION_SLOT_COUNT ammunition slots"
        }
        require(ammunitionSlots.first() == ROCK) { "ROCK must occupy the first ammunition slot" }
        val selectedAmmunition = ammunitionSlots.filterNotNull()
        require(selectedAmmunition.all(String::isNotBlank)) {
            "ammunition identifiers must not be blank"
        }
        require(selectedAmmunition.distinct().size == selectedAmmunition.size) {
            "loadout ammunition identifiers must be unique"
        }
        require(spellTypes.isNotEmpty()) { "loadout must contain at least one spell" }
        require(spellTypes.size <= MAX_SPELLS) {
            "loadout may contain at most $MAX_SPELLS spells"
        }
        require(spellTypes.all(String::isNotBlank)) { "spell identifiers must not be blank" }
        require(spellTypes.distinct().size == spellTypes.size) {
            "loadout spell identifiers must be unique"
        }
    }

    companion object {
        const val MAX_UNITS = 3
        const val AMMUNITION_SLOT_COUNT = 3
        const val MAX_SPELLS = 3
        const val ROCK = "ROCK"
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
