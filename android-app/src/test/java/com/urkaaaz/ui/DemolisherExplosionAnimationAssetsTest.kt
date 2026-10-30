package com.urkaaaz.ui

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemolisherExplosionAnimationAssetsTest {
    @Test
    fun effectOnlyFramesAreTransparentAndStayInsideTheCanvas() {
        val directory = resourceDirectory()
        val frames = (0 until FRAME_COUNT).map { index ->
            File(directory, "anim_ammunition_demolisher_explosion_%03d.png".format(index))
        }
        assertTrue("Demolisher explosion frames should exist", frames.all(File::isFile))
        assertEquals(
            FRAME_COUNT,
            directory.listFiles { file ->
                file.name.matches(Regex("anim_ammunition_demolisher_explosion_\\d{3}\\.png"))
            }.orEmpty().size,
        )

        frames.forEachIndexed { index, file ->
            val image = ImageIO.read(file)
            assertNotNull("Invalid Demolisher explosion frame $index", image)
            assertEquals(256, image.width)
            assertEquals(256, image.height)
            assertTrue(image.colorModel.hasAlpha())
            assertEquals(0, image.getRGB(0, 0) ushr 24)
            assertEquals(0, image.getRGB(255, 0) ushr 24)
            assertEquals(0, image.getRGB(0, 255) ushr 24)
            assertEquals(0, image.getRGB(255, 255) ushr 24)
        }
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
        const val FRAME_COUNT = 20
    }
}
