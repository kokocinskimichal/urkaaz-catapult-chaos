package com.urkaaaz.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DemolisherDeathAnimationPredictorTest {
    @Test
    fun startsPreImpactAnimationWhenEstimatedContactIsWithinDiveDuration() {
        val predictor = DemolisherDeathAnimationPredictor()
        val enemy = unit("enemy", "RED", "DEFENDER", x = 400f)

        assertEquals(
            emptyMap<String, String>(),
            predictor.update(listOf(unit("demolisher"), enemy), nowMilliseconds = 0L),
        )

        for (step in 1..12) {
            val prediction = predictor.update(
                listOf(unit("demolisher", x = step * 20f), enemy),
                nowMilliseconds = step * 200L,
            )
            assertEquals(emptyMap<String, String>(), prediction)
        }

        assertEquals(
            mapOf("demolisher" to "enemy"),
            predictor.update(
                listOf(unit("demolisher", x = 260f), enemy),
                nowMilliseconds = 2_600L,
            ),
        )
    }

    @Test
    fun cancelsPreImpactAnimationWhenUnitsStopClosing() {
        val predictor = DemolisherDeathAnimationPredictor()
        val enemy = unit("enemy", "RED", "DEFENDER", x = 400f)
        startPrediction(predictor, enemy)

        assertEquals(
            emptyMap<String, String>(),
            predictor.update(
                listOf(unit("demolisher", x = 260f), enemy.copy(x = 450f)),
                nowMilliseconds = 2_800L,
            ),
        )
    }

    @Test
    fun holdsPredictionAtContactUntilTheAnimationCanFollowSimulationDeath() {
        val predictor = DemolisherDeathAnimationPredictor()
        val enemy = unit("enemy", "RED", "DEFENDER", x = 400f)
        startPrediction(predictor, enemy)

        val atContact = listOf(unit("demolisher", x = 384f), enemy)
        assertEquals(
            mapOf("demolisher" to "enemy"),
            predictor.update(atContact, nowMilliseconds = 2_800L),
        )
        assertEquals(
            mapOf("demolisher" to "enemy"),
            predictor.update(atContact, nowMilliseconds = 3_000L),
        )
    }

    private fun startPrediction(
        predictor: DemolisherDeathAnimationPredictor,
        enemy: UnitRenderState,
    ) {
        predictor.update(listOf(unit("demolisher"), enemy), nowMilliseconds = 0L)
        for (step in 1..13) {
            predictor.update(
                listOf(unit("demolisher", x = step * 20f), enemy),
                nowMilliseconds = step * 200L,
            )
        }
    }

    private fun unit(
        entityId: String,
        teamLabel: String = "BLUE",
        unitType: String = "DEMOLISHER",
        x: Float = 0f,
    ) = UnitRenderState(
        entityId = entityId,
        teamLabel = teamLabel,
        unitType = unitType,
        x = x,
        y = 0f,
        health = 100,
        maxHealth = 100,
        moving = true,
        attackType = "MELEE",
        actionState = "SEEKING_TARGET",
        targetId = null,
        attackGroupId = null,
        attackCycleId = 0,
        attackProgress = 0f,
        facingDirection = if (teamLabel == "BLUE") 1f else -1f,
        sapperHasBomb = false,
    )
}
