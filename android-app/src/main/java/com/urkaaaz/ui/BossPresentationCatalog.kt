package com.urkaaaz.ui

import com.urkaaaz.android.R
import com.urkaaaz.contracts.BossAttackAnimationTiming
import com.urkaaaz.contracts.BossPhase

data class BossArtBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left >= 0f && top >= 0f && right <= 1f && bottom <= 1f)
        require(right > left && bottom > top)
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
}

enum class BossAttackDirection { LEFT, RIGHT }

/**
 * One generated boss frame sequence. [referenceArtBounds] describes where the
 * static reference art sits on its own canvas, [frameArtBounds] where the same
 * body sits on frame 0 of this sequence, so every sequence of a boss can be laid
 * out at the same world size regardless of its generated canvas.
 */
data class BossSpriteAnimationDefinition(
    val frameCount: Int,
    val durationMilliseconds: Long,
    val frameWidth: Int,
    val frameHeight: Int,
    val referenceArtBounds: BossArtBounds,
    val frameArtBounds: BossArtBounds,
    val loops: Boolean = true,
    val referenceCanvasAspect: Float = 1f,
) {
    init {
        require(frameCount > 0)
        require(durationMilliseconds > 0L)
        require(frameWidth > 0 && frameHeight > 0)
        require(referenceCanvasAspect > 0f)
    }

    fun frameIndex(elapsedMilliseconds: Long): Int {
        val clamped = elapsedMilliseconds.coerceAtLeast(0L)
        val position = if (loops) {
            clamped % durationMilliseconds
        } else {
            clamped.coerceAtMost(durationMilliseconds - 1L)
        }
        return (position * frameCount / durationMilliseconds).toInt()
    }

    fun isFinished(elapsedMilliseconds: Long): Boolean =
        !loops && elapsedMilliseconds >= durationMilliseconds
}

typealias BossIdleAnimationDefinition = BossSpriteAnimationDefinition

data class BossPresentationDefinition(
    val staticSprite: String,
    val nameResource: Int,
    val spritesByPhase: Map<BossPhase, String> = emptyMap(),
    val idleAnimation: BossSpriteAnimationDefinition? = null,
    val attackAnimations: Map<BossAttackDirection, BossSpriteAnimationDefinition> = emptyMap(),
    val visualScale: Float = 1f,
) {
    init {
        require(visualScale > 0f)
        require(attackAnimations.values.none { it.loops }) {
            "Boss attack animations must not loop"
        }
    }

    fun spriteFor(phase: BossPhase): String =
        spritesByPhase[phase] ?: staticSprite

    fun attackAnimation(direction: BossAttackDirection): BossSpriteAnimationDefinition? =
        attackAnimations[direction]
}

object BossPresentationCatalog {
    private val ANCIENT_WAR_OAK_REFERENCE_ART_BOUNDS = BossArtBounds(
        left = 0f,
        top = 0f,
        right = 1240f / 1254f,
        bottom = 1f,
    )

    private val definitions = mapOf(
        "ancient_war_oak" to BossPresentationDefinition(
            staticSprite = "boss_ancient_war_oak_idle_fallback",
            nameResource = R.string.boss_name_ancient_war_oak,
            visualScale = 5f / 3f,
            idleAnimation = BossSpriteAnimationDefinition(
                frameCount = 75,
                durationMilliseconds = 5_042L,
                frameWidth = 384,
                frameHeight = 384,
                referenceArtBounds = ANCIENT_WAR_OAK_REFERENCE_ART_BOUNDS,
                frameArtBounds = BossArtBounds(
                    left = 80f / 384f,
                    top = 67f / 384f,
                    right = 308f / 384f,
                    bottom = 311f / 384f,
                ),
            ),
            attackAnimations = mapOf(
                // Overhead branch-hand slam towards world-right (RED side). The
                // 4:3 canvas leaves room for the raised hand; frame 0 body bounds
                // were measured with the same alpha threshold as the idle frames.
                BossAttackDirection.RIGHT to BossSpriteAnimationDefinition(
                    frameCount = BossAttackAnimationTiming.ANCIENT_WAR_OAK_UNIT_ATTACK_FRAME_COUNT,
                    durationMilliseconds =
                        BossAttackAnimationTiming.ANCIENT_WAR_OAK_UNIT_ATTACK_DURATION_MILLISECONDS,
                    frameWidth = 512,
                    frameHeight = 384,
                    referenceArtBounds = ANCIENT_WAR_OAK_REFERENCE_ART_BOUNDS,
                    frameArtBounds = BossArtBounds(
                        left = 154f / 512f,
                        top = 95f / 384f,
                        right = 335f / 512f,
                        bottom = 288f / 384f,
                    ),
                    loops = false,
                ),
            ),
        ),
        "burntwood_warden" to BossPresentationDefinition(
            staticSprite = "anim_boss_burntwood_warden_idle_000",
            nameResource = R.string.boss_name_burntwood_warden,
        ),
        "copperwood_final_boss" to BossPresentationDefinition(
            staticSprite = "anim_boss_copperwood_final_boss_idle_000",
            nameResource = R.string.boss_name_copperwood_final_boss,
        ),
        "copperwood_siege_lord" to BossPresentationDefinition(
            staticSprite = "anim_boss_copperwood_siege_lord_idle_000",
            nameResource = R.string.boss_name_copperwood_siege_lord,
        ),
        "frozen_troll" to BossPresentationDefinition(
            staticSprite = "anim_boss_frozen_troll_idle_000",
            nameResource = R.string.boss_name_frozen_troll,
        ),
        "old_yeti_sovereign" to BossPresentationDefinition(
            staticSprite = "anim_boss_old_yeti_sovereign_idle_000",
            nameResource = R.string.boss_name_old_yeti_sovereign,
        ),
        "scorchbound_king" to BossPresentationDefinition(
            staticSprite = "anim_boss_scorchbound_king_idle_000",
            nameResource = R.string.boss_name_scorchbound_king,
        ),
        "thorncrown" to BossPresentationDefinition(
            staticSprite = "anim_boss_thorncrown_idle_000",
            nameResource = R.string.boss_name_thorncrown,
        ),
    )

    fun definition(presentationKey: String): BossPresentationDefinition =
        definitions[presentationKey]
            ?: error("No boss presentation is registered for '$presentationKey'")

    fun idleFrameSpriteName(presentationKey: String, frameIndex: Int): String {
        val animation = definition(presentationKey).idleAnimation
            ?: error("No boss idle animation is registered for '$presentationKey'")
        require(frameIndex in 0 until animation.frameCount) {
            "Boss idle frame index $frameIndex is outside 0 until ${animation.frameCount}"
        }
        return "anim_boss_${presentationKey}_idle_${frameIndex.toString().padStart(3, '0')}"
    }

    fun attackFrameSpriteName(
        presentationKey: String,
        direction: BossAttackDirection,
        frameIndex: Int,
    ): String {
        val animation = definition(presentationKey).attackAnimation(direction)
            ?: error("No boss $direction attack animation is registered for '$presentationKey'")
        require(frameIndex in 0 until animation.frameCount) {
            "Boss attack frame index $frameIndex is outside 0 until ${animation.frameCount}"
        }
        val suffix = direction.name.lowercase()
        return "anim_boss_${presentationKey}_attack_${suffix}_${frameIndex.toString().padStart(3, '0')}"
    }
}
