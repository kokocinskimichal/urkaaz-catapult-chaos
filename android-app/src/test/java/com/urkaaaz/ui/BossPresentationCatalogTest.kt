package com.urkaaaz.ui

import com.urkaaaz.contracts.BossAttackAnimationTiming
import com.urkaaaz.contracts.BossPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            assertNull(presentation.campaignMarkerIdleAnimation)
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
    fun boulderbloomUsesItsStaticSpriteAndLoopingWalkFrames() {
        val presentation = BossPresentationCatalog.definition("boulderbloom")
        val walk = requireNotNull(presentation.walkAnimation)

        assertEquals("anim_boss_boulderbloom_idle_000", presentation.staticSprite)
        assertEquals(1.29f, presentation.visualScale, 0f)
        assertEquals(32, walk.frameCount)
        assertEquals(4_000L, walk.durationMilliseconds)
        assertEquals(480, walk.frameWidth)
        assertEquals(576, walk.frameHeight)
        assertEquals(true, walk.loops)
        assertEquals(
            "anim_boss_boulderbloom_walk_000",
            BossPresentationCatalog.walkFrameSpriteName("boulderbloom", 0),
        )
        assertEquals(
            "anim_boss_boulderbloom_walk_031",
            BossPresentationCatalog.walkFrameSpriteName("boulderbloom", 31),
        )
    }

    @Test
    fun campaignMarkerIdlesDoNotChangeBattlefieldAnimationStates() {
        val boulderbloom = BossPresentationCatalog.definition("boulderbloom")
        val boulderbloomIdle = requireNotNull(boulderbloom.campaignMarkerIdleAnimation)
        val oak = BossPresentationCatalog.definition("ancient_war_oak")
        val oakCampaignIdle = requireNotNull(oak.campaignMarkerIdleAnimation)

        assertNull(boulderbloom.idleAnimation)
        assertEquals("campaign_idle", boulderbloomIdle.sequenceType)
        assertEquals(32, boulderbloomIdle.frameCount)
        assertEquals(4_000L, boulderbloomIdle.durationMilliseconds)
        assertEquals(480, boulderbloomIdle.frameWidth)
        assertEquals(480, boulderbloomIdle.frameHeight)
        assertEquals(0, boulderbloomIdle.frameIndex(0L))
        assertEquals(31, boulderbloomIdle.frameIndex(3_999L))
        assertEquals(0, boulderbloomIdle.frameIndex(4_000L))
        assertEquals(
            "anim_boss_boulderbloom_campaign_idle_000",
            BossPresentationCatalog.campaignMarkerIdleFrameSpriteName("boulderbloom", 0),
        )
        assertEquals(
            "anim_boss_boulderbloom_campaign_idle_031",
            BossPresentationCatalog.campaignMarkerIdleFrameSpriteName("boulderbloom", 31),
        )

        assertEquals("idle", oakCampaignIdle.sequenceType)
        assertEquals(oak.idleAnimation?.frameCount, oakCampaignIdle.frameCount)
        assertEquals(
            "anim_boss_ancient_war_oak_idle_000",
            BossPresentationCatalog.campaignMarkerIdleFrameSpriteName("ancient_war_oak", 0),
        )
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
    fun ancientWarOakHasBothApprovedDirectionalAttackSequences() {
        val presentation = BossPresentationCatalog.definition("ancient_war_oak")
        val attack = requireNotNull(presentation.attackAnimation(BossAttackDirection.RIGHT))
        val leftAttack = requireNotNull(presentation.attackAnimation(BossAttackDirection.LEFT))

        assertEquals(false, attack.loops)
        assertEquals(false, leftAttack.loops)
        assertEquals(
            BossAttackAnimationTiming.ANCIENT_WAR_OAK_UNIT_ATTACK_FRAME_COUNT,
            attack.frameCount,
        )
        assertEquals(attack.frameCount, leftAttack.frameCount)
        assertEquals(
            BossAttackAnimationTiming.ANCIENT_WAR_OAK_UNIT_ATTACK_DURATION_MILLISECONDS,
            attack.durationMilliseconds,
        )
        assertEquals(attack.durationMilliseconds, leftAttack.durationMilliseconds)
        assertEquals(512, attack.frameWidth)
        assertEquals(384, attack.frameHeight)
        assertEquals(512, leftAttack.frameWidth)
        assertEquals(384, leftAttack.frameHeight)
        assertEquals(
            BossArtBounds(
                left = 168f / 512f,
                top = 93f / 384f,
                right = 347f / 512f,
                bottom = 285f / 384f,
            ),
            leftAttack.frameArtBounds,
        )
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
        assertEquals(
            "anim_boss_ancient_war_oak_attack_left_000",
            BossPresentationCatalog.attackFrameSpriteName(
                "ancient_war_oak",
                BossAttackDirection.LEFT,
                0,
            ),
        )
        assertEquals(
            "anim_boss_ancient_war_oak_attack_left_059",
            BossPresentationCatalog.attackFrameSpriteName(
                "ancient_war_oak",
                BossAttackDirection.LEFT,
                59,
            ),
        )
    }

    @Test
    fun ancientWarOakDeathAnimationIsNonLoopingAndKeepsTheCompactCanvas() {
        val death = requireNotNull(
            BossPresentationCatalog.definition("ancient_war_oak").deathAnimation(),
        )

        assertEquals(60, death.frameCount)
        assertEquals(4_000L, death.durationMilliseconds)
        assertEquals(false, death.loops)
        assertEquals(512, death.frameWidth)
        assertEquals(384, death.frameHeight)
        assertEquals(
            BossArtBounds(
                left = 168f / 512f,
                top = 93f / 384f,
                right = 347f / 512f,
                bottom = 285f / 384f,
            ),
            death.frameArtBounds,
        )
        assertEquals(
            "anim_boss_ancient_war_oak_death_000",
            BossPresentationCatalog.deathFrameSpriteName("ancient_war_oak", 0),
        )
        assertEquals(
            "anim_boss_ancient_war_oak_death_059",
            BossPresentationCatalog.deathFrameSpriteName("ancient_war_oak", 59),
        )
    }

    @Test
    fun idleFrameNameRejectsIndicesOutsideTheRegisteredSequence() {
        assertThrows(IllegalArgumentException::class.java) {
            BossPresentationCatalog.idleFrameSpriteName("ancient_war_oak", 75)
        }
    }

    @Test
    fun campaignMarkerIdleFrameNameRejectsIndicesOutsideTheRegisteredSequence() {
        assertThrows(IllegalArgumentException::class.java) {
            BossPresentationCatalog.campaignMarkerIdleFrameSpriteName("boulderbloom", 32)
        }
    }

    @Test
    fun unknownPresentationKeyIsAnExplicitError() {
        assertThrows(IllegalStateException::class.java) {
            BossPresentationCatalog.definition("missing_boss")
        }
    }
}
