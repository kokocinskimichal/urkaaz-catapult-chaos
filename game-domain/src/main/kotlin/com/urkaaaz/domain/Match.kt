package com.urkaaaz.domain

import com.urkaaaz.contracts.MatchStatus

/** Minimal domain aggregate used by the application shell before full simulation is migrated. */
class Match {
    var status: MatchStatus = MatchStatus.NOT_STARTED
        private set

    fun start() {
        status = MatchStatus.RUNNING
    }
}
