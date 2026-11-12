package com.urkaaaz.application.tutorial

import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.campaign.CampaignMilestoneRegistry
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.UnitIds
import java.util.Locale

object TutorialMechanicRegistry {
    fun ids(): Set<String> = buildSet {
        UnitIds.all.forEach { add("unit.${it.lowercase(Locale.ROOT)}") }
        add("unit.send_wave")
        AmmunitionType.entries.forEach {
            add("ammunition.${it.name.lowercase(Locale.ROOT)}")
        }
        SpellType.entries.forEach { add("spell.${it.name.lowercase(Locale.ROOT)}") }
        (1..CampaignLevelDefinition.MAX_LEVELS)
            .mapNotNull(CampaignLevelDefinition::bossProfileIdForLevel)
            .distinct()
            .forEach { add("boss.${it.lowercase(Locale.ROOT)}") }
    }

    fun eventKinds(): Set<String> = TutorialMatchEventMapper.registeredEventKinds

    fun campaignMilestoneIds(): Set<String> =
        (1..CampaignLevelDefinition.MAX_LEVELS)
            .flatMap(CampaignMilestoneRegistry.default()::milestonesForCompletedLevel)
            .mapTo(mutableSetOf()) { it.id }
}

data class TutorialMatchEventObservation(
    val eventKind: String,
    val targetInstanceId: String?,
)

object TutorialMatchEventMapper {
    const val BOSS_SPAWNED = "boss.spawned"
    const val UNIT_DEPLOYED = "unit.deployed"
    const val SPELL_CAST = "spell.cast"
    const val PROJECTILE_FIRED = "projectile.fired"
    const val MATCH_FINISHED = "match.finished"

    val registeredEventKinds: Set<String> = setOf(
        BOSS_SPAWNED,
        UNIT_DEPLOYED,
        SPELL_CAST,
        PROJECTILE_FIRED,
        MATCH_FINISHED,
    )

    fun observe(event: MatchEvent): TutorialMatchEventObservation? =
        when (event) {
            is MatchEvent.BossSpawned ->
                TutorialMatchEventObservation(BOSS_SPAWNED, event.bossId.value)
            is MatchEvent.UnitDeployed ->
                TutorialMatchEventObservation(UNIT_DEPLOYED, event.unitId.value)
            is MatchEvent.SpellCast ->
                TutorialMatchEventObservation(SPELL_CAST, event.targetId?.value)
            is MatchEvent.ProjectileFired ->
                TutorialMatchEventObservation(PROJECTILE_FIRED, event.projectileId.value)
            is MatchEvent.MatchFinished ->
                TutorialMatchEventObservation(MATCH_FINISHED, null)
            else -> null
        }
}

object TutorialCatalogRegistrationValidator {
    fun validate(catalog: TutorialCatalog): List<TutorialCatalogIssue> =
        buildList {
            checkRegistration(
                path = "registeredMechanicIds",
                actual = catalog.registeredMechanicIds.toSet(),
                expected = TutorialMechanicRegistry.ids(),
            )?.let(::add)
            checkRegistration(
                path = "registeredEventKinds",
                actual = catalog.registeredEventKinds.toSet(),
                expected = TutorialMechanicRegistry.eventKinds(),
            )?.let(::add)
            checkRegistration(
                path = "registeredMilestoneIds",
                actual = catalog.registeredMilestoneIds.toSet(),
                expected = TutorialMechanicRegistry.campaignMilestoneIds(),
            )?.let(::add)
        }

    private fun checkRegistration(
        path: String,
        actual: Set<String>,
        expected: Set<String>,
    ): TutorialCatalogIssue? =
        if (actual == expected) {
            null
        } else {
            TutorialCatalogIssue(
                path,
                "must match the IDs registered by the owning gameplay systems",
            )
        }
}
