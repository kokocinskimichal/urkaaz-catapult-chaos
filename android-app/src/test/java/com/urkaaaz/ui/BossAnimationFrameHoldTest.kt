package com.urkaaaz.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BossAnimationFrameHoldTest {
    @Test
    fun pendingFramesKeepTheLastReadyFrameWhenAnimationTypeChanges() {
        val hold = BossAnimationFrameHold<String>()

        assertNull(
            hold.resolve(
                bossEntityId = "oak-1",
                presentationKey = "ancient_war_oak",
                currentFrame = null,
            ),
        )
        assertEquals(
            "idle-0",
            hold.resolve(
                bossEntityId = "oak-1",
                presentationKey = "ancient_war_oak",
                currentFrame = "idle-0",
            ),
        )
        assertEquals(
            "idle-0",
            hold.resolve(
                bossEntityId = "oak-1",
                presentationKey = "ancient_war_oak",
                currentFrame = null,
            ),
        )
        assertEquals(
            "attack-0",
            hold.resolve(
                bossEntityId = "oak-1",
                presentationKey = "ancient_war_oak",
                currentFrame = "attack-0",
            ),
        )
        assertEquals(
            "attack-0",
            hold.resolve(
                bossEntityId = "oak-1",
                presentationKey = "ancient_war_oak",
                currentFrame = null,
            ),
        )
    }

    @Test
    fun heldFramesDoNotLeakAcrossBossesOrPresentations() {
        val hold = BossAnimationFrameHold<String>()
        hold.resolve(
            bossEntityId = "oak-1",
            presentationKey = "ancient_war_oak",
            currentFrame = "idle-0",
        )

        assertEquals(
            "idle-0",
            hold.resolve(
                bossEntityId = "oak-1",
                presentationKey = "ancient_war_oak",
                currentFrame = null,
            ),
        )
        assertNull(
            hold.resolve(
                bossEntityId = "oak-2",
                presentationKey = "ancient_war_oak",
                currentFrame = null,
            ),
        )
        assertNull(
            hold.resolve(
                bossEntityId = "oak-1",
                presentationKey = "other_boss",
                currentFrame = null,
            ),
        )
    }

    @Test
    fun clearDropsTheRetainedFrame() {
        val hold = BossAnimationFrameHold<String>()
        hold.resolve(
            bossEntityId = "oak-1",
            presentationKey = "ancient_war_oak",
            currentFrame = "idle-0",
        )

        hold.clear()

        assertNull(
            hold.resolve(
                bossEntityId = "oak-1",
                presentationKey = "ancient_war_oak",
                currentFrame = null,
            ),
        )
    }
}
