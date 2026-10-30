package com.urkaaaz.ui

import com.urkaaaz.contracts.BossAttackAnimationTiming
import com.urkaaaz.contracts.BossPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BossPresentationCatalogTest {
    @Test
    fun bossesWithoutAnimationsKeepStaticArtworkForEveryPhase() {
        val keys = listOf(
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
            assertEquals(null, presentation.idleAnimation)
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
    fun ancientWarOakUsesTheApprovedIdleAndKeepsAStaticFallback() {
        val presentation = BossPresentationCatalog.definition("ancient_war_oak")
        val animation = requireNotNull(presentation.idleAnimation)

        assertEquals("boss_ancient_war_oak_idle_fallback", presentation.staticSprite)
        assertEquals(5f / 3f, presentation.visualScale, 0f)
        assertEquals(75, animation.frameCount)
        assertEquals(5_042L, animation.durationMilliseconds)
        assertEquals(384, animation.frameWidth)
        assertEquals(384, animation.frameHeight)
        assertEquals(
            "anim_boss_ancient_war_oak_idle_000",
            BossPresentationCatalog.idleFrameSpriteName("ancient_war_oak", 0),
        )
        assertEquals(
            "anim_boss_ancient_war_oak_idle_074",
            BossPresentationCatalog.idleFrameSpriteName("ancient_war_oak", 74),
        )
    }

    @Test
    fun ancientWarOakHasOnlyTheApprovedRightwardAttackSequence() {
        val presentation = BossPresentationCatalog.definition("ancient_war_oak")
        val attack = requireNotNull(presentation.attackAnimation(BossAttackDirection.RIGHT))

        assertEquals(null, presentation.attackAnimation(BossAttackDirection.LEFT))
        assertEquals(false, attack.loops)
        assertEquals(
            BossAttackAnimationTiming.ANCIENT_WAR_OAK_UNIT_ATTACK_FRAME_COUNT,
            attack.frameCount,
        )
        assertEquals(
            BossAttackAnimationTiming.ANCIENT_WAR_OAK_UNIT_ATTACK_DURATION_MILLISECONDS,
            attack.durationMilliseconds,
        )
        assertEquals(512, attack.frameWidth)
        assertEquals(384, attack.frameHeight)
        assertEquals(
            BossAttackAnimationTiming.ANCIENT_WAR_OAK_UNIT_ATTACK_IMPACT_FRAME_INDEX,
            attack.frameIndex(BossAttackAnimationTiming.ANCIENT_WAR_OAK_UNIT_ATTACK_WINDUP_MILLISECONDS),
        )
        assertEquals(attack.frameCount - 1, attack.frameIndex(attack.durationMilliseconds + 500L))
        assertEquals(true, attack.isFinished(attack.durationMilliseconds))
        assertEquals(
            "anim_boss_ancient_war_oak_attack_right_000",
            BossPresentationCatalog.attackFrameSpriteName(
                "ancient_war_oak",
                BossAttackDirection.RIGHT,
                0,
            ),
        )
        assertEquals(
            "anim_boss_ancient_war_oak_attack_right_059",
            BossPresentationCatalog.attackFrameSpriteName(
                "ancient_war_oak",
                BossAttackDirection.RIGHT,
                59,
            ),
        )
        assertThrows(IllegalStateException::class.java) {
            BossPresentationCatalog.attackFrameSpriteName(
                "ancient_war_oak",
                BossAttackDirection.LEFT,
                0,
            )
        }
    }

    @Test
    fun idleFrameNameRejectsIndicesOutsideTheRegisteredSequence() {
        assertThrows(IllegalArgumentException::class.java) {
            BossPresentationCatalog.idleFrameSpriteName("ancient_war_oak", 75)
        }
    }

    @Test
    fun unknownPresentationKeyIsAnExplicitError() {
        assertThrows(IllegalStateException::class.java) {
            BossPresentationCatalog.definition("missing_boss")
        }
    }
}
