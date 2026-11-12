package com.urkaaaz.screens.testmode

import org.junit.Assert.assertEquals
import org.junit.Test

class TestLevelPickerCatalogTest {
    @Test
    fun exposesEveryPlayableLevelGroupedByCampaignChapter() {
        val chapters = TestLevelPickerCatalog.chapters()

        assertEquals(6, chapters.size)
        assertEquals((1..54).toList(), chapters.flatMap { chapter ->
            chapter.levels.map(TestLevelOption::level)
        })
        assertEquals(
            listOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 50),
            chapters.flatMap { chapter ->
                chapter.levels.filter(TestLevelOption::isBoss).map(TestLevelOption::level)
            },
        )
        assertEquals("green_frontier", chapters.first().id)
        assertEquals("ironwood", chapters[2].id)
        assertEquals(listOf(51, 52, 53, 54), chapters.last().levels.map(TestLevelOption::level))
    }
}
