package com.urkaaaz.simulation

import com.urkaaaz.domain.Match

class MatchSimulation(
    private val match: Match,
) {
    fun start() {
        match.start()
    }
}
