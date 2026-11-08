package com.urkaaaz.ui

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlingmasterAnimationAssetsTest {
    @Test
    fun allAnimationSequencesHaveMatchingMasksAndTeamTextures() {
        val drawables = resourceDirectory()
        val assets = assetDirectory()
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

        val visibleCenterFractions = mutableMapOf<String, List<Float>>()
        mapOf(
            "idle" to IDLE_FRAME_COUNT,
            "move" to MOVE_FRAME_COUNT,
            "attack" to ATTACK_FRAME_COUNT,
            "death" to DEATH_FRAME_COUNT,
        )
            .forEach { (animation, frameCount) ->
                val sequencePrefix = "anim_character_slingmaster_$animation"
                assertEquals(
                    "$animation should have $frameCount shared frames",
                    frameCount,
                    drawables.listFiles { file ->
                        file.name.matches(Regex("${sequencePrefix}_\\d{3}\\.png"))
                    }.orEmpty().size,
                )
                var maskedPixelCount = 0
                val visibleHeightFractions = mutableListOf<Float>()
                val bottomPaddingRatios = mutableListOf<Float>()
                val frameCenterFractions = mutableListOf<Float>()
                (0 until frameCount).forEach { index ->
                    val suffix = "%03d.png".format(index)
                    val frame = readImage(File(drawables, "${sequencePrefix}_$suffix"))
                    val mask = readImage(File(assets, "masks/$animation/$suffix"))
                    assertEquals(FRAME_SIZE, frame.width)
                    assertEquals(FRAME_SIZE, frame.height)
                    assertEquals(frame.width, mask.width)
                    assertEquals(frame.height, mask.height)
                    assertTrue(frame.colorModel.hasAlpha())
                    assertTrue(mask.colorModel.hasAlpha())
                    assertEquals(0, frame.getRGB(0, 0) ushr 24)
                    assertEquals(0, mask.getRGB(0, 0) ushr 24)

                    var frameMaskedPixelCount = 0
                    for (y in 0 until mask.height) {
                        for (x in 0 until mask.width) {
                            val maskAlpha = mask.getRGB(x, y) ushr 24 and 0xff
                            val pixel = frame.getRGB(x, y)
                            val sourceAlpha = pixel ushr 24 and 0xff
                            val red = pixel ushr 16 and 0xff
                            val green = pixel ushr 8 and 0xff
                            val blue = pixel and 0xff
                            if (maskAlpha > 0) {
                                assertTrue(
                                    "Masks must not include transparent pixels",
                                    sourceAlpha > 0,
                                )
                                assertEquals("Masked fabric must be neutral grayscale", red, green)
                                assertEquals("Masked fabric must be neutral grayscale", green, blue)
                                frameMaskedPixelCount++
                            } else if (sourceAlpha > 0) {
                                assertTrue(
                                    "Mask should cover blue-dominant pixels at ($x, $y) " +
                                        "in $animation frame $index",
                                    !(blue > 90 && blue > red * 1.12 && blue > green * 1.05),
                                )
                            }
                        }
                    }
                    assertTrue(
                        "$animation frame $index should have a garment mask",
                        frameMaskedPixelCount > 0,
                    )
                    maskedPixelCount += frameMaskedPixelCount
                    val visible = visibleBounds(frame)
                    assertTrue(
                        "$animation frame $index must keep a transparent border",
                        visible.left > 0 &&
                            visible.top > 0 &&
                            visible.right < frame.width &&
                            visible.bottom < frame.height,
                    )
                    visibleHeightFractions +=
                        (visible.bottom - visible.top).toFloat() / frame.height
                    bottomPaddingRatios +=
                        (frame.height - visible.bottom).toFloat() / frame.height
                    frameCenterFractions +=
                        ((visible.left + visible.right).toFloat() / 2f) / frame.width
                }

                assertTrue("$animation masks should contain garment pixels", maskedPixelCount > 0)
                visibleCenterFractions[animation] = frameCenterFractions
                val canvasScale = when (animation) {
                    "attack" ->
                        BattlefieldRenderer.GENERATED_SLINGMASTER_ATTACK_CANVAS_SCALE
                    "death" ->
                        BattlefieldRenderer.GENERATED_SLINGMASTER_DEATH_CANVAS_SCALE
                    else -> BattlefieldRenderer.GENERATED_SLINGMASTER_CANVAS_SCALE
                }
                val bottomPaddingRatio = when (animation) {
                    "attack" ->
                        BattlefieldRenderer.GENERATED_SLINGMASTER_ATTACK_BOTTOM_PADDING_RATIO
                    "death" ->
                        BattlefieldRenderer.GENERATED_SLINGMASTER_DEATH_BOTTOM_PADDING_RATIO
                    else -> BattlefieldRenderer.GENERATED_SLINGMASTER_BOTTOM_PADDING_RATIO
                }
                assertEquals(
                    "The Slingmaster scale should preserve its previous visible height in $animation",
                    303f / 384f,
                    visibleHeightFractions.average().toFloat() *
                        canvasScale,
                    0.03f,
                )
                assertEquals(
                    "The Slingmaster bottom anchor should remain stable in $animation",
                    bottomPaddingRatio,
                    bottomPaddingRatios.average().toFloat(),
                    0.005f,
                )
            }

        val idleAndMoveCenter =
            listOf("idle", "move")
                .map { animation ->
                    visibleCenterFractions.getValue(animation).average().toFloat()
                }
                .average()
                .toFloat()
        val adjustedAttackCenter =
            visibleCenterFractions.getValue("attack").average().toFloat() +
                BattlefieldRenderer.GENERATED_SLINGMASTER_ATTACK_HORIZONTAL_OFFSET_RATIO
        assertEquals(
            "The attack presentation offset should align its average center with idle and move",
            idleAndMoveCenter,
            adjustedAttackCenter,
            0.01f,
        )
    }

    private fun readImage(file: File): BufferedImage =
        requireNotNull(ImageIO.read(file)) { "Could not read image '${file.path}'" }

    private fun visibleBounds(image: BufferedImage): VisibleBounds {
        var left = image.width
        var top = image.height
        var right = -1
        var bottom = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) ushr 24 > 0) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x + 1)
                    bottom = maxOf(bottom, y + 1)
                }
            }
        }
        assertTrue("Slingmaster frame must contain visible pixels", bottom > top)
        return VisibleBounds(left, top, right, bottom)
    }

    private fun resourceDirectory(): File {
        val directory = listOf(
            File("src/main/res/drawable-nodpi"),
            File("android-app/src/main/res/drawable-nodpi"),
        ).firstOrNull { it.isDirectory }
        assertNotNull("Drawable resource directory was not found", directory)
        return requireNotNull(directory)
    }

    private fun assetDirectory(): File {
        val directory = listOf(
            File("src/main/assets/animations/slingmaster"),
            File("android-app/src/main/assets/animations/slingmaster"),
        ).firstOrNull { it.isDirectory }
        assertNotNull("Slingmaster team-texture assets were not found", directory)
        return requireNotNull(directory)
    }

    private data class VisibleBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    private companion object {
        const val FRAME_SIZE = 256
        const val IDLE_FRAME_COUNT = 60
        const val MOVE_FRAME_COUNT = 16
        const val ATTACK_FRAME_COUNT = 27
        const val DEATH_FRAME_COUNT = 30
    }
}
