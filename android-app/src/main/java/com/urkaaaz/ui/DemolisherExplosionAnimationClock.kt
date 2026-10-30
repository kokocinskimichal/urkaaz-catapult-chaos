package com.urkaaaz.ui

internal class DemolisherExplosionAnimationClock {
    private data class ActiveExplosion(
        val feedback: CombatFeedbackRenderState,
        var elapsedRenderTicks: Int,
    )

    private val activeExplosions = linkedMapOf<String, ActiveExplosion>()
    private val pendingExplosions = linkedMapOf<String, CombatFeedbackRenderState>()
    private val seenEventIds = mutableSetOf<String>()

    fun update(
        feedback: List<CombatFeedbackRenderState>,
        frameCount: Int,
        paused: Boolean,
        activeDemolisherAttackUnitIds: Set<String> = emptySet(),
    ): List<CombatFeedbackRenderState> {
        if (frameCount <= 0) return feedback
        val startedThisUpdate = mutableSetOf<String>()
        feedback.asSequence()
            .filter(::isDemolisherExplosion)
            .filter { seenEventIds.add(it.eventId) }
            .forEach {
                if (it.sourceUnitId?.let(activeDemolisherAttackUnitIds::contains) == true) {
                    pendingExplosions[it.eventId] = it
                } else {
                    startExplosion(it, startedThisUpdate)
                }
            }

        pendingExplosions.entries.toList().forEach { (eventId, pending) ->
            if (pending.sourceUnitId?.let(activeDemolisherAttackUnitIds::contains) != true) {
                pendingExplosions.remove(eventId)
                startExplosion(pending, startedThisUpdate)
            }
        }

        if (!paused) {
            activeExplosions.forEach { (eventId, active) ->
                if (eventId !in startedThisUpdate) {
                    active.elapsedRenderTicks =
                        (active.elapsedRenderTicks + 1)
                            .coerceAtMost(frameCount * RENDER_TICKS_PER_FRAME)
                }
            }
        }
        activeExplosions.entries.removeAll {
            it.value.elapsedRenderTicks >= frameCount * RENDER_TICKS_PER_FRAME
        }

        val activeFeedback = activeExplosions.values.map { active ->
            active.feedback.copy(
                animationFrameIndex = (active.elapsedRenderTicks / RENDER_TICKS_PER_FRAME)
                    .coerceIn(0, frameCount - 1),
            )
        }
        return feedback.filterNot(::isDemolisherExplosion) + activeFeedback
    }

    fun reset() {
        activeExplosions.clear()
        pendingExplosions.clear()
        seenEventIds.clear()
    }

    private fun startExplosion(
        feedback: CombatFeedbackRenderState,
        startedThisUpdate: MutableSet<String>,
    ) {
        activeExplosions[feedback.eventId] = ActiveExplosion(
            feedback = feedback,
            elapsedRenderTicks = 0,
        )
        startedThisUpdate += feedback.eventId
    }

    private fun isDemolisherExplosion(feedback: CombatFeedbackRenderState): Boolean =
        feedback.kind == CombatFeedbackRenderState.Kind.EXPLOSION &&
            feedback.sourceUnitType == "DEMOLISHER"

    private companion object {
        const val RENDER_TICKS_PER_FRAME = 3
    }
}
