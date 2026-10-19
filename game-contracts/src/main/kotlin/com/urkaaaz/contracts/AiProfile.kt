package com.urkaaaz.contracts

@JvmInline
value class AiProfileId(val value: String) {
    init {
        require(value.isNotBlank()) { "AI profile ID must not be blank" }
    }

    companion object {
        val CADET = AiProfileId("CADET")
        val VETERAN = AiProfileId("VETERAN")
        val ELITE = AiProfileId("ELITE")
    }
}

data class AiProfileSelection(
    val profileId: AiProfileId = AiProfileId.VETERAN,
    val progressionPercent: Int = 0,
) {
    init {
        require(progressionPercent in 0..100) {
            "AI profile progression must be between 0 and 100"
        }
        require(profileId == AiProfileId.ELITE || progressionPercent == 0) {
            "only the ELITE AI profile can have progression"
        }
    }
}
