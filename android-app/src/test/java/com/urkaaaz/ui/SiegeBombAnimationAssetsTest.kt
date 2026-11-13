package com.urkaaaz.ui

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiegeBombAnimationAssetsTest {
    @Test
    fun flightFramesAreACompleteTransparentSequenceWithSafeMargins() {
        val drawableDirectory = File("src/main/res/drawable-nodpi")
        assertTrue("Drawable directory is missing", drawableDirectory.isDirectory)
        val prefix = "anim_ammunition_siege_bomb_flight"
        val files = requireNotNull(drawableDirectory.listFiles()) {
            "Could not list ${drawableDirectory.path}"
        }.filter { it.name.startsWith(prefix + "_") && it.extension == "png" }
            .sortedBy(File::getName)

        assertEquals(32, files.size)
        files.forEachIndexed { index, file ->
            assertEquals(
                "$prefix frame index",
                "${prefix}_%03d.png".format(index),
                file.name,
            )
            val image = requireNotNull(ImageIO.read(file)) {
                "$prefix frame $index is not a PNG"
            }
            assertEquals("$prefix frame $index width", FRAME_WIDTH, image.width)
            assertEquals("$prefix frame $index height", FRAME_HEIGHT, image.height)
            assertTrue("$prefix frame $index must have alpha", image.colorModel.hasAlpha())
            assertTrue(
                "$prefix frame $index must not touch the canvas edge",
                hasTransparentEdges(image),
            )
        }
    }

    @Test
    fun impactFramesAreACompleteTransparentSequenceWithSafeMargins() {
        val drawableDirectory = File("src/main/res/drawable-nodpi")
        assertTrue("Drawable directory is missing", drawableDirectory.isDirectory)
        val prefix = "anim_ammunition_siege_bomb_impact"
        val files = requireNotNull(drawableDirectory.listFiles()) {
            "Could not list ${drawableDirectory.path}"
        }.filter { it.name.startsWith(prefix + "_") && it.extension == "png" }
            .sortedBy(File::getName)

        assertEquals(32, files.size)
        files.forEachIndexed { index, file ->
            assertEquals(
                "$prefix frame index",
                "${prefix}_%03d.png".format(index),
                file.name,
            )
            val image = requireNotNull(ImageIO.read(file)) {
                "$prefix frame $index is not a PNG"
            }
            assertEquals("$prefix frame $index width", IMPACT_FRAME_WIDTH, image.width)
            assertEquals("$prefix frame $index height", IMPACT_FRAME_HEIGHT, image.height)
            assertTrue("$prefix frame $index must have alpha", image.colorModel.hasAlpha())
            assertTrue(
                "$prefix frame $index must not touch the canvas edge",
                hasTransparentEdges(image),
            )
        }
    }

    private fun hasTransparentEdges(image: java.awt.image.BufferedImage): Boolean {
        val alpha = image.alphaRaster ?: return false
        val lastX = image.width - 1
        val lastY = image.height - 1
        for (x in 0 until image.width) {
            if (
                alpha.getSample(x, 0, 0) != 0 ||
                alpha.getSample(x, lastY, 0) != 0
            ) {
                return false
            }
        }
        for (y in 0 until image.height) {
            if (
                alpha.getSample(0, y, 0) != 0 ||
                alpha.getSample(lastX, y, 0) != 0
            ) {
                return false
            }
        }
        return true
    }

    private companion object {
        const val FRAME_WIDTH = 138
        const val FRAME_HEIGHT = 158
        const val IMPACT_FRAME_WIDTH = 248
        const val IMPACT_FRAME_HEIGHT = 181
    }
}
