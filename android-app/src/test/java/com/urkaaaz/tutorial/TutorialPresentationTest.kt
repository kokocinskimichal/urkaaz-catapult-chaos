package com.urkaaaz.tutorial

import android.graphics.RectF
import android.view.View
import com.urkaaaz.application.tutorial.TutorialActionLabel
import com.urkaaaz.application.tutorial.TutorialEffect
import com.urkaaaz.application.tutorial.TutorialPlacementPreference
import com.urkaaaz.application.tutorial.TutorialCatalogRead
import com.urkaaaz.application.tutorial.TutorialCatalogRepository
import com.urkaaaz.application.tutorial.TutorialScreen
import com.urkaaaz.application.tutorial.TutorialUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TutorialPresentationTest {
    @Test
    fun hintPlacementDoesNotOverlapItsTargetAndRespectsViewportBounds() {
        val target = RectF(170f, 120f, 230f, 180f)

        val placement = TutorialHintPlacer.place(
            viewportWidth = 400f,
            viewportHeight = 300f,
            hintWidth = 140f,
            hintHeight = 80f,
            target = target,
            preference = TutorialPlacementPreference.ABOVE,
            gap = 24f,
            margin = 10f,
        )

        assertEquals(RectF(130f, 13f, 270f, 93f), placement?.bounds)
        assertFalse(RectF.intersects(placement?.bounds ?: RectF(), target))
    }

    @Test
    fun hintIsCenteredInTheAvailableDimmedRegionBesideItsTarget() {
        val placement = TutorialHintPlacer.place(
            viewportWidth = 400f,
            viewportHeight = 300f,
            hintWidth = 100f,
            hintHeight = 100f,
            target = RectF(200f, 60f, 280f, 240f),
            preference = TutorialPlacementPreference.LEFT,
            gap = 20f,
            margin = 10f,
        )

        assertEquals(RectF(45f, 100f, 145f, 200f), placement?.bounds)
    }

    @Test
    fun automaticPlacementPrefersCenteredSideRegionBeforeAboveOrBelow() {
        val placement = TutorialHintPlacer.place(
            viewportWidth = 400f,
            viewportHeight = 300f,
            hintWidth = 100f,
            hintHeight = 80f,
            target = RectF(250f, 120f, 300f, 180f),
            preference = TutorialPlacementPreference.AUTO,
            gap = 20f,
            margin = 10f,
        )

        assertEquals(RectF(70f, 110f, 170f, 190f), placement?.bounds)
    }

    @Test
    fun hintPlacementRejectsWhenNoSafeSpaceExists() {
        val placement = TutorialHintPlacer.place(
            viewportWidth = 200f,
            viewportHeight = 160f,
            hintWidth = 180f,
            hintHeight = 140f,
            target = RectF(80f, 60f, 120f, 100f),
            preference = TutorialPlacementPreference.AUTO,
            gap = 12f,
            margin = 10f,
        )

        assertNull(placement)
    }

    @Test
    fun overlayFitsHintBesideItsTargetOnLandscapeViewport() {
        val context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        val viewportWidth = (914f * density).toInt()
        val viewportHeight = (411f * density).toInt()
        var viewportReadyCount = 0
        val overlay = TutorialOverlayView(context, {}, {}) {
            viewportReadyCount += 1
        }
        val state = TutorialUiState(
            tutorialId = "match.basics",
            stepId = "objective",
            text = "Destroy the enemy fortress to win while defending your own.",
            targetId = "match.enemy_fortress",
            targetInstanceId = null,
            placementPreference = TutorialPlacementPreference.AUTO,
            stepIndex = 1,
            stepCount = 8,
            actionLabel = TutorialActionLabel.NEXT,
            skipAvailable = true,
            supplementalContent = null,
            touchAllowedTargetIds = emptySet(),
            lockedTargetIds = emptySet(),
            lockedMechanicIds = emptySet(),
        )
        val target = RectF(
            470f * density,
            140f * density,
            740f * density,
            270f * density,
        )

        assertTrue(overlay.render(state, target))
        assertEquals(0, viewportReadyCount)
        overlay.measure(
            View.MeasureSpec.makeMeasureSpec(viewportWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(viewportHeight, View.MeasureSpec.EXACTLY),
        )
        overlay.layout(0, 0, viewportWidth, viewportHeight)

        assertEquals(1, viewportReadyCount)
        assertTrue(overlay.render(state, target))
    }

    @Test
    fun registryResolvesOnlyTheRequestedDynamicInstance() {
        val expected = RectF(10f, 20f, 40f, 60f)
        val content = TestTutorialContent(expected)
        val registry = TutorialTargetRegistry().apply { register(content) }

        assertEquals(expected, registry.resolve("battlefield.flying_object", "object-2"))
        assertNull(registry.resolve("battlefield.flying_object", "object-1"))
        assertEquals(TutorialScreen.MATCH, registry.activeScreen())
        assertTrue(registry.dispatch(TutorialEffect.SuspendAi))
    }

    @Test
    fun everyBundledTargetHasExactlyOneRegisteredScreenProvider() {
        val catalog = (TutorialCatalogRepository.bundled().load() as TutorialCatalogRead.Available)
            .catalog
        val targetsByScreen = catalog.targets.groupBy { it.screen }

        TutorialTargetRegistration.idsByScreen.forEach { (screen, registeredIds) ->
            assertEquals(
                "target provider coverage for $screen",
                registeredIds,
                targetsByScreen[screen].orEmpty().mapTo(mutableSetOf()) { it.targetId },
            )
        }
        assertEquals(
            TutorialTargetRegistration.idsByScreen.keys,
            targetsByScreen.keys,
        )
    }

    private class TestTutorialContent(
        private val expectedBounds: RectF,
    ) : TutorialScreenContent {
        override val view: View = View(RuntimeEnvironment.getApplication())
        override val tutorialScreen: TutorialScreen = TutorialScreen.MATCH
        override val registeredTutorialTargetIds: Set<String> =
            setOf("battlefield.flying_object")

        override fun tutorialTargetBounds(
            targetId: String,
            targetInstanceId: String?,
        ): RectF? =
            expectedBounds.takeIf {
                targetId == "battlefield.flying_object" && targetInstanceId == "object-2"
            }

        override fun dispatchTutorialEffect(effect: TutorialEffect): Boolean =
            effect == TutorialEffect.SuspendAi
    }
}
