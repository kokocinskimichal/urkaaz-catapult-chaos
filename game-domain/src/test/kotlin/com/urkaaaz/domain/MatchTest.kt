package com.urkaaaz.domain

import com.urkaaaz.contracts.MatchStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchTest {
    @Test
    fun newMatchIsNotStarted() {
        assertEquals(MatchStatus.NOT_STARTED, Match().status)
    }
}
