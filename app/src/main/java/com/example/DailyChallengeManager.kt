package com.example

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.floor

enum class DailyChallengeGoalType {
    RUN_DISTANCE_IN_CITY,
    COLLECT_SHIELDS,
    COLLECT_SPEED_BOOSTS,
    CLEAR_OBSTACLES
}

data class DailyChallenge(
    val id: String,
    val title: String,
    val description: String,
    val goalType: DailyChallengeGoalType,
    val targetCity: City? = null,
    val targetAmount: Int,
    val currentAmount: Int = 0,
    val rewardCurrency: Int,
    val isCompleted: Boolean = false,
    val isRewardGranted: Boolean = false
) {
    val progressFraction: Float
        get() = if (targetAmount <= 0) 1f else (currentAmount.toFloat() / targetAmount.toFloat()).coerceIn(0f, 1f)
}

data class ChallengeCompletionBanner(
    val challengeId: String,
    val title: String,
    val rewardCurrency: Int,
    val timestampMs: Long = System.currentTimeMillis()
)

object DailyChallengeManager {
    private const val PREFS_NAME = "city_runner_daily_challenges"
    private const val KEY_CURRENCY = "virtual_currency_balance"
    private const val KEY_ROTATION_INDEX = "challenge_rotation_index"

    // 1 internal distance unit = 25 meters (so a 200-unit city run = 5000 meters)
    const val METERS_PER_DISTANCE_UNIT = 25f

    private var prefs: SharedPreferences? = null
    private var fractionalMetersByCity = HashMap<City, Float>()
    private var rotationSetIndex = 0

    private val _virtualCurrency = MutableStateFlow(0)
    val virtualCurrency: StateFlow<Int> = _virtualCurrency.asStateFlow()

    private val _challenges = MutableStateFlow<List<DailyChallenge>>(createChallengeBatch(0))
    val challenges: StateFlow<List<DailyChallenge>> = _challenges.asStateFlow()

    private val _recentCompletionBanner = MutableStateFlow<ChallengeCompletionBanner?>(null)
    val recentCompletionBanner: StateFlow<ChallengeCompletionBanner?> = _recentCompletionBanner.asStateFlow()

    fun init(context: Context? = null) {
        if (context != null) {
            try {
                val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs = sp
                _virtualCurrency.value = sp.getInt(KEY_CURRENCY, 0)
                rotationSetIndex = sp.getInt(KEY_ROTATION_INDEX, 0)
                val baseBatch = createChallengeBatch(rotationSetIndex)
                val loaded = baseBatch.map { ch ->
                    val savedProgress = sp.getInt("prog_${ch.id}", 0)
                    val savedRewarded = sp.getBoolean("rew_${ch.id}", false)
                    val completed = savedProgress >= ch.targetAmount || savedRewarded
                    ch.copy(
                        currentAmount = savedProgress.coerceAtMost(ch.targetAmount),
                        isCompleted = completed,
                        isRewardGranted = savedRewarded
                    )
                }
                _challenges.value = loaded
            } catch (_: Exception) {
                if (_challenges.value.isEmpty()) {
                    _challenges.value = createChallengeBatch(0)
                }
            }
        } else if (_challenges.value.isEmpty()) {
            _challenges.value = createChallengeBatch(0)
        }
    }

    /**
     * Resets challenges and currency to a clean initial state (useful for testing or fresh rotation).
     */
    fun resetForTesting() {
        fractionalMetersByCity.clear()
        rotationSetIndex = 0
        _virtualCurrency.value = 0
        _recentCompletionBanner.value = null
        _challenges.value = createChallengeBatch(0)
        prefs?.edit()?.clear()?.apply()
    }

    /**
     * Loads the next set of daily challenges when the player refreshes their challenge board.
     */
    fun rotateNewChallengeSet() {
        fractionalMetersByCity.clear()
        rotationSetIndex = (rotationSetIndex + 1) % 3
        val newBatch = createChallengeBatch(rotationSetIndex)
        _challenges.value = newBatch
        prefs?.edit()?.apply {
            putInt(KEY_ROTATION_INDEX, rotationSetIndex)
            for (ch in newBatch) {
                putInt("prog_${ch.id}", 0)
                putBoolean("rew_${ch.id}", false)
            }
            apply()
        }
    }

