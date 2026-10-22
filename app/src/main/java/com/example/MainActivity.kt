package com.example

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Business
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

enum class ObstacleCollisionType {
    LOW,  // Can jump over
    HIGH, // Can slide under
    SOLID // Must dodge
}

fun getCollisionType(type: String): ObstacleCollisionType {
    return when (type) {
        "Hotdog Stand", "Cafe Table", "Baguette", "Urn", "Surfboard", "Bonsai", "Mailbox", "Traffic Cone" -> ObstacleCollisionType.LOW
        "Traffic Light", "Street Lamp", "Lantern", "Bridge Sign", "Archway", "Boomerang", "Scaffolding" -> ObstacleCollisionType.HIGH
        else -> ObstacleCollisionType.SOLID // Taxi, Bus, Phone Box, Vending Machine, Camel, Kangaroo, Kiosk, Roadblock, Trash Can, Bench
    }
}

enum class City(
    val cityName: String,
    val skyColor: Color,
    val groundColor: Color,
    val obstacleTypes: List<String>
) {
    NEW_YORK("New York", Color(0xFF1976D2), Color(0xFF7A756E), listOf("Taxi", "Hotdog Stand", "Traffic Light", "Traffic Cone", "Roadblock", "Trash Can")),
    PARIS("Paris", Color(0xFF81D4FA), Color(0xFFA1887F), listOf("Kiosk", "Cafe Table", "Street Lamp", "Traffic Cone", "Roadblock", "Bench")),
    TOKYO("Tokyo", Color(0xFF212121), Color(0xFFE91E63), listOf("Vending Machine", "Bonsai", "Lantern", "Traffic Cone", "Roadblock", "Scaffolding")),
    LONDON("London", Color(0xFF9E9E9E), Color(0xFF424242), listOf("Bus", "Mailbox", "Bridge Sign", "Traffic Cone", "Roadblock", "Trash Can")),
    CAIRO("Cairo", Color(0xFFFFCC80), Color(0xFFD7CCC8), listOf("Camel", "Urn", "Archway", "Traffic Cone", "Roadblock", "Bench")),
    SYDNEY("Sydney", Color(0xFF4FC3F7), Color(0xFF81C784), listOf("Kangaroo", "Surfboard", "Boomerang", "Traffic Cone", "Roadblock", "Trash Can"))
}

data class Obstacle(
    val id: Int,
    val lane: Int,
    var yRatio: Float,
    val type: String,
    var passed: Boolean = false
)

data class PowerUp(
    val id: Int,
    val lane: Int,
    var yRatio: Float,
    val type: String
)

data class GameState(
    val isPlaying: Boolean = false,
    val isGameOver: Boolean = false,
    val isPaused: Boolean = false,
    val score: Int = 0,
    val playerLane: Int = 1, // 0: Left, 1: Middle, 2: Right
    val isJumping: Boolean = false,
    val jumpTime: Float = 0f,
    val isSliding: Boolean = false,
    val slideTime: Float = 0f,
    val obstacles: List<Obstacle> = emptyList(),
    val powerUps: List<PowerUp> = emptyList(),
    val currentCity: City = City.NEW_YORK,
    val distance: Float = 0f,
    val distanceInCurrentCity: Float = 0f,
    val sprintTimeLeft: Float = 0f,
    val hasShield: Boolean = false,
    val turnDirection: Float = 0f,
    val isTransitioning: Boolean = false,
    val transitionProgress: Float = 0f,
    val transitionNextCity: City? = null,
    val particleBursts: List<PowerUpParticleBurst> = emptyList()
)

class GameViewModel : ViewModel() {
    private val _gameState = MutableStateFlow(GameState())
    val gameState = _gameState.asStateFlow()

    private var obstacleIdCounter = 0
    private var speed = 0.008f
    private var distanceSinceLastSpawn = 0f
    private var distanceSinceLastPowerUpSpawn = 0f
    private var lastFootstepIndex = -1
    private var distanceSinceLastAmbientTrigger = 0f
    
