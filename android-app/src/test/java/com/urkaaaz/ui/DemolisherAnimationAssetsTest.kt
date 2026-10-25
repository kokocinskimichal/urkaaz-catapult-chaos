package com.urkaaaz.ui

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemolisherAnimationAssetsTest {
    @Test
    fun sharedIdleFramesHaveMatchingMasksAndTeamTextures() {
        assertSharedAnimationFrames("idle", 48)
    }

    @Test
    fun sharedMoveFramesHaveMatchingMasksAndTeamTextures() {
        assertSharedAnimationFrames("move", 60)
    }

    @Test
    fun sharedDeathFramesHaveMatchingMasksAndTeamTextures() {
        assertSharedAnimationFrames("death", 32, width = 768, height = 432)
    }

    @Test
    fun deathOpeningFrameMatchesTheVisibleScaleOfIdleAndMove() {
        val directory = resourceDirectory()
        val referenceScale = listOf("idle", "move").map { animation ->
            val frame = readFrame(directory, animation, 0)
            visibleHeight(frame) * BattlefieldRenderer.GENERATED_DEMOLISHER_CANVAS_SCALE /
                frame.height
        }.average().toFloat()
        val deathFrame = readFrame(directory, "death", 0)
        val deathScale =
            visibleHeight(deathFrame) *
                BattlefieldRenderer.GENERATED_DEMOLISHER_DEATH_CANVAS_SCALE /
                deathFrame.height

        assertEquals(referenceScale, deathScale, 0.01f)
    }

    private fun assertSharedAnimationFrames(
        animation: String,
        frameCount: Int,
        width: Int = 256,
        height: Int = 256,
    ) {
        val directory = resourceDirectory()
        val assets = assetDirectory()
        val maskDirectory = File(assets, "masks/$animation")
        val frameFiles = (0 until frameCount).map { index ->
            File(directory, "anim_character_demolisher_${animation}_%03d.png".format(index))
        }
        val maskFiles = (0 until frameCount).map { index ->
            File(maskDirectory, "%03d.png".format(index))
        }
        assertTrue("Demolisher shared $animation frames should exist", frameFiles.all(File::isFile))
        assertTrue("Demolisher $animation masks should exist", maskFiles.all(File::isFile))
        assertEquals(
            frameCount,
            directory.listFiles { file ->
                file.name.matches(Regex("anim_character_demolisher_${animation}_\\d{3}\\.png"))
            }.orEmpty().size,
        )
        assertTrue(
            "Team-specific Demolisher $animation frames should be replaced by shared frames",
            directory.listFiles()?.none {
                it.name.matches(
                    Regex("anim_character_demolisher_${animation}_(blue|red)_\\d{3}\\.png"),
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

        frameFiles.zip(maskFiles).forEachIndexed { index, (frameFile, maskFile) ->
            val frame = ImageIO.read(frameFile)
            val mask = ImageIO.read(maskFile)
            assertNotNull("Invalid shared $animation frame $index", frame)
            assertNotNull("Invalid $animation mask $index", mask)
            assertEquals(width, frame.width)
            assertEquals(height, frame.height)
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
                        assertEquals(pixel ushr 16 and 0xff, pixel ushr 8 and 0xff)
                        assertEquals(pixel ushr 8 and 0xff, pixel and 0xff)
                    }
                    val sourceAlpha = pixel ushr 24 and 0xff
                    val red = pixel ushr 16 and 0xff
                    val green = pixel ushr 8 and 0xff
                    val blue = pixel and 0xff
                    if (sourceAlpha > 0 && blue > 90 && blue > red * 1.12 && blue > green * 1.05) {
                        assertTrue(
                            "$animation mask should cover blue-dominant pixel at ($x, $y) in frame $index",
                            maskAlpha > 0,
                        )
                    }
                }
            }
        }
    }

    private fun readFrame(directory: File, animation: String, index: Int) =
        requireNotNull(
            ImageIO.read(
                File(directory, "anim_character_demolisher_${animation}_%03d.png".format(index)),
            ),
        )

    private fun visibleHeight(image: java.awt.image.BufferedImage): Int {
        var top = image.height
        var bottom = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) ushr 24 > 0) {
                    top = minOf(top, y)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        assertTrue("Animation frame must contain visible pixels", bottom >= top)
        return bottom - top + 1
    }

    private fun assetDirectory(): File {
        val assetDirectory = listOf(
            File("src/main/assets/animations/demolisher"),
            File("android-app/src/main/assets/animations/demolisher"),
        ).firstOrNull { it.isDirectory }
        assertNotNull("Demolisher team-texture assets were not found", assetDirectory)
        return requireNotNull(assetDirectory)
    }

    private fun resourceDirectory(): File {
        val directory = listOf(
            File("src/main/res/drawable-nodpi"),
            File("android-app/src/main/res/drawable-nodpi"),
        ).firstOrNull { it.isDirectory }
        assertNotNull("Drawable resource directory was not found", directory)
        return requireNotNull(directory)
    }

}
