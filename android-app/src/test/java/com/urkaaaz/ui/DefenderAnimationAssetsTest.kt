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
            Triple("blue", "idle", 60),
            Triple("red", "idle", 60),
            Triple("blue", "move", 60),
            Triple("red", "move", 60),
            Triple("blue", "attack", 30),
            Triple("red", "attack", 30),
            Triple("blue", "death", 60),
            Triple("red", "death", 60),
        ).forEach { (team, animation, expectedFrameCount) ->
            val prefix = "anim_character_defender_${animation}_${team}"
            val frameFiles = directory.listFiles { file ->
                file.name.matches(Regex("${prefix}_\\d{3}\\.png"))
            }.orEmpty()
            assertEquals(
                "$team Defender should have $expectedFrameCount $animation frames",
                expectedFrameCount,
                frameFiles.size,
            )

            (0 until expectedFrameCount).forEach { index ->
                val imageFile = File(
                    directory,
                    "${prefix}_%03d.png".format(index),
                )
                assertTrue("Missing $team $animation frame $index", imageFile.isFile)
                val image = ImageIO.read(imageFile)
                assertNotNull("Invalid $team $animation PNG frame $index", image)
                assertEquals(256, image.width)
                assertEquals(256, image.height)
                assertTrue(
                    "$team $animation frame $index must preserve alpha",
                    image.colorModel.hasAlpha(),
                )
                assertEquals(
                    "$team $animation frame $index must retain a transparent canvas corner",
                    0,
                    image.getRGB(0, 0) ushr 24,
                )
            }
        }
    }
}
