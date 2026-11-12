package com.urkaaaz.tutorial

import com.urkaaaz.application.tutorial.MatchTutorialPort
import com.urkaaaz.application.tutorial.TutorialEffect
import com.urkaaaz.ui.MatchViewModel
import java.util.Locale

class MatchTutorialAdapter(
    private val viewModel: MatchViewModel,
    private val onStateChanged: () -> Unit,
) : MatchTutorialPort {
    private data class AiRestriction(
        val unitIds: Set<String>,
        val ammunitionIds: Set<String>,
        val unitAutomationEnabled: Boolean,
    )

    private var originalAiRestriction: AiRestriction? = null

    fun apply(effect: TutorialEffect): Boolean =
        when (effect) {
            TutorialEffect.PauseSimulation -> {
                viewModel.setTutorialSimulationPaused(true)
                onStateChanged()
                true
            }
            TutorialEffect.ResumeSimulation -> {
                viewModel.setTutorialSimulationPaused(false)
                onStateChanged()
                true
            }
            TutorialEffect.SuspendAi -> {
                suspendAi()
                true
            }
            TutorialEffect.ResumeAi -> {
                resumeAi()
                true
            }
            is TutorialEffect.RestrictAiToIntroduced -> {
                restrictAiToIntroducedMechanics(effect.mechanicIds)
                true
            }
            is TutorialEffect.LockTargets -> {
                viewModel.setTutorialLockedMechanics(
                    viewModel.renderState.tutorialLockedMechanicIds + effect.identifiers,
                )
                onStateChanged()
                true
            }
            is TutorialEffect.UnlockTargets -> {
                viewModel.setTutorialLockedMechanics(
                    viewModel.renderState.tutorialLockedMechanicIds - effect.identifiers,
                )
                restoreAiRestriction()
                onStateChanged()
                true
            }
            is TutorialEffect.FocusTarget -> true
        }

    override fun pauseSimulation() {
        viewModel.setTutorialSimulationPaused(true)
        onStateChanged()
    }

    override fun resumeSimulation() {
        viewModel.setTutorialSimulationPaused(false)
        onStateChanged()
    }

    override fun suspendAi() {
        viewModel.setTutorialAiSuspended(true)
    }

    override fun resumeAi() {
        viewModel.setTutorialAiSuspended(false)
    }

    override fun restrictAiToIntroducedMechanics(mechanicIds: Set<String>) {
        if (originalAiRestriction == null) {
            originalAiRestriction = AiRestriction(
                unitIds = viewModel.aiAllowedUnitIds(),
                ammunitionIds = viewModel.aiAllowedAmmunitionIds(),
                unitAutomationEnabled = viewModel.aiUnitAutomationEnabled(),
            )
        }
        val unitIds = mechanicIds
            .filter { it.startsWith(UNIT_MECHANIC_PREFIX) }
            .mapTo(mutableSetOf()) {
                it.removePrefix(UNIT_MECHANIC_PREFIX).uppercase(Locale.ROOT)
            }
            .intersect(viewModel.aiAllowedUnitIds())
        val ammunitionIds = mechanicIds
            .filter { it.startsWith(AMMUNITION_MECHANIC_PREFIX) }
            .mapTo(mutableSetOf()) {
                it.removePrefix(AMMUNITION_MECHANIC_PREFIX).uppercase(Locale.ROOT)
            }
            .intersect(viewModel.aiAllowedAmmunitionIds())
        if (unitIds.isNotEmpty()) {
            viewModel.setAiAllowedUnitIds(unitIds)
            viewModel.setAiUnitAutomationEnabled(true)
        } else {
            viewModel.setAiUnitAutomationEnabled(false)
        }
        viewModel.setAiAllowedAmmunitionIds(ammunitionIds)
    }

    override fun lockTargets(identifiers: Set<String>) {
        viewModel.setTutorialLockedMechanics(
            viewModel.renderState.tutorialLockedMechanicIds + identifiers,
        )
        onStateChanged()
    }

    override fun unlockTargets(identifiers: Set<String>) {
        viewModel.setTutorialLockedMechanics(
            viewModel.renderState.tutorialLockedMechanicIds - identifiers,
        )
        restoreAiRestriction()
        onStateChanged()
    }

    override fun focusTarget(targetId: String, targetInstanceId: String?) = Unit

    private fun restoreAiRestriction() {
        val restriction = originalAiRestriction ?: return
        originalAiRestriction = null
        if (restriction.unitIds.isNotEmpty()) {
            viewModel.setAiAllowedUnitIds(restriction.unitIds)
        }
        viewModel.setAiAllowedAmmunitionIds(restriction.ammunitionIds)
        viewModel.setAiUnitAutomationEnabled(restriction.unitAutomationEnabled)
    }

    companion object {
        private const val UNIT_MECHANIC_PREFIX = "unit."
        private const val AMMUNITION_MECHANIC_PREFIX = "ammunition."
    }
}
