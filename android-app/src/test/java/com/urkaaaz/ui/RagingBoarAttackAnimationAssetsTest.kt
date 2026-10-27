package com.urkaaaz.ui

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RagingBoarAttackAnimationAssetsTest {
    @Test
    fun sharedAttackFramesHaveMatchingMasksAndTeamTextures() {
        val drawables = resourceDirectory()
        val assets = assetDirectory()
        val maskDirectory = File(assets, "masks/attack")
        val frameFiles = (0 until ATTACK_FRAME_COUNT).map { index ->
            File(drawables, "anim_character_raging_boar_attack_%03d.png".format(index))
        }
        val maskFiles = (0 until ATTACK_FRAME_COUNT).map { index ->
            File(maskDirectory, "%03d.png".format(index))
        }

        assertTrue("Shared attack frames should exist", frameFiles.all(File::isFile))
        assertTrue("Attack masks should exist", maskFiles.all(File::isFile))
        assertEquals(
            ATTACK_FRAME_COUNT,
            maskDirectory.listFiles { file -> file.name.matches(Regex("\\d{3}\\.png")) }
                .orEmpty()
                .size,
        )
        assertEquals(
            ATTACK_FRAME_COUNT,
            drawables.listFiles { file ->
                file.name.matches(Regex("anim_character_raging_boar_attack_\\d{3}\\.png"))
            }.orEmpty().size,
        )
        assertTrue(
            "Team-specific attack sequences should be replaced by shared frames",
            drawables.listFiles()?.none {
                it.name.matches(
                    Regex("anim_character_raging_boar_attack_(blue|red)_\\d{3}\\.png"),
                )
            } == true,
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
        frameFiles.zip(maskFiles).forEachIndexed { index, (frameFile, maskFile) ->
            val frame = ImageIO.read(frameFile)
            val mask = ImageIO.read(maskFile)
            assertNotNull("Invalid attack frame $index", frame)
            assertNotNull("Invalid attack mask $index", mask)
            assertEquals(368, frame.width)
            assertEquals(301, frame.height)
            assertEquals(frame.width, mask.width)
            assertEquals(frame.height, mask.height)
            assertTrue(frame.colorModel.hasAlpha())
            assertTrue(mask.colorModel.hasAlpha())
            assertEquals(0, frame.getRGB(0, 0) ushr 24)
            assertEquals(0, mask.getRGB(0, 0) ushr 24)
            for (x in 0 until frame.width) {
                assertEquals(0, frame.getRGB(x, 0) ushr 24)
                assertEquals(0, frame.getRGB(x, frame.height - 1) ushr 24)
            }
            for (y in 0 until frame.height) {
                assertEquals(0, frame.getRGB(0, y) ushr 24)
                assertEquals(0, frame.getRGB(frame.width - 1, y) ushr 24)
            }

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
                    assertFalse(
                        "Attack frame should not contain saturated magenta Alpha Key residue",
                        sourceAlpha > 0 && red > 160 && blue > 150 && green < 100,
                    )
                    if (
                        sourceAlpha > 0 &&
                        blue > 90 &&
                        blue > red * 1.12 &&
                        blue > green * 1.05
                    ) {
                        assertTrue(
                            "Attack mask should cover blue-dominant pixel at ($x, $y) in frame $index",
                            maskAlpha > 0,
                        )
                    }
                }
            }
        }
        assertTrue("Attack masks should cover garment pixels", maskedPixelCount > 0)
    }

    @Test
    fun attackCanvasMatchesTheApprovedBoarScaleAndGroundAnchor() {
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
        const val ATTACK_FRAME_COUNT = 19
    }
}
