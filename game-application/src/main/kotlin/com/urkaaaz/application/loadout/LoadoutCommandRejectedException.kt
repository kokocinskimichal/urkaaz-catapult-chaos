package com.urkaaaz.application.loadout

class LoadoutCommandRejectedException(
    val error: LoadoutError,
) : IllegalStateException("match command rejected by loadout: $error")
