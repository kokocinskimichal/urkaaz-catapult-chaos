package com.urkaaaz.application.loadout

import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.SpellCatalog
import com.urkaaaz.domain.UnitConfigurations

class LoadoutUseCases(
    private val progression: LoadoutProgressionPort,
    private val repository: LoadoutRepository,
) {
    suspend fun open(availability: LoadoutAvailability): LoadoutOpenResult {
        val snapshot = when (val read = progression.snapshot(availability.level)) {
            is LoadoutProgressionRead.Available -> read.snapshot
            is LoadoutProgressionRead.Blocked ->
                return LoadoutOpenResult.ProgressionBlocked(read.reason)
        }
        val stored = when (val read = repository.read(availability.level)) {
            is LoadoutStorageResult.Success -> read.selection
            is LoadoutStorageResult.Failure -> return LoadoutOpenResult.StorageFailure(read.reason)
        }
        val (draft, corrections) =
            if (stored == null) {
                MatchLoadout.defaults(availability, snapshot) to listOf(
                    LoadoutCorrection(LoadoutCategory.UNIT, null, LoadoutCorrectionReason.DEFAULT_USED),
                    LoadoutCorrection(LoadoutCategory.AMMUNITION, null, LoadoutCorrectionReason.DEFAULT_USED),
                    LoadoutCorrection(LoadoutCategory.SPELL, null, LoadoutCorrectionReason.DEFAULT_USED),
                )
            } else {
                normalize(stored, availability, snapshot)
            }
        return LoadoutOpenResult.Opened(draft, snapshot, corrections)
    }

    fun edit(
        draft: MatchLoadout,
        action: LoadoutEditAction,
        availability: LoadoutAvailability,
        snapshot: LoadoutProgressionSnapshot,
    ): LoadoutEditResult {
        val updated = when (action) {
            is LoadoutEditAction.SelectUnit -> {
                if (action.selected) {
                    if (!UnitConfigurations.all.any { it.id == action.unitType }) {
                        return rejected(draft, LoadoutError.UNKNOWN_UNIT)
                    }
                    if (action.unitType !in availability.unitTypes) {
                        return rejected(draft, LoadoutError.UNIT_UNAVAILABLE)
                    }
                    if (action.unitType in draft.unitTypes) {
                        return rejected(draft, LoadoutError.DUPLICATE_SELECTION)
                    }
                    if (draft.unitTypes.size == MatchLoadout.MAX_UNITS) {
                        return rejected(draft, LoadoutError.SELECTION_LIMIT_REACHED)
                    }
                    draft.copy(unitTypes = draft.unitTypes + action.unitType)
                } else {
                    if (action.unitType !in draft.unitTypes) return LoadoutEditResult.Accepted(draft)
                    if (draft.unitTypes.size == 1) {
                        return rejected(draft, LoadoutError.EMPTY_UNIT_SELECTION)
                    }
                    draft.copy(unitTypes = draft.unitTypes - action.unitType)
                }
            }
            is LoadoutEditAction.SetAmmunition -> {
                if (action.slot !in 1 until MatchLoadout.AMMUNITION_SLOT_COUNT) {
                    return rejected(draft, LoadoutError.INVALID_SLOT)
                }
                val type = action.ammunitionType
                if (type != null) {
                    val ammunition = AmmunitionType.values().firstOrNull { it.name == type }
                        ?: return rejected(draft, LoadoutError.UNKNOWN_AMMUNITION)
                    if (type == MatchLoadout.ROCK || type !in availability.ammunitionTypes) {
                        return rejected(draft, LoadoutError.AMMUNITION_UNAVAILABLE)
                    }
                    if (draft.ammunitionSlots.withIndex().any {
                            it.index != action.slot && it.value == type
                        }
                    ) {
                        return rejected(draft, LoadoutError.DUPLICATE_SELECTION)
                    }
                    if (!ammunition.unlimited && snapshot.ammunitionStock(type) <= 0) {
                        return rejected(draft, LoadoutError.AMMUNITION_OUT_OF_STOCK)
                    }
                }
                draft.copy(
                    ammunitionSlots = draft.ammunitionSlots.toMutableList().also {
                        it[action.slot] = type
                    },
                )
            }
            is LoadoutEditAction.ToggleAmmunition -> {
                if (action.ammunitionType == MatchLoadout.ROCK) {
                    return rejected(draft, LoadoutError.ROCK_SLOT_FIXED)
                }
                if (action.selected) {
                    val ammunition = AmmunitionType.values()
                        .firstOrNull { it.name == action.ammunitionType }
                        ?: return rejected(draft, LoadoutError.UNKNOWN_AMMUNITION)
                    if (action.ammunitionType !in availability.ammunitionTypes) {
                        return rejected(draft, LoadoutError.AMMUNITION_UNAVAILABLE)
                    }
                    if (action.ammunitionType in draft.ammunitionSlots) {
                        return rejected(draft, LoadoutError.DUPLICATE_SELECTION)
                    }
                    if (!ammunition.unlimited &&
                        snapshot.ammunitionStock(action.ammunitionType) <= 0
                    ) {
                        return rejected(draft, LoadoutError.AMMUNITION_OUT_OF_STOCK)
                    }
                    val slot = (1 until MatchLoadout.AMMUNITION_SLOT_COUNT)
                        .firstOrNull { draft.ammunitionSlots[it] == null }
                        ?: return rejected(draft, LoadoutError.SELECTION_LIMIT_REACHED)
                    draft.copy(
                        ammunitionSlots = draft.ammunitionSlots.toMutableList().also {
                            it[slot] = action.ammunitionType
                        },
                    )
                } else {
                    val slot = draft.ammunitionSlots.indexOf(action.ammunitionType)
                    if (slot <= 0) return LoadoutEditResult.Accepted(draft)
                    draft.copy(
                        ammunitionSlots = draft.ammunitionSlots.toMutableList().also {
                            it[slot] = null
                        },
                    )
                }
            }
            is LoadoutEditAction.ToggleSpell -> {
                if (action.selected) {
                    val spell = SpellCatalog.find(action.spellType)
                        ?: return rejected(draft, LoadoutError.UNKNOWN_SPELL)
                    if (spell.special) {
                        if (snapshot.specialSpellStock(action.spellType) <= 0) {
                            return rejected(draft, LoadoutError.SPECIAL_SPELL_REQUIRES_SHOP)
                        }
                    } else {
                        if (action.spellType !in availability.spellTypes) {
                            return rejected(draft, LoadoutError.SPELL_UNAVAILABLE)
                        }
                        if (action.spellType !in snapshot.unlockedSpellTypes) {
                            return rejected(draft, LoadoutError.SPELL_LOCKED)
                        }
                    }
                    if (action.spellType in draft.spellTypes) {
                        return rejected(draft, LoadoutError.DUPLICATE_SELECTION)
                    }
                    if (draft.spellTypes.size == MatchLoadout.MAX_SPELLS) {
                        return rejected(draft, LoadoutError.SELECTION_LIMIT_REACHED)
                    }
                    draft.copy(spellTypes = draft.spellTypes + action.spellType)
                } else {
                    if (action.spellType !in draft.spellTypes) {
                        return LoadoutEditResult.Accepted(draft)
                    }
                    draft.copy(spellTypes = draft.spellTypes - action.spellType)
                }
            }
        }
        return LoadoutEditResult.Accepted(updated)
    }

    suspend fun purchaseSpell(
        spellType: String,
        availability: LoadoutAvailability,
    ): LoadoutPurchaseOutcome {
        val spell = SpellCatalog.find(spellType)
            ?: return LoadoutPurchaseOutcome.Rejected(LoadoutError.UNKNOWN_SPELL)
        if (spell.special) {
            return LoadoutPurchaseOutcome.Rejected(LoadoutError.SPECIAL_SPELL_REQUIRES_SHOP)
        }
        if (spellType !in availability.spellTypes) {
            return LoadoutPurchaseOutcome.Rejected(LoadoutError.SPELL_UNAVAILABLE)
        }
        val snapshot = when (val read = progression.snapshot(availability.level)) {
            is LoadoutProgressionRead.Available -> read.snapshot
            is LoadoutProgressionRead.Blocked ->
                return LoadoutPurchaseOutcome.ProgressionBlocked
        }
        if (spellType in snapshot.unlockedSpellTypes) {
            return LoadoutPurchaseOutcome.Rejected(LoadoutError.SPELL_ALREADY_UNLOCKED)
        }
        return when (progression.purchaseSpell(spellType)) {
            LoadoutPurchaseResult.Purchased -> LoadoutPurchaseOutcome.Purchased
            LoadoutPurchaseResult.InsufficientGold -> LoadoutPurchaseOutcome.InsufficientGold
            LoadoutPurchaseResult.Blocked -> LoadoutPurchaseOutcome.ProgressionBlocked
        }
    }

    fun validateDraft(
        draft: MatchLoadout,
        availability: LoadoutAvailability,
        snapshot: LoadoutProgressionSnapshot,
    ): LoadoutError? = validate(draft, availability, snapshot)

    suspend fun commit(
        draft: MatchLoadout,
        availability: LoadoutAvailability,
        playerId: PlayerId,
    ): LoadoutCommitResult {
        val snapshot = when (val read = progression.snapshot(availability.level)) {
            is LoadoutProgressionRead.Available -> read.snapshot
            is LoadoutProgressionRead.Blocked ->
                return LoadoutCommitResult.ProgressionBlocked(read.reason)
        }
        val error = validate(draft, availability, snapshot)
        if (error != null) return LoadoutCommitResult.Rejected(error)
        when (val result = repository.save(availability.level, draft)) {
            LoadoutStorageWriteResult.Saved -> Unit
            is LoadoutStorageWriteResult.Failure ->
                return LoadoutCommitResult.StorageFailure(result.reason)
        }
        return LoadoutCommitResult.Committed(
            loadout = draft,
            playerLoadout = PlayerLoadoutConfig(
                playerId = playerId,
                unitTypes = draft.unitTypes,
                ammunitionSlots = draft.ammunitionSlots,
                spellTypes = draft.spellTypes,
            ),
        )
    }

    private fun validate(
        loadout: MatchLoadout,
        availability: LoadoutAvailability,
        snapshot: LoadoutProgressionSnapshot,
    ): LoadoutError? {
        if (loadout.unitTypes.isEmpty()) return LoadoutError.EMPTY_UNIT_SELECTION
        if (loadout.spellTypes.isEmpty()) return LoadoutError.EMPTY_SPELL_SELECTION
        for (unitType in loadout.unitTypes) {
            if (UnitConfigurations.all.none { it.id == unitType }) return LoadoutError.UNKNOWN_UNIT
            if (unitType !in availability.unitTypes) return LoadoutError.UNIT_UNAVAILABLE
        }
        for (type in loadout.ammunitionSlots.filterNotNull().drop(1)) {
            val ammunition = AmmunitionType.values().firstOrNull { it.name == type }
                ?: return LoadoutError.UNKNOWN_AMMUNITION
            if (type !in availability.ammunitionTypes) {
                return LoadoutError.AMMUNITION_UNAVAILABLE
            }
            if (!ammunition.unlimited && snapshot.ammunitionStock(type) <= 0) {
                return LoadoutError.AMMUNITION_OUT_OF_STOCK
            }
        }
        for (spellType in loadout.spellTypes) {
            val spell = SpellCatalog.find(spellType)
                ?: return LoadoutError.UNKNOWN_SPELL
            if (spell.special) {
                if (snapshot.specialSpellStock(spellType) <= 0) {
                    return LoadoutError.SPECIAL_SPELL_OUT_OF_STOCK
                }
            } else {
                if (spellType !in availability.spellTypes) return LoadoutError.SPELL_UNAVAILABLE
                if (spellType !in snapshot.unlockedSpellTypes) return LoadoutError.SPELL_LOCKED
            }
        }
        return null
    }

    private fun normalize(
        stored: StoredLoadoutSelection,
        availability: LoadoutAvailability,
        progression: LoadoutProgressionSnapshot,
    ): Pair<MatchLoadout, List<LoadoutCorrection>> {
        val corrections = mutableListOf<LoadoutCorrection>()
        fun correct(
            category: LoadoutCategory,
            itemId: String?,
            reason: LoadoutCorrectionReason,
        ) {
            corrections += LoadoutCorrection(category, itemId, reason)
        }

        val knownUnits = UnitConfigurations.all.mapTo(mutableSetOf()) { it.id }
        val units = mutableListOf<String>()
        for (id in stored.unitTypes) {
            when {
                id !in knownUnits -> correct(LoadoutCategory.UNIT, id, LoadoutCorrectionReason.UNKNOWN)
                id !in availability.unitTypes -> correct(
                    LoadoutCategory.UNIT,
                    id,
                    LoadoutCorrectionReason.UNAVAILABLE,
                )
                id in units -> correct(LoadoutCategory.UNIT, id, LoadoutCorrectionReason.DUPLICATE)
                units.size == MatchLoadout.MAX_UNITS ->
                    correct(LoadoutCategory.UNIT, id, LoadoutCorrectionReason.EXCESS)
                else -> units += id
            }
        }
        if (units.isEmpty()) {
            correct(
                LoadoutCategory.UNIT,
                null,
                LoadoutCorrectionReason.DEFAULT_USED,
            )
            units += MatchLoadout.defaults(availability, progression).unitTypes
        }

        val ammoSlots = mutableListOf<String?>(MatchLoadout.ROCK, null, null)
        if (stored.ammunitionSlots.firstOrNull() != MatchLoadout.ROCK) {
            correct(
                LoadoutCategory.AMMUNITION,
                stored.ammunitionSlots.firstOrNull(),
                LoadoutCorrectionReason.INVALID_SLOT,
            )
        }
        for (index in 1..2) {
            val type = stored.ammunitionSlots.getOrNull(index) ?: continue
            val ammunition = AmmunitionType.values().firstOrNull { it.name == type }
            when {
                ammunition == null -> correct(
                    LoadoutCategory.AMMUNITION,
                    type,
                    LoadoutCorrectionReason.UNKNOWN,
                )
                type !in availability.ammunitionTypes -> correct(
                    LoadoutCategory.AMMUNITION,
                    type,
                    LoadoutCorrectionReason.UNAVAILABLE,
                )
                !ammunition.unlimited && progression.ammunitionStock(type) <= 0 -> correct(
                    LoadoutCategory.AMMUNITION,
                    type,
                    LoadoutCorrectionReason.OUT_OF_STOCK,
                )
                type in ammoSlots -> correct(
                    LoadoutCategory.AMMUNITION,
                    type,
                    LoadoutCorrectionReason.DUPLICATE,
                )
                else -> ammoSlots[index] = type
            }
        }
        stored.ammunitionSlots.drop(3).forEach {
            correct(LoadoutCategory.AMMUNITION, it, LoadoutCorrectionReason.EXCESS)
        }
        if (stored.ammunitionSlots.size != MatchLoadout.AMMUNITION_SLOT_COUNT) {
            correct(
                LoadoutCategory.AMMUNITION,
                null,
                LoadoutCorrectionReason.INVALID_SLOT,
            )
            if (stored.ammunitionSlots.size < MatchLoadout.AMMUNITION_SLOT_COUNT) {
                val defaults = MatchLoadout.defaults(availability, progression).ammunitionSlots
                for (index in 1..2) {
                    val default = defaults[index]
                    if (ammoSlots[index] == null && default != null && default !in ammoSlots) {
                        ammoSlots[index] = default
                    }
                }
                correct(
                    LoadoutCategory.AMMUNITION,
                    null,
                    LoadoutCorrectionReason.DEFAULT_USED,
                )
            }
        }

        val knownSpells = SpellCatalog.all.mapTo(mutableSetOf()) { it.type.name }
        val spells = mutableListOf<String>()
        for (id in stored.spellTypes) {
            when {
                id !in knownSpells -> correct(LoadoutCategory.SPELL, id, LoadoutCorrectionReason.UNKNOWN)
                !id.isAllowedSpell(availability, progression) -> correct(
                    LoadoutCategory.SPELL,
                    id,
                    if (SpellCatalog.find(id)?.special == true) LoadoutCorrectionReason.OUT_OF_STOCK
                    else if (id !in availability.spellTypes) LoadoutCorrectionReason.UNAVAILABLE
                    else LoadoutCorrectionReason.LOCKED,
                )
                id in spells -> correct(
                    LoadoutCategory.SPELL,
                    id,
                    LoadoutCorrectionReason.DUPLICATE,
                )
                spells.size == MatchLoadout.MAX_SPELLS ->
                    correct(LoadoutCategory.SPELL, id, LoadoutCorrectionReason.EXCESS)
                else -> spells += id
            }
        }
        if (spells.isEmpty()) {
            correct(
                LoadoutCategory.SPELL,
                null,
                LoadoutCorrectionReason.DEFAULT_USED,
            )
            spells += MatchLoadout.defaults(availability, progression).spellTypes
        }

        return MatchLoadout(units, ammoSlots, spells) to corrections
    }

    private fun rejected(draft: MatchLoadout, error: LoadoutError) =
        LoadoutEditResult.Rejected(draft, error)
}