    private fun createChallengeBatch(batchIndex: Int): List<DailyChallenge> {
        return when (batchIndex % 3) {
            0 -> listOf(
                DailyChallenge(
                    id = "run_tokyo_5000",
                    title = "Run 5000 meters in Tokyo",
                    description = "Sprint through the neon streets of Tokyo for 5,000 meters.",
                    goalType = DailyChallengeGoalType.RUN_DISTANCE_IN_CITY,
                    targetCity = City.TOKYO,
                    targetAmount = 5000,
                    rewardCurrency = 350
                ),
                DailyChallenge(
                    id = "collect_shields_10",
                    title = "Collect 10 Shields",
                    description = "Pick up 10 Aegis Shield power-ups across any city run.",
                    goalType = DailyChallengeGoalType.COLLECT_SHIELDS,
                    targetCity = null,
                    targetAmount = 10,
                    rewardCurrency = 300
                ),
                DailyChallenge(
                    id = "collect_speed_boosts_5",
                    title = "Collect 5 Speed Boosts",
                    description = "Ignite 5 Speed Boost power-ups during your missions.",
                    goalType = DailyChallengeGoalType.COLLECT_SPEED_BOOSTS,
                    targetCity = null,
                    targetAmount = 5,
                    rewardCurrency = 250
                )
            )
            1 -> listOf(
                DailyChallenge(
                    id = "run_paris_4000",
                    title = "Run 4000 meters in Paris",
                    description = "Course along the Parisian boulevards for 4,000 meters.",
                    goalType = DailyChallengeGoalType.RUN_DISTANCE_IN_CITY,
                    targetCity = City.PARIS,
                    targetAmount = 4000,
                    rewardCurrency = 300
                ),
                DailyChallenge(
                    id = "clear_obstacles_25",
                    title = "Clear 25 Obstacles",
                    description = "Dodge, jump, slide, or smash past 25 street obstacles.",
                    goalType = DailyChallengeGoalType.CLEAR_OBSTACLES,
                    targetCity = null,
                    targetAmount = 25,
                    rewardCurrency = 220
                ),
                DailyChallenge(
                    id = "collect_shields_10_b2",
                    title = "Collect 10 Shields",
                    description = "Pick up 10 Aegis Shield power-ups across any city run.",
                    goalType = DailyChallengeGoalType.COLLECT_SHIELDS,
                    targetCity = null,
                    targetAmount = 10,
                    rewardCurrency = 300
                )
            )
            else -> listOf(
                DailyChallenge(
                    id = "run_nyc_5000",
                    title = "Run 5000 meters in New York",
                    description = "Navigate Midtown traffic in New York for 5,000 meters.",
                    goalType = DailyChallengeGoalType.RUN_DISTANCE_IN_CITY,
                    targetCity = City.NEW_YORK,
                    targetAmount = 5000,
                    rewardCurrency = 350
                ),
                DailyChallenge(
                    id = "run_cairo_3500",
                    title = "Run 3500 meters in Cairo",
                    description = "Race past the Giza horizon in Cairo for 3,500 meters.",
                    goalType = DailyChallengeGoalType.RUN_DISTANCE_IN_CITY,
                    targetCity = City.CAIRO,
                    targetAmount = 3500,
                    rewardCurrency = 280
                ),
                DailyChallenge(
                    id = "collect_speed_boosts_8",
                    title = "Collect 8 Speed Boosts",
                    description = "Collect 8 Speed Boost turbo emblems on the street.",
                    goalType = DailyChallengeGoalType.COLLECT_SPEED_BOOSTS,
                    targetCity = null,
                    targetAmount = 8,
                    rewardCurrency = 320
                )
            )
        }
    }

    /**
     * Records distance traveled in the given city (in internal game distance units),
     * converts to meters, and updates any matching RUN_DISTANCE_IN_CITY challenges.
     */
    fun onDistanceTraveled(city: City, deltaDistanceUnits: Float) {
        if (deltaDistanceUnits <= 0f) return
        val accumulated = (fractionalMetersByCity[city] ?: 0f) + deltaDistanceUnits * METERS_PER_DISTANCE_UNIT
        val wholeMeters = floor(accumulated).toInt()
        if (wholeMeters <= 0) {
            fractionalMetersByCity[city] = accumulated
            return
        }
        fractionalMetersByCity[city] = accumulated - wholeMeters
        incrementChallenges(
            goalType = DailyChallengeGoalType.RUN_DISTANCE_IN_CITY,
            city = city,
            amount = wholeMeters
        )
    }

