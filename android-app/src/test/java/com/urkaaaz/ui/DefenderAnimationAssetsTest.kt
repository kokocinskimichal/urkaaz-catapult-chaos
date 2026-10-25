package com.urkaaaz.ui

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefenderAnimationAssetsTest {
    @Test
    fun neutralDefenderFramesHaveMatchingTransparentMasks() {
        val resourceDirectory = listOf(
            File("src/main/res/drawable-nodpi"),
            File("android-app/src/main/res/drawable-nodpi"),
        ).firstOrNull { it.isDirectory }
        val assetDirectory = listOf(
            File("src/main/assets/animations/defender"),
            File("android-app/src/main/assets/animations/defender"),
        ).firstOrNull { it.isDirectory }
        assertNotNull("Drawable resource directory was not found", resourceDirectory)
        assertNotNull("Defender animation asset directory was not found", assetDirectory)

        val counts = mapOf("idle" to 60, "move" to 60, "attack" to 30, "death" to 60)
        val drawables = requireNotNull(resourceDirectory)
        val assets = requireNotNull(assetDirectory)
        val paletteConfig = File(assets, "palettes.json").readText()
        listOf("BLUE", "RED").forEach { team ->
            val texturePath = Regex("\"$team\"\\s*:\\s*\"([^\"]+)\"")
                .find(paletteConfig)
                ?.groupValues
                ?.get(1)
            assertNotNull("$team must have a configured fabric texture", texturePath)
            assertTrue(
                "$team fabric texture must exist",
                File(assets.parentFile.parentFile, requireNotNull(texturePath)).isFile,
            )
        }
        counts.forEach { (animation, frameCount) ->
            val prefix = "anim_character_defender_$animation"
            val frameFiles = drawables.listFiles { file ->
                file.name.matches(Regex("${prefix}_\\d{3}\\.png"))
            }.orEmpty()
            assertEquals("$animation should have $frameCount neutral frames", frameCount, frameFiles.size)
            var maskedPixelCount = 0

            (0 until frameCount).forEach { index ->
                val suffix = "%03d.png".format(index)
                val frame = ImageIO.read(File(drawables, "${prefix}_$suffix"))
                val mask = ImageIO.read(File(assets, "masks/$animation/$suffix"))
                assertNotNull("Invalid $animation frame $index", frame)
                assertNotNull("Invalid $animation mask $index", mask)
                assertEquals(256, frame.width)
                assertEquals(256, frame.height)
                assertEquals(frame.width, mask.width)
                assertEquals(frame.height, mask.height)
                assertTrue(frame.colorModel.hasAlpha())
                assertTrue(mask.colorModel.hasAlpha())
                assertEquals(0, frame.getRGB(0, 0) ushr 24)
                assertEquals(0, mask.getRGB(0, 0) ushr 24)
                if (index == 0) {
                    assertTrue(
                        "$animation mask should include the blue shield field",
                        (160 until mask.width).any { x ->
                            (0 until mask.height).any { y -> mask.getRGB(x, y) ushr 24 > 0 }
                        },
                    )
                }
                for (y in 0 until mask.height) {
                    for (x in 0 until mask.width) {
                        val maskAlpha = mask.getRGB(x, y) ushr 24 and 0xff
                        if (maskAlpha > 0) maskedPixelCount++

                        val sourcePixel = frame.getRGB(x, y)
                        val sourceAlpha = sourcePixel ushr 24 and 0xff
                        val red = sourcePixel ushr 16 and 0xff
                        val green = sourcePixel ushr 8 and 0xff
                        val blue = sourcePixel and 0xff
                        if (
                            sourceAlpha > 0 &&
                                blue > 90 &&
                                blue > red * 1.12 &&
                                blue > green * 1.05
                        ) {
                            assertTrue(
                                "$animation mask should cover blue-dominant pixel at ($x, $y) in frame $index",
                                maskAlpha > 0,
                            )
                        }
                    }
                }
            }
            assertTrue("$animation masks should contain garment pixels", maskedPixelCount > 0)
        }
        assertTrue(
            "Team-specific Defender animation frames should be replaced by neutral frames",
            drawables.listFiles()?.none { it.name.matches(Regex("anim_character_defender_(idle|move|attack|death)_(blue|red)_\\d{3}\\.png")) } == true,
        )
    }
}