    fun startGame(city: City) {
        _gameState.value = GameState(isPlaying = true, currentCity = city)
        obstacleIdCounter = 0
        speed = 0.008f
        distanceSinceLastSpawn = 0f
        distanceSinceLastPowerUpSpawn = 6.5f
        lastFootstepIndex = 0
        distanceSinceLastAmbientTrigger = 0f
        SoundManager.updateEnvironmentState(
            city = city,
            isPlaying = true,
            isPaused = false,
            isGameOver = false,
            isSprinting = false
        )
        SoundManager.playFootstepSound(city = city, isLeftFoot = true, isSprinting = false)
    }

    fun moveLeft() {
        val state = _gameState.value
        if (state.isPlaying && !state.isGameOver && !state.isPaused && state.playerLane > 0) {
            SoundManager.playLaneSwitchSound()
            _gameState.value = state.copy(playerLane = state.playerLane - 1)
        }
    }

    fun moveRight() {
        val state = _gameState.value
        if (state.isPlaying && !state.isGameOver && !state.isPaused && state.playerLane < 2) {
            SoundManager.playLaneSwitchSound()
            _gameState.value = state.copy(playerLane = state.playerLane + 1)
        }
    }
    
    fun jump() {
        val state = _gameState.value
        if (state.isPlaying && !state.isGameOver && !state.isPaused && !state.isJumping && !state.isSliding) {
            SoundManager.playJumpSound()
            _gameState.value = state.copy(isJumping = true, jumpTime = 0f)
        }
    }

    fun slide() {
        val state = _gameState.value
        if (state.isPlaying && !state.isGameOver && !state.isPaused && !state.isSliding && !state.isJumping) {
            SoundManager.playSlideSound(state.currentCity)
            _gameState.value = state.copy(isSliding = true, slideTime = 0f)
        }
    }
    
    fun pauseGame() {
        val state = _gameState.value
        if (state.isPlaying && !state.isGameOver && !state.isPaused) {
            val updated = state.copy(isPaused = true)
            _gameState.value = updated
            SoundManager.updateEnvironmentState(
                city = updated.currentCity,
                isPlaying = updated.isPlaying,
                isPaused = true,
                isGameOver = updated.isGameOver,
                isSprinting = updated.sprintTimeLeft > 0f
            )
        }
    }

    fun resumeGame() {
        val state = _gameState.value
        if (state.isPlaying && !state.isGameOver && state.isPaused) {
            val updated = state.copy(isPaused = false)
            _gameState.value = updated
            SoundManager.updateEnvironmentState(
                city = updated.currentCity,
                isPlaying = updated.isPlaying,
                isPaused = false,
                isGameOver = updated.isGameOver,
                isSprinting = updated.sprintTimeLeft > 0f
            )
        }
    }

    fun restartGame() {
        val state = _gameState.value
        startGame(state.currentCity)
    }
    
    fun returnToMenu() {
        val currentCity = _gameState.value.currentCity
        _gameState.value = GameState()
        SoundManager.updateEnvironmentState(
            city = currentCity,
            isPlaying = false,
            isPaused = false,
            isGameOver = false,
            isSprinting = false
        )
    }