    /**
     * Records a directly specified meter amount in the given city.
     */
    fun recordMetersInCity(city: City, meters: Int) {
        if (meters <= 0) return
        incrementChallenges(
            goalType = DailyChallengeGoalType.RUN_DISTANCE_IN_CITY,
            city = city,
            amount = meters
        )
    }

    fun onPowerUpCollected(type: String, city: City) {
        when (type) {
            "Shield" -> incrementChallenges(DailyChallengeGoalType.COLLECT_SHIELDS, city, 1)
            "Speed Boost" -> incrementChallenges(DailyChallengeGoalType.COLLECT_SPEED_BOOSTS, city, 1)
        }
    }

    fun onObstaclesCleared(count: Int, city: City) {
        if (count <= 0) return
        incrementChallenges(DailyChallengeGoalType.CLEAR_OBSTACLES, city, count)
    }

    fun dismissCompletionBanner() {
        _recentCompletionBanner.value = null
    }

    /**
     * Adds virtual currency (e.g., from bonus rewards or milestones) and persists the balance.
     */
    fun addCurrency(amount: Int) {
        if (amount <= 0) return
        _virtualCurrency.value += amount
        prefs?.edit()?.putInt(KEY_CURRENCY, _virtualCurrency.value)?.apply()
    }

    /**
     * Attempts to spend [amount] of virtual currency. Returns true and deducts from balance if sufficient.
     */
    fun spendCurrency(amount: Int): Boolean {
        if (amount < 0) return false
        val current = _virtualCurrency.value
        if (current < amount) return false
        val next = current - amount
        _virtualCurrency.value = next
        prefs?.edit()?.putInt(KEY_CURRENCY, next)?.apply()
        return true
    }

    private fun incrementChallenges(
        goalType: DailyChallengeGoalType,
        city: City,
        amount: Int
    ) {
        var currencyAwardedTotal = 0
        var newlyCompletedChallenge: DailyChallenge? = null
        var anyChanged = false

        val updatedList = _challenges.value.map { challenge ->
            val matchesType = challenge.goalType == goalType
            val matchesCity = challenge.targetCity == null || challenge.targetCity == city

            if (!challenge.isCompleted && matchesType && matchesCity) {
                val nextAmount = (challenge.currentAmount + amount).coerceAtMost(challenge.targetAmount)
                if (nextAmount != challenge.currentAmount) {
                    anyChanged = true
                    val nowCompleted = nextAmount >= challenge.targetAmount
                    val updated = challenge.copy(
                        currentAmount = nextAmount,
                        isCompleted = nowCompleted,
                        isRewardGranted = nowCompleted
                    )
                    if (nowCompleted && !challenge.isRewardGranted) {
                        currencyAwardedTotal += challenge.rewardCurrency
                        newlyCompletedChallenge = updated
                    }
                    updated
                } else {
                    challenge
                }
            } else {
                challenge
            }
        }

        if (anyChanged) {
            _challenges.value = updatedList
            if (currencyAwardedTotal > 0) {
                _virtualCurrency.value += currencyAwardedTotal
                SoundManager.playCollectSound()
                newlyCompletedChallenge?.let { completed ->
                    _recentCompletionBanner.value = ChallengeCompletionBanner(
                        challengeId = completed.id,
                        title = completed.title,
                        rewardCurrency = completed.rewardCurrency
                    )
                }
            }
            persistState(updatedList)
        }
    }

    private fun persistState(list: List<DailyChallenge>) {
        val sp = prefs ?: return
        sp.edit().apply {
            putInt(KEY_CURRENCY, _virtualCurrency.value)
            for (ch in list) {
                putInt("prog_${ch.id}", ch.currentAmount)
                putBoolean("rew_${ch.id}", ch.isRewardGranted)
            }
            apply()
        }
    }

