package com.urkaaaz.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationAssetNamingTest {
    @Test
    fun animationFramesUseCategoryPrefixAndThreeDigitIndices() {
        val directory = resourceDirectory()
        val frameFiles = requireNotNull(directory.listFiles()) {
            "Could not list ${directory.path}"
        }.filter { it.name.startsWith("anim_") && it.extension == "png" }

        assertTrue("No animation frames were found", frameFiles.isNotEmpty())
        frameFiles.forEach { file ->
            assertTrue(
                "Animation frame '${file.name}' must use the canonical name format",
                CANONICAL_FRAME_NAME.matches(file.name),
            )
        }
    }

    @Test
    fun animationFrameSequencesStartAtZeroAndHaveNoGaps() {
        val directory = resourceDirectory()
        val framesBySequence = requireNotNull(directory.listFiles()) {
            "Could not list ${directory.path}"
        }.mapNotNull { file ->
            val match = CANONICAL_FRAME_NAME.matchEntire(file.name) ?: return@mapNotNull null
            match.groupValues[1] to match.groupValues[2].toInt()
        }.groupBy({ it.first }, { it.second })

        framesBySequence.forEach { (sequence, indices) ->
            assertEquals(
                "$sequence frame indices must be contiguous from zero",
                (0 until indices.size).toList(),
                indices.sorted(),
            )
        }
    }

    private fun resourceDirectory(): File {
        val directory = listOf(
            File("src/main/res/drawable-nodpi"),
            File("android-app/src/main/res/drawable-nodpi"),
        ).firstOrNull { it.isDirectory }
        assertTrue("Drawable resource directory was not found", directory != null)
        return requireNotNull(directory)
    }

    private companion object {
        val CANONICAL_FRAME_NAME =
            Regex("^(anim_(?:character|spell|ammunition|boss)_[a-z0-9_]+)_(\\d{3})\\.png$")
    }
}