    fun update(dt: Float) {
        val state = _gameState.value
        if (!state.isPlaying || state.isGameOver || state.isPaused) return

        val safeDt = dt.coerceAtMost(32f) // Cap dt to prevent huge jumps on frame drops
        val speedMultiplier = safeDt / 16f
        
        // Handle sprint time decrement
        val nextSprintTimeLeft = (state.sprintTimeLeft - (safeDt / 1000f)).coerceAtLeast(0f)
        val isSprinting = nextSprintTimeLeft > 0f
        
        var newIsJumping = state.isJumping
        var newJumpTime = state.jumpTime
        var justLanded = false
        if (newIsJumping) {
            newJumpTime += safeDt / 1000f
            if (newJumpTime >= 0.5f) { // Jump lasts 0.5 seconds
                newIsJumping = false
                newJumpTime = 0f
                justLanded = true
            }
        }

        var newIsSliding = state.isSliding
        var newSlideTime = state.slideTime
        if (newIsSliding) {
            newSlideTime += safeDt / 1000f
            if (newSlideTime >= 0.5f) { // Slide lasts 0.5 seconds
                newIsSliding = false
                newSlideTime = 0f
            }
        }
        
        // Exhilarating 1.8x speed bump during City Sprint
        val effectiveSpeed = if (isSprinting) speed * 1.8f else speed
        val currentSpeed = effectiveSpeed * speedMultiplier
        val currentDistanceTravelled = currentSpeed * 2f

        distanceSinceLastSpawn += currentDistanceTravelled
        distanceSinceLastPowerUpSpawn += currentDistanceTravelled

        val newObstacles = state.obstacles.map { it.copy(yRatio = it.yRatio + currentSpeed) }.toMutableList()
        val newPowerUps = state.powerUps.map { it.copy(yRatio = it.yRatio + currentSpeed) }.toMutableList()

        var scoreIncr = 0
        var clearedObstaclesCount = 0
        val iterator = newObstacles.iterator()
        while (iterator.hasNext()) {
            val obs = iterator.next()
            if (obs.yRatio > 1.5f) {
                iterator.remove()
                clearedObstaclesCount++
                scoreIncr += if (isSprinting) 20 else 10 // Double point gain from bypassed/cleared obstacles during Sprint!
            }
        }


        // Spawn a new obstacle if we've traveled far enough
        val spawnThreshold = 1.0f + Random.nextFloat() * 1.5f // Distance between obstacles
        if (!state.isTransitioning && distanceSinceLastSpawn > spawnThreshold) {
            val lane = Random.nextInt(3)
            val type = state.currentCity.obstacleTypes.random()
            newObstacles.add(Obstacle(obstacleIdCounter++, lane, -0.3f, type))
            distanceSinceLastSpawn = 0f
        }

        // Spawn a new 'Speed Boost' or 'Shield' powerup
        val powerUpSpawnThreshold = 8.5f + kotlin.random.Random.nextFloat() * 5.5f
        if (!state.isTransitioning && distanceSinceLastPowerUpSpawn > powerUpSpawnThreshold) {
            var lane = kotlin.random.Random.nextInt(3)
            val lastObs = newObstacles.lastOrNull()
            if (lastObs != null && lastObs.yRatio < 0f && lastObs.lane == lane) {
                lane = (lane + 1) % 3
            }
            val puType = if (kotlin.random.Random.nextBoolean()) "Speed Boost" else "Shield"
            newPowerUps.add(PowerUp(obstacleIdCounter++, lane, -0.3f, puType))
            distanceSinceLastPowerUpSpawn = 0f
        }

        // Advance active particle bursts and prune finished ones
        val dtSec = safeDt / 1000f
        val updatedBursts = state.particleBursts.mapNotNull { burst ->
            val nextAge = burst.ageSeconds + dtSec
            if (nextAge < burst.durationSeconds) burst.copy(ageSeconds = nextAge) else null
        }.toMutableList()

        // The player's visual Y is 0.8. Since obstacle visual Y is yRatio^2,
        // the obstacle reaches the player around yRatio = sqrt(0.8) ≈ 0.894.
        val playerZStart = 0.92f
        val playerZEnd = 0.95f
        
        val playerYBottom = if (newIsJumping) 0.5f else 0f
        val playerYTop = if (newIsJumping) 1.5f else if (newIsSliding) 0.5f else 1.0f

        // Check power-up collection
        var collectSprint = false
        var collectShield = false
        val iteratorPU = newPowerUps.iterator()
        while (iteratorPU.hasNext()) {
            val pu = iteratorPU.next()
            if (pu.yRatio > 1.5f) {
                iteratorPU.remove()
            } else {
                if (pu.lane == state.playerLane && pu.yRatio + 0.1f > playerZStart && pu.yRatio < playerZEnd) {
                    iteratorPU.remove()
                    if (pu.type == "Speed Boost") {
                        collectSprint = true
                        scoreIncr += 50 // Big point bonus for grab
                        DailyChallengeManager.onPowerUpCollected("Speed Boost", state.currentCity)
                        updatedBursts.add(
                            PowerUpParticleBurst(
                                id = obstacleIdCounter++,
                                type = ParticleBurstType.SPEED_COLLECT,
                                lane = state.playerLane.toFloat()
                            )
                        )
                    } else if (pu.type == "Shield") {
                        collectShield = true
                        scoreIncr += 50
                        DailyChallengeManager.onPowerUpCollected("Shield", state.currentCity)
                        updatedBursts.add(
                            PowerUpParticleBurst(
                                id = obstacleIdCounter++,
                                type = ParticleBurstType.SHIELD_COLLECT,
                                lane = state.playerLane.toFloat()
                            )
                        )
                    }
                }
            }
        }

        // Check obstacle collisions (or smashes!)
        var collision = false
        var shieldBroken = false
        var sprintSmashed = false
        val finalObstacles = mutableListOf<Obstacle>()
        for (obs in newObstacles) {
            val obsZStart = obs.yRatio
            val obsZEnd = obs.yRatio + 0.05f
            val zOverlap = obsZStart < playerZEnd && obsZEnd > playerZStart
            
            if (obs.lane == state.playerLane && zOverlap) {
                val colType = getCollisionType(obs.type)
                val obsYBottom = if (colType == ObstacleCollisionType.HIGH) 0.6f else 0f
                val obsYTop = if (colType == ObstacleCollisionType.LOW) 0.4f else 1.5f
                
                val yOverlap = playerYBottom < obsYTop && playerYTop > obsYBottom
                
                if (yOverlap) {
                    if (isSprinting) {
                        scoreIncr += 20 // Smashed bonus points!
                        sprintSmashed = true
                    } else {
                        if (state.hasShield || collectShield) {
                            if (!shieldBroken) {
                                shieldBroken = true
                                scoreIncr += 20 // Smashed with shield!
                            }
                        } else {
                            collision = true
                            finalObstacles.add(obs)
                        }
                    }
                } else {
                    if (!obs.passed) {
                        obs.passed = true
                        scoreIncr += 5
                    }
                    finalObstacles.add(obs)
                }
            } else {
                finalObstacles.add(obs)
            }
        }

        val updatedSprintTime = if (collectSprint) 6.0f else nextSprintTimeLeft
        val updatedShield = if (shieldBroken) false else (state.hasShield || collectShield)

        if (sprintSmashed) {
            updatedBursts.add(
                PowerUpParticleBurst(
                    id = obstacleIdCounter++,
                    type = ParticleBurstType.SPEED_SMASH,
                    lane = state.playerLane.toFloat()
                )
            )
        }
        if (shieldBroken) {
            SoundManager.playShieldBreakSound()
            updatedBursts.add(
                PowerUpParticleBurst(
                    id = obstacleIdCounter++,
                    type = ParticleBurstType.SHIELD_DEFLECT,
                    lane = state.playerLane.toFloat()
                )
            )
        }
        if (collectSprint || collectShield) {
            SoundManager.playCollectSound()
        }

        val newDistance = state.distance + currentDistanceTravelled
        distanceSinceLastAmbientTrigger += currentDistanceTravelled
        
        var newIsTransitioning = state.isTransitioning
        var newTransitionProgress = state.transitionProgress
        var newCurrentCity = state.currentCity
        var newNextCity = state.transitionNextCity
        var newDistanceInCity = state.distanceInCurrentCity + currentDistanceTravelled
        
        if (newIsTransitioning) {
            newTransitionProgress += safeDt / 2000f // 2 seconds total transition
            if (newTransitionProgress >= 0.5f && state.transitionProgress < 0.5f) {
                newCurrentCity = newNextCity ?: City.NEW_YORK
                newDistanceInCity = 0f
                newObstacles.clear()
                newPowerUps.clear()
            }
            if (newTransitionProgress >= 1f) {
                newIsTransitioning = false
                newTransitionProgress = 0f
                newNextCity = null
            }
        } else {
            if (newDistanceInCity >= 200f) {
                newIsTransitioning = true
                newTransitionProgress = 0f
                newNextCity = City.entries[(City.entries.indexOf(state.currentCity) + 1) % City.entries.size]
            }
        }

        if (collision) {
            SoundManager.updateEnvironmentState(
                city = newCurrentCity,
                isPlaying = true,
                isPaused = false,
                isGameOver = true,
                isSprinting = false
            )
            SoundManager.playGameOverSound()
            _gameState.value = state.copy(
                isGameOver = true,
                obstacles = finalObstacles,
                powerUps = newPowerUps,
                sprintTimeLeft = 0f,
                hasShield = false,
                isJumping = false,
                jumpTime = 0f,
                isSliding = false,
                slideTime = 0f,
                isTransitioning = false,
                transitionProgress = 0f
            )
        } else {
            SoundManager.updateEnvironmentState(
                city = newCurrentCity,
                isPlaying = true,
                isPaused = false,
                isGameOver = false,
                isSprinting = updatedSprintTime > 0f
            )

            if (justLanded) {
                SoundManager.playLandingSound(newCurrentCity)
            }

            // Trigger surface-accurate character footsteps synchronized with RunnerRenderer's stride cycle
            val strideFreq = if (updatedSprintTime > 0f) 21f else 15f
            val currentStepIndex = ((newDistance * strideFreq) / kotlin.math.PI.toFloat()).toInt()
            if (currentStepIndex > lastFootstepIndex) {
                lastFootstepIndex = currentStepIndex
                if (!newIsJumping && !newIsSliding) {
                    val isLeftFoot = (currentStepIndex % 2) == 0
                    SoundManager.playFootstepSound(
                        city = newCurrentCity,
                        isLeftFoot = isLeftFoot,
                        isSprinting = updatedSprintTime > 0f
                    )
                }
            }

            // Trigger contextual ambient city noises based on nearby street obstacles or distance intervals
            if (distanceSinceLastAmbientTrigger >= 9.5f) {
                val nearbyObstacle = finalObstacles.firstOrNull { it.yRatio in 0.55f..0.95f }
                if (nearbyObstacle != null) {
                    distanceSinceLastAmbientTrigger = 0f
                    val ambientType = when (nearbyObstacle.type) {
                        "Taxi", "Bus", "Roadblock", "Traffic Cone", "Camel", "Kangaroo" -> AmbientNoiseType.TRAFFIC
                        "Traffic Light", "Bridge Sign", "Scaffolding", "Archway", "Street Lamp" -> AmbientNoiseType.SIREN
                        else -> AmbientNoiseType.CROWD
                    }
                    SoundManager.triggerAmbientCityNoise(newCurrentCity, ambientType)
                } else if (distanceSinceLastAmbientTrigger >= 16f) {
                    distanceSinceLastAmbientTrigger = 0f
                    val ambientCycle = ((newDistance / 16f).toInt()) % 3
                    val ambientType = when (ambientCycle) {
                        0 -> AmbientNoiseType.TRAFFIC
                        1 -> AmbientNoiseType.SIREN
                        else -> AmbientNoiseType.CROWD
                    }
                    SoundManager.triggerAmbientCityNoise(newCurrentCity, ambientType)
                }
            }

            // Update Daily Challenge progress for distance run in current city and cleared obstacles
            DailyChallengeManager.onDistanceTraveled(newCurrentCity, currentDistanceTravelled)
            val totalObstaclesClearedThisFrame = clearedObstaclesCount +
                (if (sprintSmashed) 1 else 0) +
                (if (shieldBroken) 1 else 0)
            if (totalObstaclesClearedThisFrame > 0) {
                DailyChallengeManager.onObstaclesCleared(totalObstaclesClearedThisFrame, newCurrentCity)
            }

            _gameState.value = state.copy(
                score = state.score + scoreIncr,
                obstacles = finalObstacles,
                powerUps = newPowerUps,
                distance = newDistance,
                distanceInCurrentCity = newDistanceInCity,
                currentCity = newCurrentCity,
                sprintTimeLeft = updatedSprintTime,
                hasShield = updatedShield,
                isJumping = newIsJumping,
                jumpTime = newJumpTime,
                isSliding = newIsSliding,
                slideTime = newSlideTime,
                isTransitioning = newIsTransitioning,
                transitionProgress = newTransitionProgress,
                transitionNextCity = newNextCity,
                particleBursts = updatedBursts
            )
            speed += 0.000002f * speedMultiplier
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundManager.init(this)
        DailyChallengeManager.init(this)
        CosmeticShopManager.init(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val viewModel: GameViewModel = viewModel()
    val state by viewModel.gameState.collectAsState()

    Crossfade(
        targetState = state.isPlaying || state.isGameOver,
        modifier = modifier.fillMaxSize(),
        label = "MainScreenTransition",
        animationSpec = tween(600)
    ) { isGameActive ->
        if (!isGameActive) {
            CitySelectionScreen(onSelect = { viewModel.startGame(it) })
        } else {
            GameScreen(viewModel, state)
        }
    }
}

@Composable
fun CitySelectionScreen(onSelect: (City) -> Unit) {
    val challenges by DailyChallengeManager.challenges.collectAsState()
    val currencyBalance by DailyChallengeManager.virtualCurrency.collectAsState()
    val cosmetics by CosmeticShopManager.cosmetics.collectAsState()
    val equippedCosmetic by CosmeticShopManager.equippedCosmetic.collectAsState()
    var showChallengesModal by remember { mutableStateOf(false) }
    var showCosmeticShopModal by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Subtle grid background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridStep = 40.dp.toPx()
            for (x in 0..size.width.toInt() step gridStep.toInt()) {
                drawLine(
                    color = Color.White.copy(alpha = 0.03f),
                    start = Offset(x.toFloat(), 0f),
                    end = Offset(x.toFloat(), size.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..size.height.toInt() step gridStep.toInt()) {
                drawLine(
                    color = Color.White.copy(alpha = 0.03f),
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat()),
                    strokeWidth = 1f
                )
            }
            // Glow behind header
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.15f), Color.Transparent)
                ),
                radius = 600f,
                center = Offset(size.width / 2f, 200f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar with Virtual Currency, Cosmetic Shop & Daily Challenges buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VirtualCurrencyBadge(balance = currencyBalance)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showCosmeticShopModal = true },
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("cosmetic_shop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checkroom,
                            contentDescription = "Cosmetic Shop",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SHOP",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val completedCount = challenges.count { it.isCompleted }
                    OutlinedButton(
                        onClick = { showChallengesModal = true },
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCA28).copy(alpha = 0.7f)),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("daily_challenges_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Daily Challenges",
                            tint = Color(0xFFFFCA28),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "GOALS ($completedCount/${challenges.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                contentDescription = "Runner",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "GLOBAL DEPLOYMENT",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "equipped: ${equippedCosmetic.name.uppercase()} [${equippedCosmetic.category.label}]",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = equippedCosmetic.reflectiveStripe,
                modifier = Modifier
                    .clickable { showCosmeticShopModal = true }
                    .padding(vertical = 4.dp)
                    .testTag("equipped_cosmetic_banner")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Active Daily Challenges Preview Card on Main Screen
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showChallengesModal = true }
                    .testTag("daily_challenges_preview_card"),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color(0xFFFFCA28).copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE DAILY CHALLENGES",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFFCA28),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "VIEW ALL →",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    challenges.forEach { ch ->
                        val unitSuffix = if (ch.goalType == DailyChallengeGoalType.RUN_DISTANCE_IN_CITY) "m" else ""
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• ${ch.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ch.isCompleted) Color(0xFF64FFDA) else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (ch.isCompleted) {
                                    "DONE (+${ch.rewardCurrency} CR)"
                                } else {
                                    "${ch.currentAmount}$unitSuffix/${ch.targetAmount}$unitSuffix (+${ch.rewardCurrency} CR)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ch.isCompleted) Color(0xFF64FFDA) else Color(0xFFFFE082),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(City.entries.toTypedArray()) { city ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(city) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Business,
                                    contentDescription = "City",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(20.dp))
                            Column {
                                Text(
                                    text = city.cityName.uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(city.skyColor, androidx.compose.foundation.shape.CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val profile = SoundManager.getProfileForCity(city)
                                    Text(
                                        text = "AUDIO: ${profile.surfaceName} • ${profile.ambientSummary}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 1.1.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showChallengesModal) {
            DailyChallengesDialog(
                challenges = challenges,
                currencyBalance = currencyBalance,
                onDismiss = { showChallengesModal = false },
                onDeployToCity = { targetCity ->
                    showChallengesModal = false
                    onSelect(targetCity)
                },
                onRotateChallenges = { DailyChallengeManager.rotateNewChallengeSet() }
            )
        }

        if (showCosmeticShopModal) {
            CosmeticShopDialog(
                cosmetics = cosmetics,
                equippedCosmetic = equippedCosmetic,
                currencyBalance = currencyBalance,
                onUnlockOrEquip = { id -> CosmeticShopManager.unlockOrEquip(id) },
                onDismiss = { showCosmeticShopModal = false }
            )
        }
    }
}

@Composable
fun GameScreen(viewModel: GameViewModel, state: GameState) {
    val equippedCosmetic by CosmeticShopManager.equippedCosmetic.collectAsState()
    val playerAnimatedX by animateFloatAsState(
        targetValue = state.playerLane.toFloat(),
        animationSpec = tween(150),
        label = "playerLane"
    )

    LaunchedEffect(state.isPlaying, state.isGameOver) {
        var lastFrameTime = 0L
        while (state.isPlaying && !state.isGameOver) {
            withFrameNanos { time ->
                if (lastFrameTime != 0L) {
                    val dt = (time - lastFrameTime) / 1_000_000f
                    viewModel.update(dt)
                }
                lastFrameTime = time
            }
        }
    }
    
    
        Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var swipeConsumed = false
                var swipeX = 0f
                var swipeY = 0f
                detectDragGestures(
                    onDragStart = {
                        swipeConsumed = false
                        swipeX = 0f
                        swipeY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (swipeConsumed) return@detectDragGestures
                        swipeX += dragAmount.x
                        swipeY += dragAmount.y
                        
                        val threshold = 50f
                        if (kotlin.math.abs(swipeX) > threshold || kotlin.math.abs(swipeY) > threshold) {
                            if (kotlin.math.abs(swipeX) > kotlin.math.abs(swipeY)) {
                                if (swipeX > 0) viewModel.moveRight() else viewModel.moveLeft()
                            } else {
                                if (swipeY > 0) viewModel.slide() else viewModel.jump()
                            }
                            swipeConsumed = true
                        }
                    }
                )
            }
    ) {
        val animatedCurve by animateFloatAsState(
            targetValue = state.turnDirection,
            animationSpec = tween(1000),
            label = "animatedCurve"
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val vanishingX = width / 2f + animatedCurve * (width * 0.4f)
            val vanishingY = height * 0.35f

            val roadWidthAtBottom = width * 0.7f
            val roadStartXAtBottom = (width - roadWidthAtBottom) / 2f
            val laneWidthAtBottom = roadWidthAtBottom / 3f

            // 1. Realistic City Skyline, Landmarks, Sky & Waterfront
            with(CityBackgroundRenderer) {
                drawCityBackground(
                    city = state.currentCity,
                    distance = state.distance,
                    width = width,
                    vanishingX = vanishingX,
                    vanishingY = vanishingY
                )
            }

            // 2. Realistic 3D Streets, Crosswalks, Sidewalks, Street Furniture & Street-Side 3D Buildings
            with(StreetAndBuildingRenderer) {
                drawStreetAndBuildings(
                    city = state.currentCity,
                    distance = state.distance,
                    width = width,
                    height = height,
                    vanishingX = vanishingX,
                    vanishingY = vanishingY,
                    roadStartXAtBottom = roadStartXAtBottom,
                    laneWidthAtBottom = laneWidthAtBottom
                )
            }

            // 3. Realistic 3D Obstacles
            with(ObstacleRenderer) {
                drawObstacles(
                    obstacles = state.obstacles,
                    distance = state.distance,
                    width = width,
                    height = height,
                    vanishingX = vanishingX,
                    vanishingY = vanishingY,
                    roadStartXAtBottom = roadStartXAtBottom,
                    laneWidthAtBottom = laneWidthAtBottom
                )
            }

            // 4. 3D Power-Up Icons & Local Particle Emitters ('Speed Boost' & 'Shield')
            with(PowerUpAndParticleRenderer) {
                drawPowerUps(
                    powerUps = state.powerUps,
                    distance = state.distance,
                    height = height,
                    vanishingX = vanishingX,
                    vanishingY = vanishingY,
                    roadStartXAtBottom = roadStartXAtBottom,
                    laneWidthAtBottom = laneWidthAtBottom
                )
            }

            // 5. Realistic Anatomical 3D Player Runner (Equipped Outfit / 3D Model)
            with(RunnerRenderer) {
                drawRunner(
                    state = state,
                    playerAnimatedX = playerAnimatedX,
                    width = width,
                    height = height,
                    vanishingX = vanishingX,
                    vanishingY = vanishingY,
                    roadStartXAtBottom = roadStartXAtBottom,
                    laneWidthAtBottom = laneWidthAtBottom,
                    cosmetic = equippedCosmetic
                )
            }

            // 6. Active Power-Up Trails, Orbiting Shield Particles & Collection/Smash Particle Bursts
            with(PowerUpAndParticleRenderer) {
                drawActiveEffectsAndBursts(
                    state = state,
                    playerAnimatedX = playerAnimatedX,
                    width = width,
                    height = height,
                    vanishingX = vanishingX,
                    vanishingY = vanishingY,
                    roadStartXAtBottom = roadStartXAtBottom,
                    laneWidthAtBottom = laneWidthAtBottom
                )
            }
        }
        
        // HUD
        val isMuted by SoundManager.isMuted.collectAsState()
        val activeSoundStatus by SoundManager.activeSoundStatus.collectAsState()
        val currencyBalance by DailyChallengeManager.virtualCurrency.collectAsState()
        val challenges by DailyChallengeManager.challenges.collectAsState()
        val completionBanner by DailyChallengeManager.recentCompletionBanner.collectAsState()

        LaunchedEffect(completionBanner?.timestampMs) {
            if (completionBanner != null) {
                delay(3200)
                DailyChallengeManager.dismissCompletionBanner()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 48.dp, end = 24.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "LOC: ${state.currentCity.cityName.uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )
                    VirtualCurrencyBadge(balance = currencyBalance)
                }
                Text(
                    text = "PTS: ${String.format("%06d", state.score + (state.distance * 10).toInt())}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary, // Cyan
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isMuted) stringResource(R.string.sound_status_muted) else activeSoundStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.testTag("sound_status_text")
                )
                val trackedChallenge = challenges.firstOrNull {
                    !it.isCompleted && it.targetCity == state.currentCity
                } ?: challenges.firstOrNull { !it.isCompleted }
                if (trackedChallenge != null) {
                    val unitSuffix = if (trackedChallenge.goalType == DailyChallengeGoalType.RUN_DISTANCE_IN_CITY) "m" else ""
                    Text(
                        text = "GOAL: ${trackedChallenge.title} (${trackedChallenge.currentAmount}$unitSuffix/${trackedChallenge.targetAmount}$unitSuffix)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFE082),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("hud_active_challenge_text")
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { SoundManager.toggleMute() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .testTag("sound_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = if (isMuted) {
                            stringResource(R.string.sound_off_description)
                        } else {
                            stringResource(R.string.sound_on_description)
                        },
                        tint = if (isMuted) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = { viewModel.pauseGame() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .testTag("pause_button")
                ) {
                    Text(
                        text = "||",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        // Active Power-Up Visual Icon Badges ('Speed Boost' & 'Shield')
        PowerUpHudBadges(
            sprintTimeLeft = state.sprintTimeLeft,
            hasShield = state.hasShield,
            distance = state.distance,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 132.dp)
        )

        // Animated Daily Challenge Completion Toast Banner
        DailyChallengeCompletionToast(
            banner = completionBanner,
            onDismiss = { DailyChallengeManager.dismissCompletionBanner() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 188.dp, start = 24.dp, end = 24.dp)
        )
        
        if (state.isPaused && !state.isGameOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { viewModel.resumeGame() },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clickable(enabled = false, onClick = {}),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SYSTEM PAUSED",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = { viewModel.resumeGame() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("RESUME", letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { viewModel.restartGame() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                        ) {
                            Text("RESTART", letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { viewModel.returnToMenu() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                        ) {
                            Text("ABORT MISSION", letterSpacing = 1.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        
        if (state.isGameOver) {
            GameOverModal(
                score = state.score + (state.distance * 10).toInt(),
                city = state.currentCity.cityName,
                onRestart = { viewModel.startGame(state.currentCity) },
                onChangeCity = { viewModel.returnToMenu() }
            )
        }
    }
}

@Composable
fun GameOverModal(
    score: Int,
    city: String,
    onRestart: () -> Unit,
    onChangeCity: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { /* Do nothing, force action */ },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false, 
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Game Over",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "MISSION FAILED",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = city.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PERFORMANCE RATING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black
                    )
                }
                
                Spacer(modifier = Modifier.height(40.dp))
                
                Button(
                    onClick = onRestart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("RETRY $city", letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = onChangeCity,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                ) {
                    Text("CHANGE DESTINATION", letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
