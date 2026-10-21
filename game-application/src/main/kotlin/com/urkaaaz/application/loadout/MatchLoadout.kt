package com.urkaaaz.application.loadout

import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.SpellCatalog
import com.urkaaaz.domain.UnitConfigurations

/** One canonical, immutable player selection. Empty unit/spell lists are valid drafts only. */
data class MatchLoadout(
    val unitTypes: List<String>,
    val ammunitionSlots: List<String?>,
    val spellTypes: List<String>,
) {
    init {
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

        fun defaults(availability: LoadoutAvailability, progression: LoadoutProgressionSnapshot) =
            MatchLoadout(
                unitTypes = UnitConfigurations.all.asSequence()
                    .map { it.id }
                    .filter { it in availability.unitTypes }
                    .take(MAX_UNITS)
                    .toList(),
                ammunitionSlots = listOf(ROCK) + AmmunitionType.values()
                    .asSequence()
                    .map { it.name }
                    .filter { it != ROCK && it in availability.ammunitionTypes }
                    .filter { it.isUnlimitedAmmunition() || progression.ammunitionStock(it) > 0 }
                    .take(AMMUNITION_SLOT_COUNT - 1)
                    .toList()
                    .let { it + List(AMMUNITION_SLOT_COUNT - 1 - it.size) { null } },
                spellTypes = SpellType.values()
                    .asSequence()
                    .map { it.name }
                    .filter { it in progression.defaultSpellTypes }
                    .filter { it.isAllowedSpell(availability, progression) }
                    .plus(
                        SpellType.values()
                            .asSequence()
                            .map { it.name }
                            .filter { it !in progression.defaultSpellTypes }
                            .filter { it.isAllowedSpell(availability, progression) },
                    )
                    .take(MAX_SPELLS)
                    .toList(),
            )
    }
}

private fun String.isUnlimitedAmmunition(): Boolean =
    AmmunitionType.valueOf(this).unlimited

internal fun String.isAllowedSpell(
    availability: LoadoutAvailability,
    progression: LoadoutProgressionSnapshot,
): Boolean {
    val spell = SpellCatalog.find(this) ?: return false
    return if (spell.special) {
        progression.specialSpellStock(this) > 0
    } else {
        this in availability.spellTypes && this in progression.unlockedSpellTypes
    }
}
