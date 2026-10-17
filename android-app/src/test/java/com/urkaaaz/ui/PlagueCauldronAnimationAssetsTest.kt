package com.urkaaaz.ui

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlagueCauldronAnimationAssetsTest {
    @Test
    fun impactFramesAreACompleteTransparentSequenceReusedForTheCloud() {
        val drawableDirectory = File("src/main/res/drawable-nodpi")
        assertTrue("Drawable directory is missing", drawableDirectory.isDirectory)
        val files = requireNotNull(drawableDirectory.listFiles()) {
            "Could not list ${drawableDirectory.path}"
        }.filter {
            it.name.startsWith("plague_cauldron_impact_") && it.extension == "png"
        }.sortedBy(File::getName)

        assertEquals(24, files.size)
        files.forEachIndexed { index, file ->
            assertEquals(
                "plague_cauldron_impact_%02d.png".format(index),
                file.name,
            )
            val image = requireNotNull(ImageIO.read(file)) {
                "Impact frame $index is not a PNG"
            }
            assertEquals(768, image.width)
            assertEquals(576, image.height)
            assertTrue("Impact frame $index has no alpha channel", image.colorModel.hasAlpha())
        }
    }
}
