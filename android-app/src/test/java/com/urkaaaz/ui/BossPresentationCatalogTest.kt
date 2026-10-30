package com.urkaaaz.ui

import com.urkaaaz.contracts.BossPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BossPresentationCatalogTest {
    @Test
    fun staticBossArtworkIsUsedForEveryPhaseUntilAnimationsAreAdded() {
        val keys = listOf(
            "ancient_war_oak",
            "burntwood_warden",
            "copperwood_final_boss",
            "copperwood_siege_lord",
            "frozen_troll",
            "old_yeti_sovereign",
            "scorchbound_king",
            "thorncrown",
        )
        keys.forEach { key ->
            val presentation = BossPresentationCatalog.definition(key)
            assertEquals(
                "anim_boss_${key}_idle_000",
                presentation.spriteFor(BossPhase.MARCHING),
            )
            assertEquals(
                "anim_boss_${key}_idle_000",
                presentation.spriteFor(BossPhase.ENRAGED),
            )
        }
    }

    @Test
    fun unknownPresentationKeyIsAnExplicitError() {
        assertThrows(IllegalStateException::class.java) {
            BossPresentationCatalog.definition("missing_boss")
        }
    }
}
