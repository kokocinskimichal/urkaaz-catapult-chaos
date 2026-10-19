package com.urkaaaz.application.progression

import com.urkaaaz.application.campaign.CampaignProgress
import com.urkaaaz.application.campaign.CampaignProgressFailure
import com.urkaaaz.application.campaign.CampaignProgressRepository
import com.urkaaaz.application.campaign.CampaignProgressRepositoryResult
import com.urkaaaz.application.campaign.CampaignSystem
import com.urkaaaz.application.loadout.LoadoutProgressionFailure
import com.urkaaaz.application.loadout.LoadoutProgressionRead
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.loadout.LoadoutPurchaseResult
import com.urkaaaz.application.loadout.LoadoutProgressionPort
import kotlin.test.Test
import kotlin.test.assertEquals

class CampaignAwareLoadoutProgressionPortTest {
    @Test
    fun campaignRewardsAreProjectedWithoutPersistingProfileUnlocks() = runBlocking {
        val profilePort = FakeProfilePort(
            LoadoutProgressionSnapshot(
                unlockedSpellTypes = setOf("FORTRESS_SHIELD"),
                ammunitionStocks = emptyMap(),
                specialSpellStocks = emptyMap(),
                goldBalance = 0,
            ),
        )
        val campaign = CampaignSystem(
            FakeCampaignRepository(
                CampaignProgress(
                    highestUnlockedLevel = 6,
                    completedLevels = setOf(5),
                    appliedMilestoneIds = setOf("campaign_spell_haste"),
                ),
            ),
        )

        val snapshot = (
            CampaignAwareLoadoutProgressionPort(profilePort, campaign).snapshot(6)
                as LoadoutProgressionRead.Available
            ).snapshot

        assertEquals(setOf("FORTRESS_SHIELD", "HASTE"), snapshot.unlockedSpellTypes)
        assertEquals(setOf("HASTE"), snapshot.defaultSpellTypes)
        assertEquals(emptySet(), profilePort.snapshot.defaultSpellTypes)
    }

    @Test
    fun campaignDefaultPreferenceWaitsUntilSpellAvailabilityLevel() = runBlocking {
        val profilePort = FakeProfilePort(
            LoadoutProgressionSnapshot(
                unlockedSpellTypes = setOf("FORTRESS_SHIELD"),
                ammunitionStocks = emptyMap(),
                specialSpellStocks = emptyMap(),
                goldBalance = 0,
            ),
        )
        val campaign = CampaignSystem(
            FakeCampaignRepository(
                CampaignProgress(
                    highestUnlockedLevel = 6,
                    completedLevels = setOf(5),
                    appliedMilestoneIds = setOf("campaign_spell_haste"),
                ),
            ),
        )

        val snapshot = (
            CampaignAwareLoadoutProgressionPort(profilePort, campaign).snapshot(5)
                as LoadoutProgressionRead.Available
            ).snapshot

        assertEquals(setOf("FORTRESS_SHIELD", "HASTE"), snapshot.unlockedSpellTypes)
        assertEquals(emptySet(), snapshot.defaultSpellTypes)
    }

    @Test
    fun lockedLevelAndCampaignStorageFailureRemainExplicit() = runBlocking {
        val profilePort = FakeProfilePort(
            LoadoutProgressionSnapshot(
                unlockedSpellTypes = setOf("FORTRESS_SHIELD"),
                ammunitionStocks = emptyMap(),
                specialSpellStocks = emptyMap(),
                goldBalance = 0,
            ),
        )
        val locked = CampaignAwareLoadoutProgressionPort(
            profilePort,
            CampaignSystem(FakeCampaignRepository(CampaignProgress.INITIAL)),
        )
        assertEquals(
            LoadoutProgressionRead.Blocked(LoadoutProgressionFailure.CAMPAIGN_LEVEL_LOCKED),
            locked.snapshot(2),
        )

        val unavailable = CampaignAwareLoadoutProgressionPort(
            profilePort,
            CampaignSystem(
                FakeCampaignRepository(failure = CampaignProgressFailure.STORAGE_UNAVAILABLE),
            ),
        )
        assertEquals(
            LoadoutProgressionRead.Blocked(LoadoutProgressionFailure.STORAGE_UNAVAILABLE),
            unavailable.snapshot(1),
        )
    }

    private class FakeProfilePort(
        val snapshot: LoadoutProgressionSnapshot,
    ) : LoadoutProgressionPort {
        override suspend fun snapshot(level: Int): LoadoutProgressionRead =
            LoadoutProgressionRead.Available(snapshot)

        override suspend fun purchaseSpell(spellType: String): LoadoutPurchaseResult =
            LoadoutPurchaseResult.Blocked
    }

    private class FakeCampaignRepository(
        private var progress: CampaignProgress = CampaignProgress.INITIAL,
        private val failure: CampaignProgressFailure? = null,
    ) : CampaignProgressRepository {
        override suspend fun loadOrInitialize(): CampaignProgressRepositoryResult =
            failure?.let(CampaignProgressRepositoryResult::Failure)
                ?: CampaignProgressRepositoryResult.Available(progress)

        override suspend fun update(
            transform: (CampaignProgress) -> CampaignProgress,
        ): CampaignProgressRepositoryResult {
            failure?.let { return CampaignProgressRepositoryResult.Failure(it) }
            progress = transform(progress)
            return CampaignProgressRepositoryResult.Available(progress)
        }
    }
}

private fun <T> runBlocking(block: suspend () -> T): T =
    kotlinx.coroutines.runBlocking { block() }
