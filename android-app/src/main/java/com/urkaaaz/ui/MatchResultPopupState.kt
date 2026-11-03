package com.urkaaaz.ui

import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.application.reward.LevelRewardResult

internal enum class MatchResultKind {
    VICTORY,
    DEFEAT,
    DRAW,
}

internal enum class MatchResultPopupStatus {
    SAVING_PROGRESS,
    COMPLETION_FAILED,
    SAVING_STOCK,
    STOCK_SETTLEMENT_FAILED,
}

internal enum class MatchStockSettlementUiStatus {
    NOT_REQUIRED,
    PENDING,
    SETTLED,
    FAILED,
}

internal enum class MatchResultActionType {
    NEXT_LEVEL,
    EXIT_TO_CAMPAIGN,
    RETRY,
    RETRY_SAVE,
    RETRY_STOCK_SETTLEMENT,
    BACK_TO_DASHBOARD,
}

internal enum class MatchResultButtonStyle {
    GOLD,
    RED,
    DARK,
}

internal data class MatchResultPopupAction(
    val type: MatchResultActionType,
    val style: MatchResultButtonStyle,
    val enabled: Boolean = true,
)

internal data class MatchResultPopupState(
    val result: MatchResultKind,
    val campaignLevel: Int,
    val biomeName: String,
    val status: MatchResultPopupStatus?,
    val actions: List<MatchResultPopupAction>,
    val reward: LevelRewardResult? = null,
)

internal object MatchResultPopupStateMapper {
    fun map(
        isFinished: Boolean,
        bossDeathPresentationPending: Boolean = false,
        outcome: MatchOutcome?,
        campaignLevel: Int,
        biomeName: String,
        demoMode: Boolean,
        campaignCompletionRecorded: Boolean?,
        reward: LevelRewardResult? = null,
        stockSettlementStatus: MatchStockSettlementUiStatus =
            MatchStockSettlementUiStatus.NOT_REQUIRED,
    ): MatchResultPopupState? {
        if (!isFinished || bossDeathPresentationPending || outcome == null) return null

        val result = when (outcome) {
            MatchOutcome.BLUE_WIN -> MatchResultKind.VICTORY
            MatchOutcome.RED_WIN, MatchOutcome.SURRENDER -> MatchResultKind.DEFEAT
            MatchOutcome.DRAW -> MatchResultKind.DRAW
        }
        val normalizedBiomeName = biomeName.replace('_', ' ')
        if (stockSettlementStatus == MatchStockSettlementUiStatus.PENDING) {
            return MatchResultPopupState(
                result = result,
                campaignLevel = campaignLevel,
                biomeName = normalizedBiomeName,
                status = MatchResultPopupStatus.SAVING_STOCK,
                actions = emptyList(),
                reward = reward,
            )
        }
        if (stockSettlementStatus == MatchStockSettlementUiStatus.FAILED) {
            return MatchResultPopupState(
                result = result,
                campaignLevel = campaignLevel,
                biomeName = normalizedBiomeName,
                status = MatchResultPopupStatus.STOCK_SETTLEMENT_FAILED,
                actions = listOf(
                    MatchResultPopupAction(
                        MatchResultActionType.RETRY_STOCK_SETTLEMENT,
                        MatchResultButtonStyle.RED,
                    ),
                ),
                reward = reward,
            )
        }

        val status: MatchResultPopupStatus?
        val actions: List<MatchResultPopupAction>
        when {
            demoMode -> {
                status = null
                actions = listOf(
                    MatchResultPopupAction(
                        MatchResultActionType.RETRY,
                        MatchResultButtonStyle.RED,
                    ),
                    MatchResultPopupAction(
                        MatchResultActionType.BACK_TO_DASHBOARD,
                        MatchResultButtonStyle.DARK,
                    ),
                )
            }
            result != MatchResultKind.VICTORY -> {
                status = null
                actions = listOf(
                    MatchResultPopupAction(MatchResultActionType.RETRY, MatchResultButtonStyle.RED),
                    MatchResultPopupAction(
                        MatchResultActionType.EXIT_TO_CAMPAIGN,
                        MatchResultButtonStyle.DARK,
                    ),
                )
            }
            campaignCompletionRecorded == false -> {
                status = MatchResultPopupStatus.COMPLETION_FAILED
                actions = listOf(
                    MatchResultPopupAction(
                        MatchResultActionType.RETRY_SAVE,
                        MatchResultButtonStyle.RED,
                    ),
                    MatchResultPopupAction(
                        MatchResultActionType.EXIT_TO_CAMPAIGN,
                        MatchResultButtonStyle.DARK,
                    ),
                )
            }
            campaignLevel >= CampaignLevelDefinition.LAST_COMPLETE_CAMPAIGN_LEVEL -> {
                status = if (campaignCompletionRecorded == null) {
                    MatchResultPopupStatus.SAVING_PROGRESS
                } else {
                    null
                }
                actions = listOf(
                    MatchResultPopupAction(
                        MatchResultActionType.EXIT_TO_CAMPAIGN,
                        MatchResultButtonStyle.GOLD,
                    ),
                )
            }
            campaignCompletionRecorded == true -> {
                status = null
                actions = listOf(
                    MatchResultPopupAction(
                        MatchResultActionType.NEXT_LEVEL,
                        MatchResultButtonStyle.GOLD,
                    ),
                    MatchResultPopupAction(
                        MatchResultActionType.EXIT_TO_CAMPAIGN,
                        MatchResultButtonStyle.DARK,
                    ),
                )
            }
            else -> {
                status = MatchResultPopupStatus.SAVING_PROGRESS
                actions = listOf(
                    MatchResultPopupAction(
                        MatchResultActionType.NEXT_LEVEL,
                        MatchResultButtonStyle.GOLD,
                        enabled = false,
                    ),
                    MatchResultPopupAction(
                        MatchResultActionType.EXIT_TO_CAMPAIGN,
                        MatchResultButtonStyle.DARK,
                    ),
                )
            }
        }

        return MatchResultPopupState(
            result = result,
            campaignLevel = campaignLevel,
            biomeName = normalizedBiomeName,
            status = status,
            actions = actions,
            reward = reward,
        )
    }
}

internal fun dispatchMatchResultAction(
    action: MatchResultActionType,
    onRetry: () -> Unit,
    onRetrySave: () -> Unit = onRetry,
    onRetryStockSettlement: () -> Unit = onRetry,
    onNextLevel: () -> Unit,
    onCampaignReturn: () -> Unit,
    onDashboardReturn: () -> Unit,
) {
    when (action) {
        MatchResultActionType.NEXT_LEVEL -> onNextLevel()
        MatchResultActionType.EXIT_TO_CAMPAIGN -> onCampaignReturn()
        MatchResultActionType.RETRY -> onRetry()
        MatchResultActionType.RETRY_SAVE -> onRetrySave()
        MatchResultActionType.RETRY_STOCK_SETTLEMENT -> onRetryStockSettlement()
        MatchResultActionType.BACK_TO_DASHBOARD -> onDashboardReturn()
    }
}
