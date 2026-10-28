package com.urkaaaz.ui

import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.contracts.MatchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchResultPopupStateMapperTest {
    @Test
    fun victoryOffersNextLevelOnlyAfterCompletionIsPersisted() {
        val pending = popup(completionRecorded = null)
        val saved = popup(completionRecorded = true)

        assertEquals(
            listOf(MatchResultActionType.NEXT_LEVEL, MatchResultActionType.EXIT_TO_CAMPAIGN),
            pending?.actions?.map { it.type },
        )
        assertFalse(requireNotNull(pending).actions.first().enabled)
        assertTrue(pending.actions.last().enabled)
        assertEquals(MatchResultPopupStatus.SAVING_PROGRESS, pending.status)
        assertEquals(
            listOf(MatchResultActionType.NEXT_LEVEL, MatchResultActionType.EXIT_TO_CAMPAIGN),
            saved?.actions?.map { it.type },
        )
        assertTrue(requireNotNull(saved).actions.first().enabled)
    }

    @Test
    fun finalCampaignVictoryOffersOnlyGoldCampaignReturn() {
        val state = popup(
            level = CampaignLevelDefinition.MAX_LEVELS,
            completionRecorded = true,
        )

        assertEquals(
            listOf(MatchResultActionType.EXIT_TO_CAMPAIGN),
            state?.actions?.map { it.type },
        )
        assertEquals(MatchResultButtonStyle.GOLD, state?.actions?.single()?.style)
    }

    @Test
    fun failedCompletionOffersRetryAndCampaignReturnWithoutNextLevel() {
        val state = popup(completionRecorded = false)

        assertEquals(
            listOf(MatchResultActionType.RETRY_SAVE, MatchResultActionType.EXIT_TO_CAMPAIGN),
            state?.actions?.map { it.type },
        )
        assertEquals(MatchResultPopupStatus.COMPLETION_FAILED, state?.status)
    }

    @Test
    fun retrySaveActionInvokesPersistenceCallbackWithoutRestartingMatch() {
        val called = mutableListOf<String>()
        dispatchMatchResultAction(
            action = MatchResultActionType.RETRY_SAVE,
            onRetry = { called += "restart" },
            onRetrySave = { called += "save" },
            onNextLevel = {},
            onCampaignReturn = {},
            onDashboardReturn = {},
        )

        assertEquals(listOf("save"), called)
    }

    @Test
    fun defeatOffersRetryAndCampaignReturnWithoutCampaignProgression() {
        val state = popup(outcome = MatchOutcome.RED_WIN, completionRecorded = false)

        assertEquals(MatchResultKind.DEFEAT, state?.result)
        assertEquals(
            listOf(MatchResultActionType.RETRY, MatchResultActionType.EXIT_TO_CAMPAIGN),
            state?.actions?.map { it.type },
        )
        assertNull(state?.status)
    }

    @Test
    fun surrenderUsesTheStandardDefeatResult() {
        val state = popup(outcome = MatchOutcome.SURRENDER)

        assertEquals(MatchResultKind.DEFEAT, state?.result)
        assertEquals(
            listOf(MatchResultActionType.RETRY, MatchResultActionType.EXIT_TO_CAMPAIGN),
            state?.actions?.map { it.type },
        )
    }

    @Test
    fun demoOffersRetryAndDashboardReturnForAnyOutcome() {
        val state = popup(demoMode = true)

        assertEquals(
            listOf(MatchResultActionType.RETRY, MatchResultActionType.BACK_TO_DASHBOARD),
            state?.actions?.map { it.type },
        )
    }

    @Test
    fun unfinishedMatchDoesNotShowResultPopup() {
        assertNull(popup(isFinished = false))
    }

    @Test
    fun resultContextUsesTheCurrentLevelAndBiome() {
        val state = popup(biome = "GREEN_FRONTIER")

        assertEquals(4, state?.campaignLevel)
        assertEquals("GREEN FRONTIER", state?.biomeName)
    }

    @Test
    fun popupActionsInvokeTheirCorrespondingCallbacks() {
        val called = mutableListOf<String>()
        val callbacks = { action: MatchResultActionType ->
            dispatchMatchResultAction(
                action = action,
                onRetry = { called += "retry" },
                onNextLevel = { called += "next" },
                onCampaignReturn = { called += "campaign" },
                onDashboardReturn = { called += "dashboard" },
            )
        }

        callbacks(MatchResultActionType.RETRY)
        callbacks(MatchResultActionType.NEXT_LEVEL)
        callbacks(MatchResultActionType.EXIT_TO_CAMPAIGN)
        callbacks(MatchResultActionType.BACK_TO_DASHBOARD)

        assertEquals(listOf("retry", "next", "campaign", "dashboard"), called)
    }

    private fun popup(
        outcome: MatchOutcome = MatchOutcome.BLUE_WIN,
        level: Int = 4,
        biome: String = "GREEN_FRONTIER",
        demoMode: Boolean = false,
        completionRecorded: Boolean? = true,
        isFinished: Boolean = true,
    ) = MatchResultPopupStateMapper.map(
        isFinished = isFinished,
        outcome = outcome,
        campaignLevel = level,
        biomeName = biome,
        demoMode = demoMode,
        campaignCompletionRecorded = completionRecorded,
    )
}
