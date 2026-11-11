package com.urkaaaz.persistence

import com.urkaaaz.application.progression.WallClock

object SystemWallClock : WallClock {
    override fun nowEpochMilliseconds(): Long = System.currentTimeMillis()
}
