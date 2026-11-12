package com.urkaaaz.tutorial

import android.graphics.RectF
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.application.tutorial.TutorialEffect
import com.urkaaaz.application.tutorial.TutorialScreen

interface TutorialScreenContent : ScreenContent {
    val tutorialScreen: TutorialScreen
    val registeredTutorialTargetIds: Set<String>
        get() = emptySet()
    val isTutorialDemoMode: Boolean
        get() = false
    val tutorialMatchId: String?
        get() = null
    val tutorialMatchGeneration: Long
        get() = 0L
    val tutorialTargetsReady: Boolean
        get() = true

    fun tutorialTargetBounds(
        targetId: String,
        targetInstanceId: String?,
    ): RectF?

    fun setTutorialUiState(state: com.urkaaaz.application.tutorial.TutorialUiState?) = Unit

    fun startBattleFromTutorial(): Boolean = false

    fun focusTutorialTarget(
        targetId: String,
        targetInstanceId: String?,
    ): Boolean = false

    fun dispatchTutorialEffect(effect: TutorialEffect): Boolean = false
}

object TutorialTargetRegistration {
    val idsByScreen: Map<TutorialScreen, Set<String>> = mapOf(
        TutorialScreen.DASHBOARD to setOf(
            "dashboard.campaign",
            "dashboard.shop",
            "dashboard.language",
            "dashboard.reset",
            "dashboard.tutorials",
        ),
        TutorialScreen.CAMPAIGN to setOf(
            "campaign.map",
            "campaign.level_marker",
            "campaign.start",
            "campaign.chapter_navigation",
        ),
        TutorialScreen.LOADOUT to setOf(
            "loadout.units",
            "loadout.ammunition",
            "loadout.spells",
            "loadout.start",
        ),
        TutorialScreen.MATCH to setOf(
            "match.enemy_fortress",
            "match.player_catapult",
            "match.aim_guide",
            "match.wind",
            "match.ammunition",
            "match.health",
            "match.timer",
            "match.units",
            "match.supply",
            "match.send_wave",
            "match.mana",
            "match.spells",
            "match.boss",
        ),
    )

    fun idsFor(screen: TutorialScreen): Set<String> =
        idsByScreen[screen].orEmpty()
}

class TutorialTargetRegistry {
    private var activeSource: TutorialScreenContent? = null

    fun register(source: TutorialScreenContent) {
        activeSource = source
    }

    fun clear(source: TutorialScreenContent? = null) {
        if (source == null || activeSource === source) activeSource = null
    }

    fun resolve(
        targetId: String,
        targetInstanceId: String?,
    ): RectF? = activeSource?.tutorialTargetBounds(targetId, targetInstanceId)

    fun focus(
        targetId: String,
        targetInstanceId: String?,
    ): Boolean = activeSource?.focusTutorialTarget(targetId, targetInstanceId) == true

    fun dispatch(effect: TutorialEffect): Boolean =
        activeSource?.dispatchTutorialEffect(effect) == true

    fun activeScreen(): TutorialScreen? = activeSource?.tutorialScreen

    fun registeredTargetIds(): Set<String> =
        activeSource?.registeredTutorialTargetIds.orEmpty()
}

fun android.view.View.visibleBoundsIn(root: android.view.View): RectF? {
    if (!isShown || width <= 0 || height <= 0) return null
    val visibleGlobalBounds = android.graphics.Rect()
    if (!getGlobalVisibleRect(visibleGlobalBounds)) return null
    val rootLocation = IntArray(2)
    val viewLocation = IntArray(2)
    root.getLocationOnScreen(rootLocation)
    getLocationOnScreen(viewLocation)
    val fullBounds = RectF(
        (viewLocation[0] - rootLocation[0]).toFloat(),
        (viewLocation[1] - rootLocation[1]).toFloat(),
        (viewLocation[0] - rootLocation[0] + width).toFloat(),
        (viewLocation[1] - rootLocation[1] + height).toFloat(),
    )
    visibleGlobalBounds.offset(-rootLocation[0], -rootLocation[1])
    return fullBounds.takeIf {
        visibleGlobalBounds.left == fullBounds.left.toInt() &&
            visibleGlobalBounds.top == fullBounds.top.toInt() &&
            visibleGlobalBounds.right == fullBounds.right.toInt() &&
            visibleGlobalBounds.bottom == fullBounds.bottom.toInt()
    }
}

fun android.view.View.boundsInRoot(bounds: RectF, root: android.view.View): RectF {
    val viewLocation = IntArray(2)
    val rootLocation = IntArray(2)
    getLocationOnScreen(viewLocation)
    root.getLocationOnScreen(rootLocation)
    return RectF(bounds).apply {
        offset(
            (viewLocation[0] - rootLocation[0]).toFloat(),
            (viewLocation[1] - rootLocation[1]).toFloat(),
        )
    }
}
