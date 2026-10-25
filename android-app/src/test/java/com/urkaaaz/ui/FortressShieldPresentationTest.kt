package com.urkaaaz.ui

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FortressShieldPresentationTest {
    @Test
    fun shieldSequencesAreCompleteTransparentAndInsideTheirCanvases() {
        val directory = File("src/main/res/drawable-nodpi")
        val files = requireNotNull(directory.listFiles()) { "Missing ${directory.path}" }
        listOf("anim_spell_fortress_shield_dome_", "anim_spell_fortress_shield_hit_").forEach { prefix ->
            val sequence = files.filter { it.name.startsWith(prefix) && it.extension == "png" }
                .sortedBy(File::getName)
            assertEquals("$prefix frame count", FortressShieldPresentation.FRAME_COUNT, sequence.size)
            sequence.forEachIndexed { index, file ->
                assertEquals("$prefix%03d.png".format(index), file.name)
                val image = requireNotNull(ImageIO.read(file)) { "${file.name} is not a PNG" }
                assertEquals(FortressShieldPresentation.FRAME_WIDTH, image.width)
                assertEquals(FortressShieldPresentation.FRAME_HEIGHT, image.height)
                assertTrue("${file.name} has no alpha", image.colorModel.hasAlpha())
                for (x in 0 until image.width) {
                    assertEquals("${file.name} touches top edge", 0, image.getRGB(x, 0) ushr 24)
                    assertEquals(
                        "${file.name} touches bottom edge",
                        0,
                        image.getRGB(x, image.height - 1) ushr 24,
                    )
                }
                for (y in 0 until image.height) {
                    assertEquals("${file.name} touches left edge", 0, image.getRGB(0, y) ushr 24)
                    assertEquals(
                        "${file.name} touches right edge",
                        0,
                        image.getRGB(image.width - 1, y) ushr 24,
                    )
                }
            }
        }
    }

    @Test
    fun frameIndexLoopsThroughEveryFrame() {
        val step = FortressShieldPresentation.RENDER_FRAMES_PER_ANIMATION_STEP
        assertEquals(0, FortressShieldPresentation.frameIndex(0))
        assertEquals(1, FortressShieldPresentation.frameIndex(step))
        assertEquals(0, FortressShieldPresentation.frameIndex(step * FortressShieldPresentation.FRAME_COUNT))
        assertEquals(0, FortressShieldPresentation.frameIndex(-5))
    }

    @Test
    fun hitAnimationPlaysOnceAndMirrorsOnlyForTheLeftFortress() {
        val step = FortressShieldPresentation.HIT_RENDER_FRAMES_PER_ANIMATION_STEP
        val startFrame = FortressShieldPresentation.HIT_FLASH_START_FRAME
        assertEquals(startFrame, FortressShieldPresentation.hitFrameIndex(0))
        assertEquals(startFrame, FortressShieldPresentation.hitFrameIndex(step - 1))
        assertEquals(startFrame + 1, FortressShieldPresentation.hitFrameIndex(step))
        assertEquals(
            (FortressShieldPresentation.FRAME_COUNT - startFrame) * step,
            FortressShieldPresentation.hitAnimationDurationRenderFrames(),
        )
        assertEquals(
            FortressShieldPresentation.FRAME_COUNT - 1,
            FortressShieldPresentation.hitFrameIndex(
                FortressShieldPresentation.hitAnimationDurationRenderFrames() - 1,
            ),
        )
        assertTrue(FortressShieldPresentation.shouldMirrorHitHorizontally("BLUE"))
        assertTrue(!FortressShieldPresentation.shouldMirrorHitHorizontally("RED"))
    }

    @Test
    fun domePaintOpacityDoesNotChangeWithShieldHealth() {
        assertEquals(255, FortressShieldPresentation.ACTIVE_DOME_ALPHA)
    }

    @Test
    fun shieldHitRemainsVisibleWhenTheHitDepletesItsHealth() {
        assertTrue(FortressShieldPresentation.shouldRenderShield(150, false))
        assertTrue(FortressShieldPresentation.shouldRenderShield(null, true))
        assertTrue(FortressShieldPresentation.shouldRenderShield(0, true))
        assertFalse(FortressShieldPresentation.shouldRenderShield(null, false))
    }

    @Test
    fun shieldIconShadeCoversOnlyTheLostHealthAndClearsWhenInactive() {
        assertEquals(0f, FortressShieldPresentation.shieldIconCoveredFraction(150, 150), 0.0001f)
        assertEquals(0.5f, FortressShieldPresentation.shieldIconCoveredFraction(75, 150), 0.0001f)
        assertEquals(1f, FortressShieldPresentation.shieldIconCoveredFraction(0, 150), 0.0001f)
        assertEquals(0f, FortressShieldPresentation.shieldIconCoveredFraction(null, 150), 0.0001f)
    }

    @Test
    fun spriteIsPlacedSoTheVisibleDomeMatchesTheShieldEllipse() {
        val bounds = FortressShieldPresentation.spriteBounds(100f, 50f, 485f, 257f)
        assertEquals(100f - 77f, bounds.left, 0.001f)
        assertEquals(50f - 112f, bounds.top, 0.001f)
        assertEquals(640f, bounds.right - bounds.left, 0.001f)
        assertEquals(480f, bounds.bottom - bounds.top, 0.001f)
    }
}
