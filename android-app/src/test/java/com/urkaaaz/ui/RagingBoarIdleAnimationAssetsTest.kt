package com.urkaaaz.ui

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RagingBoarIdleAnimationAssetsTest {
    @Test
    fun sharedIdleFramesHaveMatchingMasksAndTeamTextures() {
        val drawables = resourceDirectory()
        val assets = assetDirectory()
        assertEquals(
            IDLE_FRAME_COUNT,
            drawables.listFiles { file ->
                file.name.matches(Regex("anim_character_raging_boar_idle_\\d{3}\\.png"))
            }.orEmpty().size,
        )
        assertEquals(
            IDLE_FRAME_COUNT,
            File(assets, "masks/idle").listFiles { file ->
                file.name.matches(Regex("\\d{3}\\.png"))
            }.orEmpty().size,
        )
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

        var maskedPixelCount = 0
        (0 until IDLE_FRAME_COUNT).forEach { index ->
            val suffix = "%03d.png".format(index)
            val frame = ImageIO.read(
                File(drawables, "anim_character_raging_boar_idle_$suffix"),
            )
            val mask = ImageIO.read(File(assets, "masks/idle/$suffix"))
            assertNotNull("Invalid idle frame $index", frame)
            assertNotNull("Invalid idle mask $index", mask)
            assertEquals(368, frame.width)
            assertEquals(301, frame.height)
            assertEquals(frame.width, mask.width)
            assertEquals(frame.height, mask.height)
            assertTrue(frame.colorModel.hasAlpha())
            assertTrue(mask.colorModel.hasAlpha())
            assertEquals(0, frame.getRGB(0, 0) ushr 24)
            assertEquals(0, mask.getRGB(0, 0) ushr 24)

            for (y in 0 until mask.height) {
                for (x in 0 until mask.width) {
                    val maskAlpha = mask.getRGB(x, y) ushr 24 and 0xff
                    val pixel = frame.getRGB(x, y)
                    if (maskAlpha > 0) {
                        maskedPixelCount++
                        assertEquals(pixel ushr 16 and 0xff, pixel ushr 8 and 0xff)
                        assertEquals(pixel ushr 8 and 0xff, pixel and 0xff)
                        assertTrue("Masked frame pixels must remain visible", pixel ushr 24 > 0)
                    }
                    val sourceAlpha = pixel ushr 24 and 0xff
                    val red = pixel ushr 16 and 0xff
                    val green = pixel ushr 8 and 0xff
                    val blue = pixel and 0xff
                    if (
                        sourceAlpha > 0 &&
                        blue > 90 &&
                        blue > red * 1.12 &&
                        blue > green * 1.05
                    ) {
                        assertTrue(
                            "Idle mask should cover blue-dominant pixel at ($x, $y) in frame $index",
                            maskAlpha > 0,
                        )
                    }
                }
            }
        }
        assertTrue("Idle masks should cover garment pixels", maskedPixelCount > 0)
        assertTrue(
            "Team-specific Boar idle sequences should be replaced by shared frames",
            drawables.listFiles()?.none {
                it.name.matches(Regex("anim_character_raging_boar_idle_(blue|red)_\\d{3}\\.png"))
            } == true,
        )
    }

    @Test
    fun sharedIdleCanvasPreservesTheExistingBoarScaleAndGroundAnchor() {
        assertEquals(1.22f, BattlefieldRenderer.GENERATED_RAGING_BOAR_IDLE_CANVAS_SCALE, 0.001f)
        assertEquals(
            37f / 301f,
            BattlefieldRenderer.GENERATED_RAGING_BOAR_IDLE_BOTTOM_PADDING_RATIO,
            0.001f,
        )
    }

    private fun assetDirectory(): File {
        val directory = listOf(
            File("src/main/assets/animations/raging_boar"),
            File("android-app/src/main/assets/animations/raging_boar"),
        ).firstOrNull { it.isDirectory }
        assertNotNull("Raging Boar team-texture assets were not found", directory)
        return requireNotNull(directory)
    }

    private fun resourceDirectory(): File {
        val directory = listOf(
            File("src/main/res/drawable-nodpi"),
            File("android-app/src/main/res/drawable-nodpi"),
        ).firstOrNull { it.isDirectory }
        assertNotNull("Drawable resource directory was not found", directory)
        return requireNotNull(directory)
    }

    private companion object {
        const val IDLE_FRAME_COUNT = 60
    }
}
