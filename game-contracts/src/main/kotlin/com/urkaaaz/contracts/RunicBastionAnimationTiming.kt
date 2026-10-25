package com.urkaaaz.contracts

/** Shared timing for the destruction animation and its gameplay movement lock. */
object RunicBastionAnimationTiming {
    const val DESTRUCTION_FRAME_COUNT = 24
    const val DESTRUCTION_FRAME_DURATION_MILLISECONDS = 167L
    const val DESTRUCTION_DURATION_MILLISECONDS =
        DESTRUCTION_FRAME_COUNT * DESTRUCTION_FRAME_DURATION_MILLISECONDS
}
