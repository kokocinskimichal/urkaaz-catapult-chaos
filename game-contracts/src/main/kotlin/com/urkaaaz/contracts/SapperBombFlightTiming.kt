package com.urkaaaz.contracts

object SapperBombFlightTiming {
    const val DURATION_MILLISECONDS = 500L
    const val THROW_ANIMATION_FRAME_COUNT = 15
    const val BOMB_RELEASE_FRAME_INDEX = 11

    val BOMB_RELEASE_PROGRESS =
        BOMB_RELEASE_FRAME_INDEX.toFloat() / THROW_ANIMATION_FRAME_COUNT
}
