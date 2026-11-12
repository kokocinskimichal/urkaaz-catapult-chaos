package com.urkaaaz.application.tutorial

import com.urkaaaz.application.language.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TutorialEngineTest {
    private val context = TutorialContext(
        isDemo = false,
        language = AppLanguage.PL,
        matchId = "match-1",
        matchGeneration = 1L,
    )

    @Test
    fun bundledCatalogLoadsAndContainsOnlyTheApprovedMatchBasicsTutorial() {
        val catalog = assertIs<TutorialCatalogRead.Available>(
            TutorialCatalogRepository.bundled().load(),
        ).catalog

        assertEquals(1, catalog.schemaVersion)
        assertEquals("tutorial-1", catalog.revision)
        assertEquals(setOf("match.basics"), catalog.tutorials.map { it.tutorialId }.toSet())
        assertEquals(
            listOf(
                "objective",
                "catapult",
                "wind",
                "aim",
                "fire-practice",
                "ammunition",
                "health",
                "timer",
            ),
            catalog.tutorial("match.basics")?.steps?.map { it.stepId },
        )
    }

    @Test
    fun demoSignalsAreIgnoredWithoutChangingTerminalProgress() {
        val engine = TutorialEngine(bundledCatalog())

        val result = engine.handle(
            TutorialSignal.ScreenEntered(
                TutorialScreen.DASHBOARD,
                context.copy(isDemo = true),
            ),
        )

        assertNull(result.uiState)
        assertTrue(result.events.isEmpty())
        assertTrue(engine.terminalStatuses().isEmpty())
    }

    @Test
    fun matchBasicsExplainsWindBeforeAimAndCountsOnlyAcceptedFireActions() {
        val engine = TutorialEngine(bundledCatalog())
        val update = engine.handle(
            TutorialSignal.MatchStarted(
                scenarioFacts = TutorialScenarioFacts(
                    campaignLevel = 1,
                    availableMechanicIds = setOf("unit.defender", "ammunition.rock"),
                    availableUnits = setOf("DEFENDER"),
                    availableAmmunition = setOf("ROCK", "SIEGE_BOMB"),
                    availableSpells = setOf("FORTRESS_SHIELD"),
                    bossPresent = false,
                ),
                context = context,
            ),
        )

        assertEquals("objective", update.uiState?.stepId)
        assertTrue(update.effects.contains(TutorialEffect.SuspendAi))
        repeat(3) {
            val next = engine.handle(
                TutorialSignal.UserAction(TutorialUserAction.Next, context),
            )
            assertEquals(listOf("catapult", "wind", "aim")[it], next.uiState?.stepId)
        }
        assertEquals(
            "fire-practice",
            engine.handle(TutorialSignal.UserAction(TutorialUserAction.Next, context))
                .uiState?.stepId,
        )

        repeat(2) {
            val progress = engine.handle(
                TutorialSignal.UserAction(
                    TutorialUserAction.PlayerActionPerformed(TutorialPlayerActionKind.FIRE),
                    context,
                ),
            )
            assertEquals("fire-practice", progress.uiState?.stepId)
            assertNull(progress.terminalStatus)
        }
        val completedPractice = engine.handle(
            TutorialSignal.UserAction(
                TutorialUserAction.PlayerActionPerformed(TutorialPlayerActionKind.FIRE),
                context,
            ),
        )

        assertEquals("ammunition", completedPractice.uiState?.stepId)
        assertEquals("match.basics", completedPractice.events
            .filterIsInstance<TutorialEvent.StepCompleted>()
            .single()
            .tutorialId)
    }

    @Test
    fun eventTargetInstanceIsForwardedAndNeverReplacedByAnotherInstance() {
        val catalog = catalog(
            targets = listOf(
                target("battlefield.buried_treasure", TutorialScreen.MATCH, TutorialTargetKind.BATTLEFIELD),
            ),
            tutorials = listOf(
                tutorial(
                    id = "treasure.intro",
                    trigger = TutorialTrigger.MatchEventObserved("treasure.revealed"),
                    targetId = "battlefield.buried_treasure",
                ),
            ),
        )
        val engine = TutorialEngine(catalog)

        val update = engine.handle(
            TutorialSignal.MatchEventObserved(
                eventKind = "treasure.revealed",
                targetInstanceId = "treasure-42",
                context = context,
            ),
        )

        assertEquals("battlefield.buried_treasure", update.uiState?.targetId)
        assertEquals("treasure-42", update.uiState?.targetInstanceId)
        assertTrue(update.effects.contains(
            TutorialEffect.FocusTarget("battlefield.buried_treasure", "treasure-42"),
        ))
    }

    @Test
    fun requiresAndPriorityControlActivationAndSkipIsTerminal() {
        val catalog = catalog(
            targets = listOf(target("dashboard.campaign", TutorialScreen.DASHBOARD)),
            tutorials = listOf(
                tutorial(
                    id = "intro.first",
                    trigger = TutorialTrigger.ScreenEntered(TutorialScreen.DASHBOARD),
                    targetId = "dashboard.campaign",
                    priority = 10,
                ),
                tutorial(
                    id = "intro.second",
                    trigger = TutorialTrigger.ScreenEntered(TutorialScreen.DASHBOARD),
                    targetId = "dashboard.campaign",
                    priority = 1,
                    requires = setOf("intro.first"),
                ),
            ),
        )
        val engine = TutorialEngine(catalog)
        val start = engine.handle(
            TutorialSignal.ScreenEntered(TutorialScreen.DASHBOARD, context),
        )

        assertEquals("intro.first", start.uiState?.tutorialId)
        val next = engine.handle(TutorialSignal.UserAction(TutorialUserAction.Next, context))

        assertEquals("intro.second", next.uiState?.tutorialId)
        assertEquals("intro.first" to TutorialStatus.COMPLETED, next.terminalStatus)
        val skipped = engine.handle(TutorialSignal.UserAction(TutorialUserAction.Skip, context))
        assertEquals("intro.second" to TutorialStatus.SKIPPED, skipped.terminalStatus)
        assertNull(skipped.uiState)
        assertEquals(
            mapOf(
                "intro.first" to TutorialStatus.COMPLETED,
                "intro.second" to TutorialStatus.SKIPPED,
            ),
            engine.terminalStatuses(),
        )
    }

    @Test
    fun interruptedMatchResumesEffectsWithoutPersistingTerminalStatus() {
        val engine = TutorialEngine(
            catalog = bundledCatalog(),
        )
        engine.handle(
            TutorialSignal.MatchStarted(
                scenarioFacts = scenarioFacts(),
                context = context,
            ),
        )

        val interrupted = engine.handle(
            TutorialSignal.MatchLifecycleEnded(context),
        )

        assertTrue(interrupted.events.contains(TutorialEvent.Interrupted("match.basics")))
        assertTrue(interrupted.effects.contains(TutorialEffect.ResumeSimulation))
        assertTrue(interrupted.effects.contains(TutorialEffect.ResumeAi))
        assertNull(interrupted.terminalStatus)
        assertTrue(engine.terminalStatuses().isEmpty())
    }

    @Test
    fun nextIsRejectedForActionSteps() {
        val engine = TutorialEngine(bundledCatalog())
        engine.handle(
            TutorialSignal.MatchStarted(scenarioFacts(), context),
        )
        repeat(4) {
            engine.handle(TutorialSignal.UserAction(TutorialUserAction.Next, context))
        }

        val rejected = engine.handle(TutorialSignal.UserAction(TutorialUserAction.Next, context))

        assertIs<TutorialEvent.Rejected>(rejected.events.single())
        assertEquals(TutorialRejection.ACTION_NOT_ALLOWED, (rejected.events.single() as TutorialEvent.Rejected).reason)
        assertEquals("fire-practice", rejected.uiState?.stepId)
    }

    @Test
    fun matchEventCountCanCompleteATriggeredStep() {
        val catalog = catalog(
            targets = listOf(target("match.boss", TutorialScreen.MATCH)),
            tutorials = listOf(
                tutorial(
                    id = "boss.intro",
                    trigger = TutorialTrigger.MatchEventObserved("boss.spawned"),
                    targetId = "match.boss",
                    completion = TutorialStepCompletion.MatchEventCount("boss.damaged", 2),
                ),
            ),
        )
        val engine = TutorialEngine(catalog)
        val started = engine.handle(
            TutorialSignal.MatchEventObserved("boss.spawned", context = context),
        )
        assertEquals("boss.intro", started.uiState?.tutorialId)

        engine.handle(TutorialSignal.MatchEventObserved("boss.damaged", context = context))
        val completed = engine.handle(
            TutorialSignal.MatchEventObserved("boss.damaged", context = context),
        )

        assertEquals("boss.intro" to TutorialStatus.COMPLETED, completed.terminalStatus)
        assertNull(completed.uiState)
    }

    @Test
    fun catalogValidationReportsUnknownTargetsAndRequirementCycles() {
        val issues = TutorialCatalogValidator.validate(
            schemaVersion = 1,
            revision = "test-1",
            targets = emptyList(),
            tutorials = listOf(
                tutorial(
                    id = "first",
                    trigger = TutorialTrigger.ScreenEntered(TutorialScreen.DASHBOARD),
                    targetId = "missing.target",
                    requires = setOf("second"),
                ),
                tutorial(
                    id = "second",
                    trigger = TutorialTrigger.ScreenEntered(TutorialScreen.DASHBOARD),
                    targetId = null,
                    requires = setOf("first"),
                ),
            ),
            registeredMechanicIds = emptyList(),
            registeredEventKinds = emptyList(),
            registeredMilestoneIds = emptyList(),
        )

        assertTrue(issues.any { it.path.endsWith("targetId") && "unknown" in it.message })
        assertTrue(issues.any { "cycle" in it.message })
    }

    private fun bundledCatalog(): TutorialCatalog =
        assertIs<TutorialCatalogRead.Available>(TutorialCatalogRepository.bundled().load()).catalog

    private fun scenarioFacts() = TutorialScenarioFacts(
        campaignLevel = 1,
        availableMechanicIds = emptySet(),
        availableUnits = emptySet(),
        availableAmmunition = emptySet(),
        availableSpells = emptySet(),
        bossPresent = false,
    )

    private fun catalog(
        targets: List<TutorialTargetDefinition>,
        tutorials: List<TutorialDefinition>,
    ): TutorialCatalog {
        val eventKinds = tutorials.flatMap { tutorial ->
            buildList {
                (tutorial.trigger as? TutorialTrigger.MatchEventObserved)
                    ?.let { add(it.eventKind) }
                tutorial.steps.forEach { step ->
                    (step.completion as? TutorialStepCompletion.MatchEventCount)
                        ?.let { add(it.eventKind) }
                }
            }
        }.toSet()
        val mechanicIds = tutorials.flatMap { tutorial ->
            buildList {
                addAll(tutorial.introduces)
                (tutorial.trigger as? TutorialTrigger.MatchStarted)
                    ?.requiresMechanicId
                    ?.let(::add)
            }
        }.toSet()
        return TutorialCatalog(
            schemaVersion = 1,
            revision = "test-1",
            targets = targets,
            tutorials = tutorials,
            registeredMechanicIds = mechanicIds.toList(),
            registeredEventKinds = eventKinds.toList(),
        )
    }

    private fun target(
        id: String,
        screen: TutorialScreen,
        kind: TutorialTargetKind = TutorialTargetKind.VIEW,
    ) = TutorialTargetDefinition(id, screen, kind)

    private fun tutorial(
        id: String,
        trigger: TutorialTrigger,
        targetId: String?,
        priority: Int = 0,
        requires: Set<String> = emptySet(),
        completion: TutorialStepCompletion = TutorialStepCompletion.Next,
    ) = TutorialDefinition(
        tutorialId = id,
        scope = if (trigger is TutorialTrigger.ScreenEntered) {
            TutorialScope.SCREEN
        } else {
            TutorialScope.MATCH
        },
        trigger = trigger,
        priority = priority,
        requires = requires,
        steps = listOf(
            TutorialStep(
                stepId = "step",
                targetId = targetId,
                text = mapOf("pl" to "Krok", "en" to "Step", "es" to "Paso"),
                completion = completion,
            ),
        ),
    )
}