    /**
     * Draws a stylized 3D Gold Virtual Currency Coin icon.
     */
    fun DrawScope.drawCurrencyCoinIcon(cx: Float, cy: Float, radius: Float) {
        // Outer golden rim
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFF59D), Color(0xFFFFCA28), Color(0xFFFF8F00)),
                start = Offset(cx - radius, cy - radius),
                end = Offset(cx + radius, cy + radius)
            ),
            radius = radius,
            center = Offset(cx, cy)
        )
        // Inner recessed face
        drawCircle(
            color = Color(0xFFFFB300),
            radius = radius * 0.76f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color(0xFFFFE082),
            radius = radius * 0.76f,
            center = Offset(cx, cy),
            style = Stroke(width = (radius * 0.1f).coerceAtLeast(1f))
        )
        // Central cyber-credit 'C' / vertical bar emblem
        drawLine(
            color = Color(0xFFFFFDE7),
            start = Offset(cx, cy - radius * 0.44f),
            end = Offset(cx, cy + radius * 0.44f),
            strokeWidth = (radius * 0.22f).coerceAtLeast(1.5f),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun VirtualCurrencyBadge(
    balance: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = Color(0xCC1A1D24),
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0xFFFFCA28).copy(alpha = 0.75f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("currency_balance_badge"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            with(DailyChallengeManager) {
                drawCurrencyCoinIcon(
                    cx = size.width * 0.5f,
                    cy = size.height * 0.5f,
                    radius = size.minDimension * 0.46f
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$balance CR",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFFFE082),
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun DailyChallengeCompletionToast(
    banner: ChallengeCompletionBanner?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = banner != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        if (banner != null) {
            Row(
                modifier = Modifier
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xEE102820), Color(0xEE183D32))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(
                        width = 1.5.dp,
                        color = Color(0xFFFFCA28),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("challenge_complete_banner"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = "Challenge Completed",
                    tint = Color(0xFFFFCA28),
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "DAILY CHALLENGE COMPLETE!",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64FFDA),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "${banner.title} (+${banner.rewardCurrency} CR)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun DailyChallengesDialog(
    challenges: List<DailyChallenge>,
    currencyBalance: Int,
    onDismiss: () -> Unit,
    onDeployToCity: ((City) -> Unit)? = null,
    onRotateChallenges: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("daily_challenges_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Daily Challenges",
                            tint = Color(0xFFFFCA28),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "DAILY CHALLENGES",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "COMPLETE GOALS FOR CITY CREDITS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    VirtualCurrencyBadge(balance = currencyBalance)
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(challenges, key = { it.id }) { challenge ->
                        DailyChallengeItemCard(
                            challenge = challenge,
                            onDeployToCity = onDeployToCity
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onRotateChallenges,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("refresh_challenges_button"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "New Challenges",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("NEW GOALS", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("close_challenges_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("CLOSE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DailyChallengeItemCard(
    challenge: DailyChallenge,
    onDeployToCity: ((City) -> Unit)? = null
) {
    val borderColor = if (challenge.isCompleted) {
        Color(0xFF64FFDA).copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("challenge_card_${challenge.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = challenge.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = challenge.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Reward Badge
                Row(
                    modifier = Modifier
                        .background(
                            color = if (challenge.isCompleted) Color(0xFF004D40) else Color(0xFF2B2308),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (challenge.isCompleted) Color(0xFF64FFDA) else Color(0xFFFFCA28),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (challenge.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Rewarded",
                            tint = Color(0xFF64FFDA),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+${challenge.rewardCurrency} CR",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64FFDA),
                            fontWeight = FontWeight.Black
                        )
                    } else {
                        Canvas(modifier = Modifier.size(14.dp)) {
                            with(DailyChallengeManager) {
                                drawCurrencyCoinIcon(
                                    cx = size.width * 0.5f,
                                    cy = size.height * 0.5f,
                                    radius = size.minDimension * 0.45f
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "+${challenge.rewardCurrency} CR",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFE082),
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { challenge.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = if (challenge.isCompleted) Color(0xFF64FFDA) else MaterialTheme.colorScheme.primary,
                trackColor = Color.Black.copy(alpha = 0.35f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val unitSuffix = if (challenge.goalType == DailyChallengeGoalType.RUN_DISTANCE_IN_CITY) "m" else ""
                Text(
                    text = if (challenge.isCompleted) {
                        "COMPLETED (${challenge.currentAmount}$unitSuffix / ${challenge.targetAmount}$unitSuffix)"
                    } else {
                        "PROGRESS: ${challenge.currentAmount}$unitSuffix / ${challenge.targetAmount}$unitSuffix"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (challenge.isCompleted) Color(0xFF64FFDA) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                if (!challenge.isCompleted && challenge.targetCity != null && onDeployToCity != null) {
                    Text(
                        text = "DEPLOY TO ${challenge.targetCity.cityName.uppercase()} →",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clickable { onDeployToCity(challenge.targetCity) }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                            .testTag("deploy_challenge_${challenge.id}")
                    )
                }
            }
        }
    }
}
