package com.urkaaaz.domain

/**
 * Primary combat category. New movement/attack models can add categories
 * without changing the meaning of existing unit assignments.
 */
enum class AttackType {
    MELEE,
    RANGED,
    CONTACT_EXPLOSIVE,
    SIEGE_MISSION,
}
