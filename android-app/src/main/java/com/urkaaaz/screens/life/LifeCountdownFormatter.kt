package com.urkaaaz.screens.life

object LifeCountdownFormatter {
    fun format(milliseconds: Long): String {
        val nonNegativeMilliseconds = milliseconds.coerceAtLeast(0L)
        val totalSeconds = nonNegativeMilliseconds / 1_000L +
            if (nonNegativeMilliseconds % 1_000L == 0L) 0L else 1L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
