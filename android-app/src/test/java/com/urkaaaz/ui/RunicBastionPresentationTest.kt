package com.urkaaaz.ui

import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.RunicBastionGeometry
import com.urkaaaz.contracts.RunicBastionSnapshot
import com.urkaaaz.contracts.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunicBastionPresentationTest {
    @Test
    fun creationAnimationAdvancesAtItsAuthoredCadenceAndHoldsItsFinalFrame() {
        assertEquals(2_400L, RunicBastionPresentation.CREATION_DURATION_MILLISECONDS)
        assertEquals(0, RunicBastionPresentation.frameIndex(0L))
        assertEquals(0, RunicBastionPresentation.frameIndex(119L))
        assertEquals(1, RunicBastionPresentation.frameIndex(120L))
        assertEquals(19, RunicBastionPresentation.frameIndex(2_400L))
        assertEquals(19, RunicBastionPresentation.frameIndex(Long.MAX_VALUE))
    }

    @Test
    fun destructionAnimationAdvancesAtItsAuthoredCadenceAndHoldsItsFinalFrame() {
        assertEquals(0, RunicBastionPresentation.destructionFrameIndex(0L))
        assertEquals(0, RunicBastionPresentation.destructionFrameIndex(166L))
        assertEquals(1, RunicBastionPresentation.destructionFrameIndex(167L))
        assertEquals(
            RunicBastionPresentation.DESTRUCTION_FRAME_COUNT - 1,
            RunicBastionPresentation.destructionFrameIndex(Long.MAX_VALUE),
        )
    }

    @Test
    fun newBastionTransitionsFromCreationToTheLoopingRunePulse() {
        val clock = RunicBastionPresentationClock()
        val state = state(bastions = listOf(bastion()))

        clock.update(state, 0L)
        val beforeTransition = clock.update(
            state,
            RunicBastionPresentation.CREATION_DURATION_MILLISECONDS - 1L,
        )
        assertEquals(19, beforeTransition.creationFrameIndices.getValue("bastion-1"))
        assertTrue(beforeTransition.idleFrameIndices.isEmpty())

        val firstIdleFrame = clock.update(
            state,
            RunicBastionPresentation.CREATION_DURATION_MILLISECONDS,
        )
        assertEquals(19, firstIdleFrame.creationFrameIndices.getValue("bastion-1"))
        assertEquals(0, firstIdleFrame.idleFrameIndices.getValue("bastion-1"))

        val nextIdleFrame = clock.update(
            state,
            RunicBastionPresentation.CREATION_DURATION_MILLISECONDS +
                RunicBastionPresentation.IDLE_FRAME_DURATION_MILLISECONDS,
        )
        assertEquals(1, nextIdleFrame.idleFrameIndices.getValue("bastion-1"))

        assertEquals(
            0,
            RunicBastionPresentation.idleFrameIndex(
                RunicBastionPresentation.IDLE_FRAME_COUNT *
                    RunicBastionPresentation.IDLE_FRAME_DURATION_MILLISECONDS,
            ),
        )
    }

    @Test
    fun creationStartsWhenBastionAppearsAndFreezesWhilePaused() {
        val clock = RunicBastionPresentationClock()
        val state = state(bastions = listOf(bastion()))

        assertEquals(
            0,
            clock.update(state, 0L).creationFrameIndices.getValue("bastion-1"),
        )
        assertEquals(
            2,
            clock.update(state, 320L).creationFrameIndices.getValue("bastion-1"),
        )

        val pausedState = state.copy(isPaused = true)
        assertEquals(
            2,
            clock.update(pausedState, 400L).creationFrameIndices.getValue("bastion-1"),
        )
        assertEquals(
            2,
            clock.update(pausedState, 5_000L).creationFrameIndices.getValue("bastion-1"),
        )
        assertEquals(
            2,
            clock.update(state, 5_010L).creationFrameIndices.getValue("bastion-1"),
        )
        assertEquals(
            3,
            clock.update(state, 5_130L).creationFrameIndices.getValue("bastion-1"),
        )
    }

    @Test
    fun destroyedBastionIsRemovedAndARecastStartsAtTheFirstFrame() {
        val clock = RunicBastionPresentationClock()
        val state = state(bastions = listOf(bastion()))
        clock.update(state, 0L)
        clock.update(state, 640L)

        assertTrue(clock.update(state(bastions = emptyList()), 650L)
            .creationFrameIndices.isEmpty())
        val recast = clock.update(state, 660L)
        assertEquals(0, recast.creationFrameIndices.getValue("bastion-1"))
        assertTrue(recast.destructionAnimations.isEmpty())
    }

    @Test
    fun removedBastionPlaysDestructionOnceThenExpires() {
        val clock = RunicBastionPresentationClock()
        val activeState = state(bastions = listOf(bastion()))
        val destroyedState = state(bastions = emptyList())
        clock.update(activeState, 0L)
        clock.update(activeState, 640L)

        val firstFrame = clock.update(destroyedState, 650L)
        assertEquals(1, firstFrame.destructionAnimations.size)
        assertEquals("bastion-1", firstFrame.destructionAnimations.single().bastion.entityId)
        assertEquals(0, firstFrame.destructionAnimations.single().frameIndex)

        assertEquals(
            1,
            clock.update(destroyedState, 817L).destructionAnimations.single().frameIndex,
        )
        assertTrue(
            clock.update(
                destroyedState,
                650L + RunicBastionPresentation.DESTRUCTION_DURATION_MILLISECONDS,
            ).destructionAnimations.isEmpty(),
        )
    }

    @Test
    fun destructionAnimationFreezesWhilePaused() {
        val clock = RunicBastionPresentationClock()
        val activeState = state(bastions = listOf(bastion()))
        val destroyedState = state(bastions = emptyList())
        clock.update(activeState, 0L)
        clock.update(activeState, 100L)

        val paused = clock.update(destroyedState.copy(isPaused = true), 110L)
        assertEquals(0, paused.destructionAnimations.single().frameIndex)
        assertEquals(
            0,
            clock.update(destroyedState.copy(isPaused = true), 1_000L)
                .destructionAnimations.single().frameIndex,
        )
        assertEquals(
            0,
            clock.update(destroyedState, 1_010L).destructionAnimations.single().frameIndex,
        )
        assertEquals(
            1,
            clock.update(destroyedState, 1_177L).destructionAnimations.single().frameIndex,
        )
    }

    @Test
    fun matchRestartDoesNotPlayDestructionForPreviousMatch() {
        val clock = RunicBastionPresentationClock()
        val activeState = state(bastions = listOf(bastion()))
        clock.update(activeState, 0L)

        val restartedState = activeState.copy(
            matchGeneration = 1L,
            runicBastions = emptyList(),
        )
        assertTrue(clock.update(restartedState, 100L).destructionAnimations.isEmpty())
    }

    @Test
    fun spriteBoundsAlignTheVisibleArtToTheBastionGeometry() {
        val bounds = RunicBastionPresentation.spriteBounds(
            centerX = 200f,
            foundationY = RunicBastionGeometry.raisedFoundationY(400f),
            visibleWidth = RunicBastionGeometry.WIDTH,
            visibleHeight = RunicBastionGeometry.HEIGHT,
        )
        val scaleX = RunicBastionGeometry.WIDTH /
            (RunicBastionPresentation.VISIBLE_RIGHT - RunicBastionPresentation.VISIBLE_LEFT)
        val scaleY = RunicBastionGeometry.HEIGHT /
            (RunicBastionPresentation.VISIBLE_BOTTOM - RunicBastionPresentation.VISIBLE_TOP)

        assertEquals(56f, bounds.left + RunicBastionPresentation.VISIBLE_LEFT * scaleX, 0.001f)
        assertEquals(344f, bounds.left + RunicBastionPresentation.VISIBLE_RIGHT * scaleX, 0.001f)
        assertEquals(
            400f - RunicBastionGeometry.VERTICAL_LIFT - RunicBastionGeometry.HEIGHT,
            bounds.top + RunicBastionPresentation.VISIBLE_TOP * scaleY,
            0.001f,
        )
        assertEquals(
            400f - RunicBastionGeometry.VERTICAL_LIFT,
            bounds.top + RunicBastionPresentation.VISIBLE_BOTTOM * scaleY,
            0.001f,
        )
    }

    @Test
    fun destructionArtMatchesTheSharedBastionSizeAndGroundAnchor() {
        val bounds = RunicBastionPresentation.spriteBounds(
            centerX = 200f,
            foundationY = RunicBastionGeometry.raisedFoundationY(400f),
            visibleWidth = RunicBastionGeometry.WIDTH,
            visibleHeight = RunicBastionGeometry.HEIGHT,
            visibleArtBounds = RunicBastionPresentation.DESTRUCTION_VISIBLE_ART_BOUNDS,
        )
        val visibleArt = RunicBastionPresentation.DESTRUCTION_VISIBLE_ART_BOUNDS
        val scaleX = RunicBastionGeometry.WIDTH / (visibleArt.right - visibleArt.left)
        val scaleY = RunicBastionGeometry.HEIGHT / (visibleArt.bottom - visibleArt.top)

        assertEquals(
            200f - RunicBastionGeometry.WIDTH / 2f,
            bounds.left + visibleArt.left * scaleX,
            0.001f,
        )
        assertEquals(
            200f + RunicBastionGeometry.WIDTH / 2f,
            bounds.left + visibleArt.right * scaleX,
            0.001f,
        )
        assertEquals(
            400f - RunicBastionGeometry.VERTICAL_LIFT - RunicBastionGeometry.HEIGHT,
            bounds.top + visibleArt.top * scaleY,
            0.001f,
        )
        assertEquals(
            400f - RunicBastionGeometry.VERTICAL_LIFT,
            bounds.top + visibleArt.bottom * scaleY,
            0.001f,
        )
    }

    @Test
    fun matchRestartReplaysTheCreationAnimation() {
        val clock = RunicBastionPresentationClock()
        val firstState = state(bastions = listOf(bastion()))
        clock.update(firstState, 0L)
        assertEquals(
            4,
            clock.update(firstState, 480L).creationFrameIndices.getValue("bastion-1"),
        )

        val restarted = firstState.copy(matchGeneration = 1L)
        assertEquals(
            0,
            clock.update(restarted, 500L).creationFrameIndices.getValue("bastion-1"),
        )
    }

    private fun state(bastions: List<RunicBastionSnapshot>): RenderState =
        RenderStateMapper.map(
            MatchSnapshot(
                matchId = MatchId("runic-bastion-match"),
                runicBastions = bastions,
            ),
        )

    private fun bastion(): RunicBastionSnapshot = RunicBastionSnapshot(
        entityId = EntityId("bastion-1"),
        team = Team.BLUE,
        centerX = 550f,
        foundationY = 500f,
        health = 360,
        maxHealth = 360,
    )
}
