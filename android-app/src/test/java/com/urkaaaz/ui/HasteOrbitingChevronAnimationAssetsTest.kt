package com.urkaaaz.ui

import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HasteOrbitingChevronAnimationAssetsTest {
    @Test
    fun orbitingChevronSequenceHasTwelveUniqueTransparentFramesWithSafeMargins() {
        val drawableDirectory = File("src/main/res/drawable-nodpi")
        assertTrue("Drawable directory is missing", drawableDirectory.isDirectory)
        val files = requireNotNull(drawableDirectory.listFiles()) {
            "Could not list ${drawableDirectory.path}"
        }.filter { it.name.startsWith("anim_spell_haste_orbiting_chevron_") && it.extension == "png" }
            .sortedBy(File::getName)

        assertEquals(HasteOrbitingChevronPresentation.FRAME_COUNT, files.size)
        val frameHashes = files.map {
            MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList()
        }
        assertEquals(HasteOrbitingChevronPresentation.FRAME_COUNT, frameHashes.distinct().size)
        files.forEachIndexed { index, file ->
            assertEquals("anim_spell_haste_orbiting_chevron_%03d.png".format(index), file.name)
            val image = requireNotNull(ImageIO.read(file)) { "${file.name} is not a PNG" }
            assertEquals(HasteOrbitingChevronPresentation.FRAME_WIDTH, image.width)
            assertEquals(HasteOrbitingChevronPresentation.FRAME_HEIGHT, image.height)
            assertTrue("${file.name} has no alpha channel", image.colorModel.hasAlpha())
            var left = image.width
            var top = image.height
            var right = -1
            var bottom = -1
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    if (image.getRGB(x, y) ushr 24 > 16) {
                        left = minOf(left, x)
                        top = minOf(top, y)
                        right = maxOf(right, x)
                        bottom = maxOf(bottom, y)
                    }
                }
            }
            assertTrue("${file.name} has no visible pixels", right >= left && bottom >= top)
            assertTrue("${file.name} has less than 64 px horizontal margin", left >= 64)
            assertTrue(
                "${file.name} has less than 64 px horizontal margin",
                image.width - right - 1 >= 64,
            )
            assertTrue("${file.name} has less than 64 px vertical margin", top >= 64)
            assertTrue(
                "${file.name} has less than 64 px vertical margin",
                image.height - bottom - 1 >= 64,
            )
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

    @Test
    fun frameIndexCyclesThroughAllFramesAtTheMatchRenderCadence() {
        val loopFrames = HasteOrbitingChevronPresentation.RENDER_FRAMES_PER_ANIMATION_LOOP
        val indices =
            (0 until loopFrames).map(HasteOrbitingChevronPresentation::frameIndex).distinct()

        assertEquals((0 until HasteOrbitingChevronPresentation.FRAME_COUNT).toList(), indices)
        assertEquals(
            HasteOrbitingChevronPresentation.FRAME_COUNT - 1,
            HasteOrbitingChevronPresentation.frameIndex(loopFrames - 1),
        )
        assertEquals(0, HasteOrbitingChevronPresentation.frameIndex(loopFrames))
        assertEquals(
            HasteOrbitingChevronPresentation.FRAME_COUNT - 1,
            HasteOrbitingChevronPresentation.frameIndex(1023),
        )
        assertEquals(0, HasteOrbitingChevronPresentation.frameIndex(1024))
        assertEquals(0, HasteOrbitingChevronPresentation.frameIndex(-5))
    }

    @Test
    fun orbitingChevronOnlyRendersForLivingMovingHastedUnitsAndMirrorsWithFacing() {
        assertTrue(
            HasteOrbitingChevronPresentation.shouldRender(
                hasted = true,
                moving = true,
                defeated = false,
            ),
        )
        assertFalse(
            HasteOrbitingChevronPresentation.shouldRender(
                hasted = false,
                moving = true,
                defeated = false,
            ),
        )
        assertFalse(
            HasteOrbitingChevronPresentation.shouldRender(
                hasted = true,
                moving = false,
                defeated = false,
            ),
        )
        assertFalse(
            HasteOrbitingChevronPresentation.shouldRender(
                hasted = true,
                moving = true,
                defeated = true,
            ),
        )
        assertTrue(HasteOrbitingChevronPresentation.shouldMirrorHorizontally(-1f))
        assertFalse(HasteOrbitingChevronPresentation.shouldMirrorHorizontally(1f))
    }

    @Test
    fun spriteBoundsStayCenteredAndAnchoredAtTheUnitBaseline() {
        val bounds = HasteOrbitingChevronPresentation.spriteBounds(
            centerX = 100f,
            baselineY = 200f,
            unitSpriteWidth = 40f,
        )

        assertEquals(60f, bounds.left, 0.001f)
        assertEquals(140f, bounds.right, 0.001f)
        val spriteHeight =
            40f * HasteOrbitingChevronPresentation.EFFECT_WIDTH_TO_UNIT_WIDTH *
                HasteOrbitingChevronPresentation.FRAME_HEIGHT /
                HasteOrbitingChevronPresentation.FRAME_WIDTH
        assertEquals(
            200f + spriteHeight *
                (1f - HasteOrbitingChevronPresentation.EFFECT_TOP_FROM_BASELINE_HEIGHT),
            bounds.bottom,
            0.001f,
        )
    }
}
