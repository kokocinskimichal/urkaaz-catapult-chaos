package com.urkaaaz.screens.dashboard

import com.urkaaaz.application.progression.DeveloperProgressSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardViewModelTest {
    @Test
    fun initialStateDoesNotPresentExampleValuesAsLoadedProgress() {
        val viewModel = DashboardViewModel()

        assertTrue(viewModel.state.isLoading)
        assertNull(viewModel.state.progressSummary)
        assertFalse(viewModel.state.hasProgressError)
    }

    @Test
    fun successfulProgressLoadPublishesTheSummary() {
        val viewModel = DashboardViewModel()
        val summary = DeveloperProgressSummary(
            currentLevel = 4,
            lives = 3,
            gold = 120,
        )

        val loaded = viewModel.updateProgress(summary)

        assertEquals(summary, loaded.progressSummary)
        assertFalse(loaded.isLoading)
        assertFalse(loaded.hasProgressError)
    }

    @Test
    fun failedProgressLoadDoesNotRetainStaleSummary() {
        val viewModel = DashboardViewModel()
        viewModel.updateProgress(
            DeveloperProgressSummary(
                currentLevel = 4,
                lives = 3,
                gold = 120,
            ),
        )

        val failed = viewModel.failProgressLoad()

        assertNull(failed.progressSummary)
        assertFalse(failed.isLoading)
        assertTrue(failed.hasProgressError)
    }
}
