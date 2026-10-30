package com.urkaaaz.ui

import com.urkaaaz.android.R
import com.urkaaaz.contracts.BossPhase

data class BossPresentationDefinition(
    val staticSprite: String,
    val nameResource: Int,
    val spritesByPhase: Map<BossPhase, String> = emptyMap(),
) {
    fun spriteFor(phase: BossPhase): String =
        spritesByPhase[phase] ?: staticSprite
}

object BossPresentationCatalog {
    private val definitions = mapOf(
        "ancient_war_oak" to BossPresentationDefinition(
            staticSprite = "anim_boss_ancient_war_oak_idle_000",
            nameResource = R.string.boss_name_ancient_war_oak,
        ),
        "burntwood_warden" to BossPresentationDefinition(
            staticSprite = "anim_boss_burntwood_warden_idle_000",
            nameResource = R.string.boss_name_burntwood_warden,
        ),
        "copperwood_final_boss" to BossPresentationDefinition(
            staticSprite = "anim_boss_copperwood_final_boss_idle_000",
            nameResource = R.string.boss_name_copperwood_final_boss,
        ),
        "copperwood_siege_lord" to BossPresentationDefinition(
            staticSprite = "anim_boss_copperwood_siege_lord_idle_000",
            nameResource = R.string.boss_name_copperwood_siege_lord,
        ),
        "frozen_troll" to BossPresentationDefinition(
            staticSprite = "anim_boss_frozen_troll_idle_000",
            nameResource = R.string.boss_name_frozen_troll,
        ),
        "old_yeti_sovereign" to BossPresentationDefinition(
            staticSprite = "anim_boss_old_yeti_sovereign_idle_000",
            nameResource = R.string.boss_name_old_yeti_sovereign,
        ),
        "scorchbound_king" to BossPresentationDefinition(
            staticSprite = "anim_boss_scorchbound_king_idle_000",
            nameResource = R.string.boss_name_scorchbound_king,
        ),
        "thorncrown" to BossPresentationDefinition(
            staticSprite = "anim_boss_thorncrown_idle_000",
            nameResource = R.string.boss_name_thorncrown,
        ),
    )

    fun definition(presentationKey: String): BossPresentationDefinition =
        definitions[presentationKey]
            ?: error("No boss presentation is registered for '$presentationKey'")
}
