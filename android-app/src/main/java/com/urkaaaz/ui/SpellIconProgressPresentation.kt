package com.urkaaaz.ui

internal object SpellIconProgressPresentation {
    fun coveredFraction(remainingMilliseconds: Long?, durationMilliseconds: Long?): Float {
        val duration = durationMilliseconds?.takeIf { it > 0L } ?: return 0f
        val remaining = remainingMilliseconds ?: return 0f
        return (1f - remaining.toFloat() / duration).coerceIn(0f, 1f)
    }
}
