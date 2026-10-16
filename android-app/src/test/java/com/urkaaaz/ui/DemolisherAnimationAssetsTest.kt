package com.urkaaaz.ui

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemolisherAnimationAssetsTest {
    @Test
    fun approvedBlueAndRedIdleFramesAreContiguousTransparentPngs() {
        val directory = resourceDirectory()

        listOf("left", "right").forEach { side ->
            val team = if (side == "left") "Blue" else "Red"
            assertContiguousFrames(
                directory = directory,
                prefix = "demolisher_${side}_idle",
                digits = 3,
                frameCount = 48,
                team = team,
                requireRedPalette = side == "right",
            )
        }
    }

    @Test
    fun approvedBlueAndRedJogFramesAreContiguousTransparentPngs() {
        val directory = resourceDirectory()

        listOf("left", "right").forEach { side ->
            val team = if (side == "left") "Blue" else "Red"
            assertContiguousFrames(
                directory = directory,
                prefix = "demolisher_${side}_jog",
                digits = 3,
                frameCount = 60,
                team = team,
                requireRedPalette = side == "right",
            )
        }
    }

    @Test
    fun acceptedBlueContactExplosionFramesAreContiguousTransparentPngs() {
        assertContiguousFrames(
            directory = resourceDirectory(),
            prefix = "demolisher_left_death",
            digits = 3,
            frameCount = 60,
            team = "Blue",
            requireTransparentCorner = false,
        )
    }

    private fun resourceDirectory(): File {
        val directory = listOf(
            File("src/main/res/drawable-nodpi"),
            File("android-app/src/main/res/drawable-nodpi"),
        ).firstOrNull { it.isDirectory }
        assertNotNull("Drawable resource directory was not found", directory)
        return requireNotNull(directory)
    }

    private fun assertContiguousFrames(
        directory: File,
        prefix: String,
        digits: Int,
        frameCount: Int,
        team: String,
        requireRedPalette: Boolean = false,
        requireTransparentCorner: Boolean = true,
    ) {
        val frameFiles = directory.listFiles { file ->
            file.name.matches(Regex("${Regex.escape(prefix)}_\\d{$digits}\\.png"))
        }.orEmpty()
        assertEquals("$team Demolisher should have $frameCount approved frames", frameCount, frameFiles.size)
        (0 until frameCount).forEach { index ->
            val imageFile = File(directory, "${prefix}_${"%0${digits}d".format(index)}.png")
            assertTrue("Missing $team Demolisher frame $index", imageFile.isFile)
            val image = ImageIO.read(imageFile)
            assertNotNull("Invalid $team Demolisher PNG frame $index", image)
            val decodedImage = requireNotNull(image)
            assertEquals(512, decodedImage.width)
            assertEquals(512, decodedImage.height)
            assertTrue(
                "$team Demolisher frame $index must preserve alpha",
                decodedImage.colorModel.hasAlpha(),
            )
            if (requireTransparentCorner) {
                assertEquals(
                    "$team Demolisher frame $index must retain a transparent canvas corner",
                    0,
                    decodedImage.getRGB(0, 0) ushr 24,
                )
            }
            if (requireRedPalette) {
                assertTrue(
                    "$team Demolisher frame $index must retain its red team palette",
                    countRedPixels(decodedImage) > 3_000,
                )
            }
        }
    }

    private fun countRedPixels(image: java.awt.image.BufferedImage): Int {
        var count = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val pixel = image.getRGB(x, y)
                val alpha = pixel ushr 24 and 0xff
                val red = pixel ushr 16 and 0xff
                val green = pixel ushr 8 and 0xff
                val blue = pixel and 0xff
                if (alpha > 80 && red > 100 && red > green * 1.35 && red > blue * 1.2) {
                    count++
                }
            }
        }
        return count
    }
}
