package com.urkaaaz.ui

internal object SpellIconAvailabilityPresentation {
    fun isVisuallyActive(spellType: String, active: Boolean): Boolean =
        active && spellType != "RUNIC_BASTION"

    fun isHighlighted(spellType: String, active: Boolean, ready: Boolean): Boolean =
        ready || isVisuallyActive(spellType, active)
}
