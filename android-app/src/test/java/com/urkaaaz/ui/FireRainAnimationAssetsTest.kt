package com.urkaaaz.ui

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FireRainAnimationAssetsTest {
    @Test
    fun everyFireRainPhaseHasItsCompleteTransparentPngSequence() {
        val drawableDirectory = File("src/main/res/drawable-nodpi")
        assertTrue("Drawable directory is missing", drawableDirectory.isDirectory)
        val phases = listOf(
            "fire_rain_flight" to 24,
            "fire_rain_split_flash" to 12,
            "fire_rain_fragment_flight" to 24,
            "fire_rain_field" to 24,
        )

        phases.forEach { (prefix, frameCount) ->
            val files = requireNotNull(drawableDirectory.listFiles()) {
                "Could not list ${drawableDirectory.path}"
            }.filter { it.name.startsWith("${prefix}_") && it.extension == "png" }
                .sortedBy(File::getName)

            assertEquals("$prefix frame count", frameCount, files.size)
            files.forEachIndexed { index, file ->
                assertEquals("$prefix frame index", "${prefix}_%02d.png".format(index), file.name)
                val image = requireNotNull(ImageIO.read(file)) {
                    "$prefix frame $index is not a PNG"
                }
                assertEquals("$prefix frame $index width", FRAME_SIZE, image.width)
                assertEquals("$prefix frame $index height", FRAME_SIZE, image.height)
                assertTrue("$prefix frame $index has no alpha channel", image.colorModel.hasAlpha())
            }
        }
    }

    @Test
    fun fireFieldSequenceEndsWithACompletelyTransparentFrame() {
        val lastFrame = File(
            "src/main/res/drawable-nodpi/fire_rain_field_23.png",
        )
        val image = requireNotNull(ImageIO.read(lastFrame)) {
            "Final fire-field frame is not a PNG"
        }
        assertTrue(image.colorModel.hasAlpha())
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                assertEquals("Final frame contains an opaque pixel at ($x, $y)", 0, image.getRGB(x, y) ushr 24)
            }
        }
    }

    @Test
    fun visibleFireFieldFootprintMatchesTheRendererGroundAnchor() {
        listOf("00", "12").forEach { frame ->
            val image = requireNotNull(
                ImageIO.read(File("src/main/res/drawable-nodpi/fire_rain_field_$frame.png")),
            )
            var minX = image.width
            var maxX = -1
            var maxY = -1
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    if ((image.getRGB(x, y) ushr 24) > 0) {
                        minX = minOf(minX, x)
                        maxX = maxOf(maxX, x)
                        maxY = maxOf(maxY, y)
                    }
                }
            }

            assertTrue("$frame should have a visible fire patch", maxX >= minX)
            assertTrue("$frame visible width is too small", maxX - minX + 1 >= 128)
            assertTrue(
                "$frame ground alpha anchor should stay near the renderer anchor",
                maxY + 1 in 373..374,
            )
        }
    }

    private companion object {
        const val FRAME_SIZE = 512
    }
}
