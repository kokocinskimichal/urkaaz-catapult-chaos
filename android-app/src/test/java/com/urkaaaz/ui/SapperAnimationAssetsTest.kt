package com.urkaaaz.ui

import java.awt.image.BufferedImage
import java.io.File
import java.util.ArrayDeque
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SapperAnimationAssetsTest {
    @Test
    fun sharedFramesHaveMatchingNeutralMasksAndTeamTextures() {
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

        listOf(
            "idle" to IDLE_FRAME_COUNT,
            "move" to MOVE_FRAME_COUNT,
            "attack" to ATTACK_FRAME_COUNT,
            "turn" to TURN_FRAME_COUNT,
            "return" to RETURN_FRAME_COUNT,
        ).forEach { (animation, frameCount) ->
            var maskedPixelCount = 0
            (0 until frameCount).forEach { index ->
                val suffix = "%03d.png".format(index)
                val frame = readFrame(drawables, animation, index)
                val mask = ImageIO.read(File(assets, "masks/$animation/$suffix"))
                assertNotNull("Invalid Sapper $animation mask frame $index", mask)
                assertEquals(FRAME_SIZE, frame.width)
                assertEquals(FRAME_SIZE, frame.height)
                assertEquals(frame.width, mask.width)
                assertEquals(frame.height, mask.height)
                assertTrue(frame.colorModel.hasAlpha())
                assertTrue(mask.colorModel.hasAlpha())
                assertEquals(0, frame.getRGB(0, 0) ushr 24)
                assertEquals(0, mask.getRGB(0, 0) ushr 24)

                var frameMaskedPixelCount = 0
                var frameHeadWrapMaskedPixelCount = 0
                for (y in 0 until mask.height) {
                    for (x in 0 until mask.width) {
                        val maskAlpha = mask.getRGB(x, y) ushr 24 and 0xff
                        val pixel = frame.getRGB(x, y)
                        val sourceAlpha = pixel ushr 24 and 0xff
                        val red = pixel ushr 16 and 0xff
                        val green = pixel ushr 8 and 0xff
                        val blue = pixel and 0xff
                        if (maskAlpha == 0) {
                            assertFalse(
                                "Mask should cover blue-dominant garment pixels at ($x, $y) in $animation frame $index",
                                sourceAlpha > 0 &&
                                    blue > 90 &&
                                    blue > red * 1.12 &&
                                    blue > green * 1.05,
                            )
                            continue
                        }
                        maskedPixelCount++
                        frameMaskedPixelCount++
                        assertTrue("Mask must not include transparent pixels", sourceAlpha > 0)
                        if (
                            animation == "return" &&
                            x in RETURN_HEAD_WRAP_LEFT until RETURN_HEAD_WRAP_RIGHT &&
                            y in RETURN_HEAD_WRAP_TOP until RETURN_HEAD_WRAP_BOTTOM
                        ) {
                            frameHeadWrapMaskedPixelCount++
                        }
                        assertEquals("Masked frame pixels must be neutral grayscale", red, green)
                        assertEquals("Masked frame pixels must be neutral grayscale", green, blue)
                    }
                }
                if (animation == "return") {
                    assertTrue(
                        "Sapper return mask frame $index should cover visible fabric",
                        frameMaskedPixelCount >= MIN_RETURN_MASKED_PIXELS,
                    )
                    assertTrue(
                        "Sapper return mask frame $index should color the head wrap",
                        frameHeadWrapMaskedPixelCount >= MIN_RETURN_HEAD_WRAP_MASKED_PIXELS,
                    )
                }
            }
            assertTrue("Sapper $animation masks should contain garment pixels", maskedPixelCount > 0)
            assertEquals(
                "Exactly $frameCount shared neutral Sapper $animation frames should exist",
                frameCount,
                drawables.listFiles { file ->
                    file.name.matches(Regex("anim_character_sapper_${animation}_\\d{3}\\.png"))
                }.orEmpty().size,
            )
        }
    }

    @Test
    fun generatedScaleMatchesIdleAndMoveSizeAndPreservesGroundAnchor() {
        val drawables = resourceDirectory()
        val reference = readImage(File(drawables, "sapper_left.png"))
        val referenceBounds = visibleBounds(reference)
        val referenceVisibleHeight =
            (referenceBounds.bottom - referenceBounds.top).toFloat() / reference.height
        val idleFrame = readFrame(drawables, "idle", 0)
        val moveFrame = readFrame(drawables, "move", 0)
        val idleVisibleBounds = visibleBounds(idleFrame)
        val moveVisibleBounds = visibleBounds(moveFrame)
        val idleVisibleHeight =
            visibleHeight(idleFrame) * BattlefieldRenderer.GENERATED_SAPPER_CANVAS_SCALE /
                idleFrame.height
        val moveStartVisibleHeight =
            visibleHeight(moveFrame) * BattlefieldRenderer.GENERATED_SAPPER_MOVE_CANVAS_SCALE /
                moveFrame.height
        val idleVisibleWidth =
            (idleVisibleBounds.right - idleVisibleBounds.left).toFloat() *
                BattlefieldRenderer.GENERATED_SAPPER_CANVAS_SCALE / idleFrame.width
        val moveStartVisibleWidth =
            (moveVisibleBounds.right - moveVisibleBounds.left).toFloat() *
                BattlefieldRenderer.GENERATED_SAPPER_MOVE_CANVAS_SCALE / moveFrame.width
        val staticFrames = (12..25).map { readFrame(drawables, "idle", it) }
        val averageBottomPadding = staticFrames.map { frame ->
            (frame.height - visibleBounds(frame).bottom).toFloat() / frame.height
        }.average().toFloat()
        val moveFrames = (0 until MOVE_FRAME_COUNT).map { readFrame(drawables, "move", it) }
        val moveVisibleHeight = moveFrames.map { frame ->
            visibleHeight(frame) * BattlefieldRenderer.GENERATED_SAPPER_MOVE_CANVAS_SCALE /
                frame.height
        }.average().toFloat()
        val moveBottomPadding = moveFrames.map { frame ->
            (frame.height - visibleBounds(frame).bottom).toFloat() / frame.height
        }
        val attackFrames = (0 until ATTACK_FRAME_COUNT).map {
            readFrame(drawables, "attack", it)
        }
        val attackStartVisibleHeight =
            visibleHeight(attackFrames.first()) *
                BattlefieldRenderer.GENERATED_SAPPER_ATTACK_CANVAS_SCALE /
                attackFrames.first().height
        val attackBottomPadding = attackFrames.map { frame ->
            (frame.height - visibleBounds(frame).bottom).toFloat() / frame.height
        }
        val turnFrames = (0 until TURN_FRAME_COUNT).map { readFrame(drawables, "turn", it) }
        val turnCanvasScales = turnFrames.indices.map { index ->
            BattlefieldRenderer.sapperTurnCanvasScale(index, turnFrames.size)
        }
        val turnBottomPadding = turnFrames.map { frame ->
            (frame.height - visibleBounds(frame).bottom).toFloat() / frame.height
        }
        val returnFrames = (0 until RETURN_FRAME_COUNT).map {
            readFrame(drawables, "return", it)
        }
        val attackToTurnStart = scaledBodySize(
            attackFrames.last(),
            BattlefieldRenderer.GENERATED_SAPPER_ATTACK_CANVAS_SCALE,
        )
        val turnStart = scaledBodySize(turnFrames.first(), turnCanvasScales.first())
        val turnEnd = scaledBodySize(turnFrames.last(), turnCanvasScales.last())
        val returnStart = scaledBodySize(
            returnFrames.first(),
            BattlefieldRenderer.GENERATED_SAPPER_RETURN_CANVAS_SCALE,
        )
        val returnVisibleHeight = returnFrames.map { frame ->
            visibleHeight(frame) * BattlefieldRenderer.GENERATED_SAPPER_RETURN_CANVAS_SCALE /
                frame.height
        }
        val returnGroundBottomPadding = returnFrames.map { frame ->
            (frame.height - visibleBounds(frame).bottom).toFloat() / frame.height
        }
        val returnHorizontalCenters = returnFrames.map { frame ->
            val bounds = visibleBounds(frame)
            (bounds.left + bounds.right).toFloat() / (2f * frame.width)
        }

        assertEquals(
            BattlefieldRenderer.GENERATED_SAPPER_MOVE_CANVAS_SCALE,
            BattlefieldRenderer.GENERATED_SAPPER_CANVAS_SCALE,
            0f,
        )
        assertEquals(moveStartVisibleHeight, idleVisibleHeight, 0.02f)
        assertEquals(moveStartVisibleWidth, idleVisibleWidth, 0.02f)
        assertEquals(referenceVisibleHeight, moveVisibleHeight, 0.03f)
        assertEquals(referenceVisibleHeight, attackStartVisibleHeight, 0.03f)
        assertEquals(
            BattlefieldRenderer.GENERATED_SAPPER_BOTTOM_PADDING_RATIO,
            averageBottomPadding,
            0.01f,
        )
        assertEquals(
            BattlefieldRenderer.GENERATED_SAPPER_MOVE_BOTTOM_PADDING_RATIO,
            moveBottomPadding.average().toFloat(),
            0.02f,
        )
        assertTrue(moveBottomPadding.max() - moveBottomPadding.min() <= 0.04f)
        assertEquals(
            BattlefieldRenderer.GENERATED_SAPPER_ATTACK_BOTTOM_PADDING_RATIO,
            attackBottomPadding.average().toFloat(),
            0.02f,
        )
        assertTrue(attackBottomPadding.max() - attackBottomPadding.min() <= 0.02f)
        assertEquals(
            BattlefieldRenderer.GENERATED_SAPPER_ATTACK_CANVAS_SCALE,
            turnCanvasScales.first(),
            0f,
        )
        assertEquals(
            BattlefieldRenderer.GENERATED_SAPPER_RETURN_CANVAS_SCALE,
            turnCanvasScales.last(),
            0f,
        )
        assertTrue(turnCanvasScales.zipWithNext().all { (start, end) -> end > start })
        assertEquals(attackToTurnStart.height, turnStart.height, 0.02f)
        assertEquals(attackToTurnStart.width, turnStart.width, 0.02f)
        assertEquals(attackToTurnStart.bottomPadding, turnStart.bottomPadding, 0.02f)
        assertEquals(turnEnd.height, returnStart.height, 0.02f)
        assertEquals(turnEnd.width, returnStart.width, 0.02f)
        assertEquals(turnEnd.bottomPadding, returnStart.bottomPadding, 0.02f)
        assertEquals(referenceVisibleHeight, returnVisibleHeight.average().toFloat(), 0.03f)
        assertEquals(
            BattlefieldRenderer.GENERATED_SAPPER_TURN_BOTTOM_PADDING_RATIO,
            turnBottomPadding.first(),
            0.01f,
        )
        assertEquals(
            BattlefieldRenderer.GENERATED_SAPPER_TURN_BOTTOM_PADDING_RATIO,
            turnBottomPadding.last(),
            0.01f,
        )
        assertTrue(turnBottomPadding.max() - turnBottomPadding.min() <= 0.08f)
        assertEquals(
            BattlefieldRenderer.GENERATED_SAPPER_RETURN_BOTTOM_PADDING_RATIO,
            returnGroundBottomPadding.first(),
            0.005f,
        )
        assertTrue(returnGroundBottomPadding.max() - returnGroundBottomPadding.min() <= 0.04f)
        assertTrue(
            kotlin.math.abs(returnHorizontalCenters.first() - returnHorizontalCenters.last()) <=
                0.03f,
        )
        assertTrue(returnHorizontalCenters.max() - returnHorizontalCenters.min() <= 0.05f)
    }

    private fun readFrame(directory: File, animation: String, index: Int) =
        readImage(File(directory, "anim_character_sapper_${animation}_%03d.png".format(index)))

    private fun readImage(file: File): BufferedImage =
        requireNotNull(ImageIO.read(file)) { "Could not read image '${file.path}'" }

    private fun visibleHeight(image: BufferedImage): Int {
        val bounds = visibleBounds(image)
        assertTrue("Sapper frame must contain visible pixels", bounds.bottom > bounds.top)
        return bounds.bottom - bounds.top
    }

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
        assertTrue("Image must contain visible pixels", bottom > top)
        return VisibleBounds(left, top, right, bottom)
    }

    private fun scaledBodySize(image: BufferedImage, scale: Float): ScaledBodySize {
        val bounds = largestComponentBounds(image)
        return ScaledBodySize(
            height = (bounds.bottom - bounds.top).toFloat() * scale / image.height,
            width = (bounds.right - bounds.left).toFloat() * scale / image.width,
            bottomPadding = (image.height - bounds.bottom).toFloat() * scale / image.height,
        )
    }

    private fun largestComponentBounds(image: BufferedImage): VisibleBounds {
        val visited = BooleanArray(image.width * image.height)
        var largestBounds: VisibleBounds? = null
        var largestArea = 0

        for (startY in 0 until image.height) {
            for (startX in 0 until image.width) {
                val startIndex = startY * image.width + startX
                if (visited[startIndex] || !isVisible(image, startX, startY)) continue

                val pending = ArrayDeque<Int>()
                pending.addLast(startIndex)
                visited[startIndex] = true
                var area = 0
                var left = startX
                var top = startY
                var right = startX
                var bottom = startY

                while (pending.isNotEmpty()) {
                    val index = pending.removeLast()
                    val x = index % image.width
                    val y = index / image.width
                    area++
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x + 1)
                    bottom = maxOf(bottom, y + 1)

                    for (neighborY in maxOf(0, y - 1)..minOf(image.height - 1, y + 1)) {
                        for (
                            neighborX in maxOf(0, x - 1)..minOf(image.width - 1, x + 1)
                        ) {
                            val neighborIndex = neighborY * image.width + neighborX
                            if (
                                !visited[neighborIndex] &&
                                isVisible(image, neighborX, neighborY)
                            ) {
                                visited[neighborIndex] = true
                                pending.addLast(neighborIndex)
                            }
                        }
                    }
                }

                if (area > largestArea) {
                    largestArea = area
                    largestBounds = VisibleBounds(left, top, right, bottom)
                }
            }
        }
        assertTrue("Image must contain a visible character body", largestBounds != null)
        return requireNotNull(largestBounds)
    }

    private fun isVisible(image: BufferedImage, x: Int, y: Int) =
        image.getRGB(x, y) ushr 24 > 16

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
            File("src/main/assets/animations/sapper"),
            File("android-app/src/main/assets/animations/sapper"),
        ).firstOrNull { it.isDirectory }
        assertNotNull("Sapper team-texture assets were not found", directory)
        return requireNotNull(directory)
    }

    private data class VisibleBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    private data class ScaledBodySize(
        val height: Float,
        val width: Float,
        val bottomPadding: Float,
    )

    private companion object {
        const val IDLE_FRAME_COUNT = 30
        const val MOVE_FRAME_COUNT = 15
        const val ATTACK_FRAME_COUNT = 15
        const val TURN_FRAME_COUNT = 15
        const val RETURN_FRAME_COUNT = 15
        const val MIN_RETURN_MASKED_PIXELS = 400
        const val MIN_RETURN_HEAD_WRAP_MASKED_PIXELS = 280
        const val RETURN_HEAD_WRAP_LEFT = 75
        const val RETURN_HEAD_WRAP_TOP = 108
        const val RETURN_HEAD_WRAP_RIGHT = 128
        const val RETURN_HEAD_WRAP_BOTTOM = 136
        const val FRAME_SIZE = 256
    }
}
