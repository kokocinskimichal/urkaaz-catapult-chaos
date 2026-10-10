package com.urkaaaz.ui

/** User intent understood by the Android presentation layer. */
sealed interface MatchUiAction {
    data object StartMatch : MatchUiAction
    data object FireRock : MatchUiAction
    data object AdvanceSimulation : MatchUiAction
    data object TogglePause : MatchUiAction
    data class Aim(val directionDegrees: Float, val power: Float) : MatchUiAction
    data class SelectAmmo(val ammunitionType: String) : MatchUiAction
    data class DeployUnit(val unitType: String) : MatchUiAction
    data object SendWave : MatchUiAction
}
