package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("City Runner", appName)
    }

    @Test
    fun `sound manager service binds and triggers city ambient noises and footsteps`() {
        val controller = Robolectric.buildService(SoundManagerService::class.java).create()
        val service = controller.get()
        val binder = service.onBind(Intent()) as SoundManagerService.SoundServiceBinder
        assertNotNull(binder.getService())

        SoundManager.setMuted(false)
        for (city in City.entries) {
            val profile = SoundManager.getProfileForCity(city)
            assertTrue(profile.surfaceName.isNotEmpty())
            assertTrue(profile.ambientSummary.isNotEmpty())

            val leftStep = SoundManager.synthesizeFootstep(city, isLeftFoot = true)
            val rightStep = SoundManager.synthesizeFootstep(city, isLeftFoot = false)
            assertTrue(leftStep.isNotEmpty())
            assertTrue(rightStep.isNotEmpty())

            val traffic = SoundManager.synthesizeTrafficBurst(city)
            val siren = SoundManager.synthesizeSirenBurst(city)
            val crowd = SoundManager.synthesizeCrowdBurst(city)
            assertTrue(traffic.isNotEmpty())
            assertTrue(siren.isNotEmpty())
            assertTrue(crowd.isNotEmpty())

            SoundManager.triggerAmbientCityNoise(city, AmbientNoiseType.TRAFFIC)
            assertEquals(AmbientNoiseType.TRAFFIC, SoundManager.lastTriggeredAmbient.value)

            SoundManager.triggerAmbientCityNoise(city, AmbientNoiseType.SIREN)
            assertEquals(AmbientNoiseType.SIREN, SoundManager.lastTriggeredAmbient.value)

            SoundManager.triggerAmbientCityNoise(city, AmbientNoiseType.CROWD)
            assertEquals(AmbientNoiseType.CROWD, SoundManager.lastTriggeredAmbient.value)
        }

        val viewModel = GameViewModel()
        val stepsBefore = SoundManager.footstepCount.value
        viewModel.startGame(City.PARIS)
        assertEquals(City.PARIS, SoundManager.currentCity.value)

        // Advance several frames so the runner completes multiple strides in Paris
        repeat(30) {
            viewModel.update(16f)
        }
        assertTrue(SoundManager.footstepCount.value > stepsBefore)

        controller.destroy()
        SoundManager.release()
    }

    @Test
    fun `daily challenge system tracks goals and rewards virtual currency`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DailyChallengeManager.init(context)
        DailyChallengeManager.resetForTesting()

        val initialChallenges = DailyChallengeManager.challenges.value
        assertTrue(initialChallenges.any { it.title == "Run 5000 meters in Tokyo" })
        assertTrue(initialChallenges.any { it.title == "Collect 10 Shields" })
        assertEquals(0, DailyChallengeManager.virtualCurrency.value)

        // 1. Progress "Run 5000 meters in Tokyo" -> rewards 350 CR
        DailyChallengeManager.recordMetersInCity(City.TOKYO, 5000)
        val afterTokyoRun = DailyChallengeManager.challenges.value.first { it.id == "run_tokyo_5000" }
        assertTrue(afterTokyoRun.isCompleted)
        assertEquals(350, DailyChallengeManager.virtualCurrency.value)

        // 2. Progress "Collect 10 Shields" -> rewards +300 CR (total 650 CR)
        repeat(10) {
            DailyChallengeManager.onPowerUpCollected("Shield", City.NEW_YORK)
        }
        val afterShields = DailyChallengeManager.challenges.value.first { it.id == "collect_shields_10" }
        assertTrue(afterShields.isCompleted)
        assertEquals(650, DailyChallengeManager.virtualCurrency.value)
    }

    @Test
    fun `cosmetic shop unlocks and equips outfits and 3d runner models using virtual currency`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DailyChallengeManager.init(context)
        DailyChallengeManager.resetForTesting()
        CosmeticShopManager.init(context)
        CosmeticShopManager.resetForTesting()

        assertEquals("metro_sprinter", CosmeticShopManager.equippedCosmetic.value.id)

        // Attempt purchase with 0 CR -> INSUFFICIENT_FUNDS
        val failRes = CosmeticShopManager.unlockOrEquip("tokyo_cyber_ninja")
        assertEquals(CosmeticPurchaseResult.INSUFFICIENT_FUNDS, failRes)

        // Complete "Run 5000 meters in Tokyo" to earn 350 CR
        DailyChallengeManager.recordMetersInCity(City.TOKYO, 5000)
        assertEquals(350, DailyChallengeManager.virtualCurrency.value)

        // Unlock Tokyo Cyber-Ninja (costs 250 CR) -> leaves 100 CR and equips it
        val successRes = CosmeticShopManager.unlockOrEquip("tokyo_cyber_ninja")
        assertEquals(CosmeticPurchaseResult.UNLOCKED_AND_EQUIPPED, successRes)
        assertEquals(100, DailyChallengeManager.virtualCurrency.value)
        assertEquals("tokyo_cyber_ninja", CosmeticShopManager.equippedCosmetic.value.id)
        assertTrue(CosmeticShopManager.cosmetics.value.first { it.id == "tokyo_cyber_ninja" }.isUnlocked)

        // Switch back to Metro Sprinter and then re-equip Tokyo Cyber-Ninja for free
        assertEquals(CosmeticPurchaseResult.EQUIPPED, CosmeticShopManager.unlockOrEquip("metro_sprinter"))
        assertEquals("metro_sprinter", CosmeticShopManager.equippedCosmetic.value.id)
        assertEquals(CosmeticPurchaseResult.EQUIPPED, CosmeticShopManager.unlockOrEquip("tokyo_cyber_ninja"))
        assertEquals("tokyo_cyber_ninja", CosmeticShopManager.equippedCosmetic.value.id)
    }
}
