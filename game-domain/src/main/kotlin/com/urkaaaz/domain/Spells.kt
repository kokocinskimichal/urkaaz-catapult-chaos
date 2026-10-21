package com.urkaaaz.domain

/** Gameplay category used by progression and rule selection. */
enum class SpellCategory {
    OFFENSIVE,
    DEFENSIVE,
    BATTLEFIELD_CONTROL,
    SUPPORT,
    SIEGE,
}

/** Stable serialized spell identifiers. Balance belongs to [SpellCatalog]. */
enum class SpellType {
    ICE_TRAP,
    RUNIC_BASTION,
    HASTE,
    HEAL,
    FORTRESS_SHIELD,
    CATAPULT_LOCK,
    SLOWING_FIELD,
    VAMPIRIC_BANNER,
    STONE_SKIN,
    SIEGE_FRENZY,
    BERSERKER,
}
