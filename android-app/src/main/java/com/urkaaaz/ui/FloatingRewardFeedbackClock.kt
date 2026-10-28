package com.urkaaaz.ui

internal class FloatingRewardFeedbackClock(
    private val durationMilliseconds: Long = 1_000L,
) {
    private data class ActiveFeedback(
        val feedback: CombatFeedbackRenderState,
        var elapsedMilliseconds: Long = 0L,
    )

    private val activeFeedback = linkedMapOf<String, ActiveFeedback>()
    private val seenFeedbackIds = mutableSetOf<String>()
    private var lastUpdateMilliseconds: Long? = null

    fun update(
        feedback: List<CombatFeedbackRenderState>,
        nowMilliseconds: Long,
        paused: Boolean,
    ): List<CombatFeedbackRenderState> {
        val elapsed = lastUpdateMilliseconds
            ?.let { (nowMilliseconds - it).coerceAtLeast(0L) }
            ?: 0L
        lastUpdateMilliseconds = nowMilliseconds

        if (!paused) {
            activeFeedback.values.forEach { active ->
                active.elapsedMilliseconds += elapsed
            }
        }
        feedback.asSequence()
            .filter { it.kind == CombatFeedbackRenderState.Kind.GOLD_REWARD }
            .filter { seenFeedbackIds.add(it.eventId) }
            .forEach { item ->
                activeFeedback[item.eventId] = ActiveFeedback(item)
            }
        activeFeedback.entries.removeAll { (_, active) ->
            active.elapsedMilliseconds >= durationMilliseconds
        }

        return activeFeedback.values.map { active ->
            active.feedback.copy(
                progress = (
                    active.elapsedMilliseconds.toFloat() / durationMilliseconds
                    ).coerceIn(0f, 1f),
            )
        }
    }

    fun reset() {
        activeFeedback.clear()
        seenFeedbackIds.clear()
        lastUpdateMilliseconds = null
    }
}
