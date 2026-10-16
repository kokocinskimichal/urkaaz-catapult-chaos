package com.urkaaaz.ui

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefenderAnimationAssetsTest {
    @Test
    fun generatedDefenderAnimationFramesAreContiguousAndRendererSized() {
        val resourceDirectory = listOf(
            File("src/main/res/drawable-nodpi"),
            File("android-app/src/main/res/drawable-nodpi"),
        ).firstOrNull { it.isDirectory }
        assertNotNull("Drawable resource directory was not found", resourceDirectory)
        val directory = requireNotNull(resourceDirectory)

        listOf(
            Triple("left", "idle", 60),
            Triple("right", "idle", 60),
            Triple("left", "jog", 60),
            Triple("right", "jog", 60),
            Triple("left", "fight", 30),
            Triple("right", "fight", 30),
            Triple("left", "death_anim", 60),
            Triple("right", "death_anim", 60),
        ).forEach { (side, animation, expectedFrameCount) ->
            val frameFiles = directory.listFiles { file ->
                file.name.matches(Regex("defender_${side}_${animation}_\\d{3}\\.png"))
            }.orEmpty()
            assertEquals(
                "$side Defender should have $expectedFrameCount $animation frames",
                expectedFrameCount,
                frameFiles.size,
            )

            (0 until expectedFrameCount).forEach { index ->
                val imageFile = File(
                    directory,
                    "defender_${side}_${animation}_%03d.png".format(index),
                )
                assertTrue("Missing $side $animation frame $index", imageFile.isFile)
                val image = ImageIO.read(imageFile)
                assertNotNull("Invalid $side $animation PNG frame $index", image)
                val expectedDimension = if (animation == "death_anim") 512 else 256
                assertEquals(expectedDimension, image.width)
                assertEquals(expectedDimension, image.height)
                assertTrue(
                    "$side $animation frame $index must preserve alpha",
                    image.colorModel.hasAlpha(),
                )
                assertEquals(
                    "$side $animation frame $index must retain a transparent canvas corner",
                    0,
                    image.getRGB(0, 0) ushr 24,
                )
            }
        }
    }
}
