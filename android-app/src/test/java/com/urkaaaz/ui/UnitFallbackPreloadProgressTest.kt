package com.urkaaaz.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitFallbackPreloadProgressTest {
    @Test
    fun matchCanStartOnlyAfterEveryFallbackIsReady() {
        assertFalse(
            UnitFallbackPreloadProgress(
                readyCount = 1,
                totalCount = 2,
                missingAssetKeys = emptyList(),
            ).isReady,
        )
        assertTrue(
            UnitFallbackPreloadProgress(
                readyCount = 2,
                totalCount = 2,
                missingAssetKeys = emptyList(),
            ).isReady,
        )
    }

    @Test
    fun missingFallbackAssetBlocksMatchStartEvenIfOtherAssetsAreReady() {
        assertFalse(
            UnitFallbackPreloadProgress(
                readyCount = 2,
                totalCount = 2,
                missingAssetKeys = listOf("drawable:missing_unit"),
            ).isReady,
        )
    }
}
