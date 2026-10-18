package com.urkaaaz.domain

/** Gameplay category used by progression and rule selection. */
enum class SpellCategory {
    OFFENSIVE,
    DEFENSIVE,
    BATTLEFIELD_CONTROL,
    SUPPORT,
    SIEGE,
}

/** Domain definition of a spell, independent of UI and persistence. */
enum class SpellType(
    val goldCost: Int,
    val category: SpellCategory,
    val special: Boolean,
) {
    ICE_TRAP(500, SpellCategory.BATTLEFIELD_CONTROL, false),
    RUNIC_BASTION(1_200, SpellCategory.DEFENSIVE, false),
    HASTE(900, SpellCategory.SUPPORT, false),
    HEAL(900, SpellCategory.SUPPORT, false),
    FORTRESS_SHIELD(0, SpellCategory.DEFENSIVE, false),
    CATAPULT_LOCK(1_000, SpellCategory.SIEGE, true),
    SLOWING_FIELD(1_000, SpellCategory.BATTLEFIELD_CONTROL, true),
    VAMPIRIC_BANNER(1_100, SpellCategory.SUPPORT, true),
    STONE_SKIN(1_100, SpellCategory.DEFENSIVE, true),
    SIEGE_FRENZY(1_200, SpellCategory.OFFENSIVE, true),
    BERSERKER(2_000, SpellCategory.OFFENSIVE, true),
}
