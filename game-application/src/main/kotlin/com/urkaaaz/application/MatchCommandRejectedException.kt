package com.urkaaaz.application

import com.urkaaaz.contracts.MatchError

class MatchCommandRejectedException(
    val error: MatchError,
) : IllegalStateException("match command was rejected: $error")
