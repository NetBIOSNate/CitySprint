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
    NEW_YORK("New York", Color(0xFF1976D2), Color(0xFF607D8B), listOf("Taxi", "Hotdog Stand", "Traffic Light", "Traffic Cone", "Roadblock", "Trash Can")),
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
    val transitionNextCity: City? = null
)

class GameViewModel : ViewModel() {
    private val _gameState = MutableStateFlow(GameState())
    val gameState = _gameState.asStateFlow()

    private var obstacleIdCounter = 0
    private var speed = 0.008f
    private var distanceSinceLastSpawn = 0f
    private var distanceSinceLastPowerUpSpawn = 0f
    
    fun startGame(city: City) {
        _gameState.value = GameState(isPlaying = true, currentCity = city)
        obstacleIdCounter = 0
        speed = 0.008f
        distanceSinceLastSpawn = 0f
        distanceSinceLastPowerUpSpawn = 0f
    }

    fun moveLeft() {
        val state = _gameState.value
        if (state.isPlaying && !state.isGameOver && !state.isPaused && state.playerLane > 0) {
            _gameState.value = state.copy(playerLane = state.playerLane - 1)
        }
    }

    fun moveRight() {
        val state = _gameState.value
        if (state.isPlaying && !state.isGameOver && !state.isPaused && state.playerLane < 2) {
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
            _gameState.value = state.copy(isSliding = true, slideTime = 0f)
        }
    }
    
    fun pauseGame() {
        val state = _gameState.value
        if (state.isPlaying && !state.isGameOver && !state.isPaused) {
            _gameState.value = state.copy(isPaused = true)
        }
    }

    fun resumeGame() {
        val state = _gameState.value
        if (state.isPlaying && !state.isGameOver && state.isPaused) {
            _gameState.value = state.copy(isPaused = false)
        }
    }

    fun restartGame() {
        val state = _gameState.value
        startGame(state.currentCity)
    }
    
    fun returnToMenu() {
        _gameState.value = GameState()
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
        if (newIsJumping) {
            newJumpTime += safeDt / 1000f
            if (newJumpTime >= 0.5f) { // Jump lasts 0.5 seconds
                newIsJumping = false
                newJumpTime = 0f
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
        val iterator = newObstacles.iterator()
        while (iterator.hasNext()) {
            val obs = iterator.next()
            if (obs.yRatio > 1.5f) {
                iterator.remove()
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
        val powerUpSpawnThreshold = 12f + kotlin.random.Random.nextFloat() * 6f
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
                    } else if (pu.type == "Shield") {
                        collectShield = true
                        scoreIncr += 50
                    }
                }
            }
        }

        // Check obstacle collisions (or smashes!)
        var collision = false
        var shieldBroken = false
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

        if (collectSprint || collectShield) {
            SoundManager.playCollectSound()
        }

        val newDistance = state.distance + currentDistanceTravelled
        
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
                transitionNextCity = newNextCity
            )
            speed += 0.000002f * speedMultiplier
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundManager.init()
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
                .padding(horizontal = 24.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                contentDescription = "Runner",
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "GLOBAL DEPLOYMENT",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "SELECT MISSION DESTINATION",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(48.dp))

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
                                    Text(
                                        text = "STATUS: ACTIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 1.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameScreen(viewModel: GameViewModel, state: GameState) {
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

            // Sky
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(state.currentCity.skyColor, state.currentCity.skyColor.copy(alpha = 0.5f), Color(0xFFFFCC80).copy(alpha = 0.3f)),
                    startY = 0f,
                    endY = vanishingY
                ),
                size = Size(width, vanishingY)
            )

            // Building drawing helper for realistic cities
            val drawBuilding = { baseX: Float, baseY: Float, w: Float, h: Float, baseColor: Color, alpha: Float, seed: Int, detailLevel: Int ->
                val r = kotlin.random.Random(seed)
                val isStepped = r.nextFloat() > 0.5f
                val hasAntenna = r.nextFloat() > 0.5f
                
                val mainColor = baseColor.copy(alpha = alpha)
                val highlightColor = Color.White.copy(alpha = alpha * 0.15f)
                val shadowColor = Color.Black.copy(alpha = alpha * 0.35f)
                
                var currentW = w
                var currentX = baseX
                var currentY = baseY
                
                val steps = if (isStepped) 2 + r.nextInt(3) else 1
                val stepH = h / steps
                
                for (i in 0 until steps) {
                    drawRect(mainColor, Offset(currentX, currentY - stepH), Size(currentW, stepH))
                    drawRect(highlightColor, Offset(currentX, currentY - stepH), Size(currentW * 0.1f, stepH))
                    drawRect(shadowColor, Offset(currentX + currentW * 0.9f, currentY - stepH), Size(currentW * 0.1f, stepH))
                    
                    // Windows for this step
                    if (detailLevel > 0) {
                        val cols = (currentW / (width * 0.015f)).toInt().coerceIn(2, 8)
                        val rows = (stepH / (width * 0.02f)).toInt().coerceIn(2, 15)
                        val winW = (currentW * 0.5f) / cols
                        val winH = (stepH * 0.7f) / rows
                        val padX = (currentW * 0.5f) / (cols + 1)
                        val padY = (stepH * 0.3f) / (rows + 1)
                        
                        for (row in 0 until rows) {
                            for (col in 0 until cols) {
                                if (r.nextFloat() > 0.4f) {
                                    val isLit = r.nextFloat() > 0.7f
                                    val winColor = if (isLit) {
                                        listOf(Color(0xFFFFF59D), Color(0xFFFFE082), Color(0xFF80DEEA)).random(r).copy(alpha = alpha * 0.9f)
                                    } else {
                                        Color.Black.copy(alpha = alpha * 0.6f)
                                    }
                                    drawRect(winColor, Offset(currentX + padX + col * (winW + padX), currentY - stepH + padY + row * (winH + padY)), Size(winW, winH))
                                }
                            }
                        }
                    }
                    
                    currentY -= stepH
                    val inset = currentW * (0.1f + r.nextFloat() * 0.15f)
                    currentX += inset
                    currentW -= inset * 2
                }
                
                if (hasAntenna) {
                    drawLine(shadowColor, Offset(baseX + w/2, currentY), Offset(baseX + w/2, currentY - h * 0.15f), strokeWidth = 2.dp.toPx())
                    if (r.nextFloat() > 0.5f) {
                        drawCircle(Color.Red.copy(alpha = alpha), radius = 2.dp.toPx(), center = Offset(baseX + w/2, currentY - h * 0.15f))
                    }
                }
            }
            
            // 1. Far Parallax Layer (Distant Silhouettes/Mountains)
            val farOffset = (state.distance * 0.03f) % 1f
            for (wrap in 0..1) {
                val baseOffsetX = (wrap - farOffset) * width
                val random = kotlin.random.Random(state.currentCity.cityName.hashCode() + 100)
                var currentX = 0f
                while (currentX < width) {
                    val w = width * (0.15f + random.nextFloat() * 0.2f)
                    val h = vanishingY * (0.4f + random.nextFloat() * 0.3f)
                    drawBuilding(baseOffsetX + currentX, vanishingY, w, h, state.currentCity.groundColor, 0.3f, random.nextInt(), 0)
                    currentX += w + width * 0.02f
                }
            }

            // 2. Mid Parallax Layer (City Landmarks)
            val skylineOffset = (state.distance * 0.08f) % 1f // Slow down skyline slightly
            for (wrap in 0..1) {
                val baseOffsetX = (wrap - skylineOffset) * width
                
                when (state.currentCity) {
                    City.NEW_YORK -> {
                        val random = kotlin.random.Random(state.currentCity.cityName.hashCode())
                        var currentX = 0f
                        while (currentX < width) {
                            if (currentX > width * 0.2f && currentX < width * 0.35f) {
                                currentX += width * 0.15f
                                continue
                            }
                            if (currentX > width * 0.7f && currentX < width * 0.85f) {
                                currentX += width * 0.15f
                                continue
                            }
                            val bWidth = width * (0.08f + random.nextFloat() * 0.1f)
                            val bHeight = vanishingY * (0.3f + random.nextFloat() * 0.5f)
                            drawBuilding(baseOffsetX + currentX, vanishingY, bWidth, bHeight, state.currentCity.groundColor, 0.7f, random.nextInt(), 1)
                            currentX += bWidth + width * 0.02f
                        }

                        // Empire State Building
                        val esbX = baseOffsetX + width * 0.25f
                        drawBuilding(esbX, vanishingY, width * 0.08f, vanishingY * 0.85f, state.currentCity.groundColor, 0.9f, 1, 2)
                        val esbColor = state.currentCity.groundColor.copy(alpha = 0.9f)
                        drawRect(esbColor, Offset(esbX, vanishingY - vanishingY * 0.7f), Size(width * 0.08f, vanishingY * 0.7f)) // Base
                        drawRect(esbColor, Offset(esbX + width * 0.01f, vanishingY - vanishingY * 0.85f), Size(width * 0.06f, vanishingY * 0.15f)) // Mid
                        drawRect(esbColor, Offset(esbX + width * 0.025f, vanishingY - vanishingY * 0.95f), Size(width * 0.03f, vanishingY * 0.1f)) // Top
                        drawLine(esbColor, Offset(esbX + width * 0.04f, vanishingY - vanishingY * 0.95f), Offset(esbX + width * 0.04f, vanishingY - vanishingY * 1.1f), strokeWidth = 4f) // Spire

                        // Statue of Liberty
                        val solX = baseOffsetX + width * 0.75f
                        val pedColor = state.currentCity.groundColor.copy(alpha = 0.7f)
                        val solColor = Color(0xFF81C784).copy(alpha = 0.8f) // Copper green
                        drawRect(pedColor, Offset(solX, vanishingY - vanishingY * 0.2f), Size(width * 0.08f, vanishingY * 0.2f)) // Pedestal
                        drawRect(solColor, Offset(solX + width * 0.02f, vanishingY - vanishingY * 0.5f), Size(width * 0.04f, vanishingY * 0.3f)) // Body
                        drawRect(solColor, Offset(solX + width * 0.025f, vanishingY - vanishingY * 0.58f), Size(width * 0.03f, vanishingY * 0.08f)) // Head
                        drawLine(solColor, Offset(solX + width * 0.05f, vanishingY - vanishingY * 0.45f), Offset(solX + width * 0.08f, vanishingY - vanishingY * 0.65f), strokeWidth = 8f) // Arm
                        drawCircle(Color(0xFFFFB300).copy(alpha = 0.9f), radius = 8f, center = Offset(solX + width * 0.08f, vanishingY - vanishingY * 0.65f)) // Torch
                    }
                    City.PARIS -> {
                        val random = kotlin.random.Random(state.currentCity.cityName.hashCode())
                        var currentX = 0f
                        while (currentX < width) {
                            if (currentX > width * 0.4f && currentX < width * 0.6f) {
                                currentX += width * 0.2f
                                continue
                            }
                            val bWidth = width * (0.08f + random.nextFloat() * 0.1f)
                            val bHeight = vanishingY * (0.2f + random.nextFloat() * 0.4f)
                            drawBuilding(baseOffsetX + currentX, vanishingY, bWidth, bHeight, state.currentCity.groundColor, 0.7f, random.nextInt(), 1)
                            currentX += bWidth + width * 0.02f
                        }

                        // Eiffel Tower
                        val etX = baseOffsetX + width * 0.45f
                        val etColor = state.currentCity.groundColor.copy(alpha = 0.9f)
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(etX, vanishingY)
                            lineTo(etX + width * 0.1f, vanishingY)
                            lineTo(etX + width * 0.06f, vanishingY - vanishingY * 0.4f)
                            lineTo(etX + width * 0.04f, vanishingY - vanishingY * 0.4f)
                            close()
                            
                            moveTo(etX + width * 0.04f, vanishingY - vanishingY * 0.4f)
                            lineTo(etX + width * 0.06f, vanishingY - vanishingY * 0.4f)
                            lineTo(etX + width * 0.055f, vanishingY - vanishingY * 0.8f)
                            lineTo(etX + width * 0.045f, vanishingY - vanishingY * 0.8f)
                            close()
                        }
                        drawPath(path, etColor)
                        drawLine(etColor, Offset(etX + width * 0.05f, vanishingY - vanishingY * 0.8f), Offset(etX + width * 0.05f, vanishingY - vanishingY * 0.95f), strokeWidth = 4f)
                    }
                    City.LONDON -> {
                        val random = kotlin.random.Random(state.currentCity.cityName.hashCode())
                        var currentX = 0f
                        while (currentX < width) {
                            if (currentX > width * 0.2f && currentX < width * 0.35f) {
                                currentX += width * 0.15f
                                continue
                            }
                            if (currentX > width * 0.65f && currentX < width * 0.85f) {
                                currentX += width * 0.2f
                                continue
                            }
                            val bWidth = width * (0.08f + random.nextFloat() * 0.1f)
                            val bHeight = vanishingY * (0.2f + random.nextFloat() * 0.4f)
                            drawBuilding(baseOffsetX + currentX, vanishingY, bWidth, bHeight, state.currentCity.groundColor, 0.7f, random.nextInt(), 1)
                            currentX += bWidth + width * 0.02f
                        }

                        // Big Ben
                        val bbX = baseOffsetX + width * 0.25f
                        val bbColor = state.currentCity.groundColor.copy(alpha = 0.9f)
                        drawBuilding(bbX, vanishingY, width * 0.06f, vanishingY * 0.7f, state.currentCity.groundColor, 0.9f, 2, 2)
                        drawRect(Color(0xFFFFF59D).copy(alpha = 0.9f), Offset(bbX + width * 0.01f, vanishingY - vanishingY * 0.65f), Size(width * 0.04f, width * 0.04f)) // Clock face
                        val bbPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(bbX - width * 0.01f, vanishingY - vanishingY * 0.7f)
                            lineTo(bbX + width * 0.07f, vanishingY - vanishingY * 0.7f)
                            lineTo(bbX + width * 0.03f, vanishingY - vanishingY * 0.85f)
                            close()
                        }
                        drawPath(bbPath, bbColor)

                        // London Eye
                        val leX = baseOffsetX + width * 0.75f
                        val leColor = state.currentCity.groundColor.copy(alpha = 0.8f)
                        drawLine(leColor, Offset(leX, vanishingY), Offset(leX, vanishingY - vanishingY * 0.4f), strokeWidth = 8f)
                        drawLine(leColor, Offset(leX - width * 0.05f, vanishingY), Offset(leX, vanishingY - vanishingY * 0.4f), strokeWidth = 8f)
                        drawLine(leColor, Offset(leX + width * 0.05f, vanishingY), Offset(leX, vanishingY - vanishingY * 0.4f), strokeWidth = 8f)
                        drawCircle(color = leColor, radius = vanishingY * 0.35f, center = Offset(leX, vanishingY - vanishingY * 0.4f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f))
                    }
                    City.TOKYO -> {
                        // Mt Fuji in background
                        val fujiColor = Color(0xFF5C6BC0).copy(alpha = 0.5f)
                        val fujiPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(baseOffsetX + width * 0.1f, vanishingY)
                            lineTo(baseOffsetX + width * 0.9f, vanishingY)
                            lineTo(baseOffsetX + width * 0.5f, vanishingY - vanishingY * 0.8f)
                            close()
                        }
                        drawPath(fujiPath, fujiColor)
                        val snowPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(baseOffsetX + width * 0.4f, vanishingY - vanishingY * 0.6f)
                            lineTo(baseOffsetX + width * 0.6f, vanishingY - vanishingY * 0.6f)
                            lineTo(baseOffsetX + width * 0.5f, vanishingY - vanishingY * 0.8f)
                            close()
                        }
                        drawPath(snowPath, Color.White.copy(alpha = 0.8f))

                        val random = kotlin.random.Random(state.currentCity.cityName.hashCode())
                        var currentX = 0f
                        while (currentX < width) {
                            if (currentX > width * 0.6f && currentX < width * 0.8f) {
                                currentX += width * 0.2f
                                continue
                            }
                            val bWidth = width * (0.06f + random.nextFloat() * 0.08f)
                            val bHeight = vanishingY * (0.3f + random.nextFloat() * 0.6f)
                            drawBuilding(baseOffsetX + currentX, vanishingY, bWidth, bHeight, state.currentCity.groundColor, 0.7f, random.nextInt(), 2)
                            // Some extra neon lights
                            drawRect(
                                color = listOf(Color.Red, Color.Green, Color.Cyan, Color.Yellow).random(random).copy(alpha = 0.8f),
                                topLeft = Offset(baseOffsetX + currentX + bWidth * 0.2f, vanishingY - bHeight * 0.8f),
                                size = Size(bWidth * 0.6f, bHeight * 0.1f)
                            )
                            currentX += bWidth + width * 0.02f
                        }

                        // Tokyo Tower
                        val ttX = baseOffsetX + width * 0.7f
                        val ttColor = Color(0xFFE53935).copy(alpha = 0.9f)
                        val ttPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(ttX, vanishingY)
                            lineTo(ttX + width * 0.12f, vanishingY)
                            lineTo(ttX + width * 0.08f, vanishingY - vanishingY * 0.4f)
                            lineTo(ttX + width * 0.04f, vanishingY - vanishingY * 0.4f)
                            close()
                            
                            moveTo(ttX + width * 0.04f, vanishingY - vanishingY * 0.4f)
                            lineTo(ttX + width * 0.08f, vanishingY - vanishingY * 0.4f)
                            lineTo(ttX + width * 0.07f, vanishingY - vanishingY * 0.9f)
                            lineTo(ttX + width * 0.05f, vanishingY - vanishingY * 0.9f)
                            close()
                        }
                        drawPath(ttPath, ttColor)
                        drawLine(ttColor, Offset(ttX + width * 0.06f, vanishingY - vanishingY * 0.9f), Offset(ttX + width * 0.06f, vanishingY - vanishingY * 1.1f), strokeWidth = 4f)
                    }
                    City.CAIRO -> {
                        val pyrColor = state.currentCity.groundColor.copy(alpha = 0.8f)
                        val pyrPath1 = androidx.compose.ui.graphics.Path().apply {
                            moveTo(baseOffsetX + width * 0.1f, vanishingY)
                            lineTo(baseOffsetX + width * 0.5f, vanishingY)
                            lineTo(baseOffsetX + width * 0.3f, vanishingY - vanishingY * 0.6f)
                            close()
                        }
                        drawPath(pyrPath1, pyrColor)
                        
                        val pyrPath2 = androidx.compose.ui.graphics.Path().apply {
                            moveTo(baseOffsetX + width * 0.4f, vanishingY)
                            lineTo(baseOffsetX + width * 0.8f, vanishingY)
                            lineTo(baseOffsetX + width * 0.6f, vanishingY - vanishingY * 0.5f)
                            close()
                        }
                        drawPath(pyrPath2, pyrColor.copy(alpha = 0.7f))

                        val pyrPath3 = androidx.compose.ui.graphics.Path().apply {
                            moveTo(baseOffsetX + width * 0.7f, vanishingY)
                            lineTo(baseOffsetX + width * 0.95f, vanishingY)
                            lineTo(baseOffsetX + width * 0.825f, vanishingY - vanishingY * 0.3f)
                            close()
                        }
                        drawPath(pyrPath3, pyrColor.copy(alpha = 0.9f))
                    }
                    City.SYDNEY -> {
                        // Opera house
                        val ohX = baseOffsetX + width * 0.3f
                        val ohColor = Color.White.copy(alpha = 0.9f)
                        val ohPath1 = androidx.compose.ui.graphics.Path().apply {
                            moveTo(ohX, vanishingY)
                            quadraticBezierTo(ohX + width * 0.05f, vanishingY - vanishingY * 0.5f, ohX + width * 0.15f, vanishingY)
                            close()
                        }
                        drawPath(ohPath1, ohColor)
                        val ohPath2 = androidx.compose.ui.graphics.Path().apply {
                            moveTo(ohX + width * 0.1f, vanishingY)
                            quadraticBezierTo(ohX + width * 0.15f, vanishingY - vanishingY * 0.6f, ohX + width * 0.25f, vanishingY)
                            close()
                        }
                        drawPath(ohPath2, ohColor)
                        val ohPath3 = androidx.compose.ui.graphics.Path().apply {
                            moveTo(ohX + width * 0.2f, vanishingY)
                            quadraticBezierTo(ohX + width * 0.25f, vanishingY - vanishingY * 0.4f, ohX + width * 0.35f, vanishingY)
                            close()
                        }
                        drawPath(ohPath3, ohColor)

                        // Bridge
                        val brColor = state.currentCity.groundColor.copy(alpha = 0.8f)
                        val brPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(baseOffsetX + width * 0.5f, vanishingY)
                            quadraticBezierTo(baseOffsetX + width * 0.75f, vanishingY - vanishingY * 0.8f, baseOffsetX + width * 1.0f, vanishingY)
                        }
                        drawPath(brPath, color = brColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f))
                        drawLine(brColor, Offset(baseOffsetX + width * 0.5f, vanishingY - vanishingY * 0.1f), Offset(baseOffsetX + width * 1.0f, vanishingY - vanishingY * 0.1f), strokeWidth = 12f)
                    }
                    else -> {
                        val random = kotlin.random.Random(state.currentCity.cityName.hashCode())
                        var currentX = 0f
                        while (currentX < width) {
                            val bWidth = width * (0.1f + random.nextFloat() * 0.15f)
                            val bHeight = vanishingY * (0.3f + random.nextFloat() * 0.6f)
                            drawBuilding(baseOffsetX + currentX, vanishingY, bWidth, bHeight, state.currentCity.groundColor, 0.7f, random.nextInt(), 1)
                            currentX += bWidth + width * 0.02f
                        }
                    }
                }
            }
            
            // 3. Near Parallax Layer (Foreground Details)
            val nearOffset = (state.distance * 0.18f) % 1f
            for (wrap in 0..1) {
                val baseOffsetX = (wrap - nearOffset) * width
                val random = kotlin.random.Random(state.currentCity.cityName.hashCode() + 200)
                var currentX = 0f
                while (currentX < width) {
                    val w = width * (0.05f + random.nextFloat() * 0.08f)
                    val h = vanishingY * (0.15f + random.nextFloat() * 0.15f)
                    drawBuilding(baseOffsetX + currentX, vanishingY, w, h, state.currentCity.groundColor, 0.95f, random.nextInt(), 2)
                    currentX += w + width * 0.15f
                }
            }

            // Ground
            drawRect(
                color = state.currentCity.groundColor,
                topLeft = Offset(0f, vanishingY),
                size = Size(width, height - vanishingY)
            )

            // Background scrolling (Ground grid)
            for (i in 0 until 15) {
                val offset = (state.distance + i * (1.5f / 15f)) % 1.5f
                val yRatio = offset * offset * offset // Cubic for stronger perspective
                val lineY = vanishingY + yRatio * (height - vanishingY)
                
                drawLine(
                    color = Color.Black.copy(alpha = 0.15f),
                    start = Offset(0f, lineY),
                    end = Offset(width, lineY),
                    strokeWidth = (3.dp.toPx() * offset).coerceAtLeast(1f)
                )
            }

            val roadWidthAtBottom = width * 0.7f
            val roadStartXAtBottom = (width - roadWidthAtBottom) / 2f
            val laneWidthAtBottom = roadWidthAtBottom / 3f
            
            // Draw Sidewalks
            val sidewalkColor = Color.LightGray.copy(alpha = 0.8f)
            val curbColor = Color.DarkGray.copy(alpha = 0.9f)
            
            val leftSidewalkPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, height)
                lineTo(roadStartXAtBottom, height)
                lineTo(vanishingX, vanishingY)
                lineTo(0f, vanishingY)
                close()
            }
            drawPath(leftSidewalkPath, sidewalkColor)

            val rightSidewalkPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(width, height)
                lineTo(width - roadStartXAtBottom, height)
                lineTo(vanishingX, vanishingY)
                lineTo(width, vanishingY)
                close()
            }
            drawPath(rightSidewalkPath, sidewalkColor)
            
            // Draw Curbs
            drawLine(
                color = curbColor,
                start = Offset(vanishingX, vanishingY),
                end = Offset(roadStartXAtBottom, height),
                strokeWidth = 8.dp.toPx()
            )
            drawLine(
                color = curbColor,
                start = Offset(vanishingX, vanishingY),
                end = Offset(width - roadStartXAtBottom, height),
                strokeWidth = 8.dp.toPx()
            )

            // Passing sidewalk tiles and bollards (side rails)
            val railColor = Color(0xFF607D8B)
            for (i in 0 until 12) {
                val offset = (state.distance * 2f + i * (1.5f / 12f)) % 1.5f
                val perspective = offset * (0.6f + 0.4f * offset)
                val lineY = vanishingY + perspective * (height - vanishingY)
                val leftX = vanishingX + (roadStartXAtBottom - vanishingX) * perspective
                val rightX = vanishingX + ((width - roadStartXAtBottom) - vanishingX) * perspective
                
                // Left sidewalk tile divider
                drawLine(
                    color = Color.Gray.copy(alpha = 0.6f),
                    start = Offset(leftX - (width * 0.15f * perspective), lineY),
                    end = Offset(leftX, lineY),
                    strokeWidth = 2.dp.toPx() * perspective
                )
                // Right sidewalk tile divider
                drawLine(
                    color = Color.Gray.copy(alpha = 0.6f),
                    start = Offset(rightX, lineY),
                    end = Offset(rightX + (width * 0.15f * perspective), lineY),
                    strokeWidth = 2.dp.toPx() * perspective
                )

                // Side Rails/Bollards
                if (i % 2 == 0) {
                    val bollardHeight = 40.dp.toPx() * perspective
                    val bollardWidth = 14.dp.toPx() * perspective
                    // Left Rail
                    drawRoundRect(
                        color = railColor,
                        topLeft = Offset(leftX - bollardWidth * 1.5f, lineY - bollardHeight),
                        size = Size(bollardWidth, bollardHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx() * perspective)
                    )
                    // Right Rail
                    drawRoundRect(
                        color = railColor,
                        topLeft = Offset(rightX + bollardWidth * 0.5f, lineY - bollardHeight),
                        size = Size(bollardWidth, bollardHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx() * perspective)
                    )
                }
            }
            
            // 3D Procedural Buildings along the road
            val zStart = state.distance.toInt()
            // Draw from back to front, including a few behind the camera so they slide off smoothly
            for (z in (zStart + 15) downTo (zStart - 2)) {
                // Building Z distance from camera
                val relZFront = (z - state.distance)
                if (relZFront > 15f) continue
                
                val cityIndexForZ = ((z / 100) % City.entries.size)
                val buildingCity = City.entries[cityIndexForZ]
                val bRandom = kotlin.random.Random(z + buildingCity.name.hashCode())
                
                if (bRandom.nextFloat() > 0.85f) continue // 15% chance for a completely empty slot
                
                val yRatioFront = (1f - (relZFront / 15f))
                val perspFront = yRatioFront * (0.6f + 0.4f * yRatioFront)
                
                val relZBack = ((z + 1) - state.distance)
                val yRatioBack = (1f - (relZBack / 15f))
                val perspBack = yRatioBack * (0.6f + 0.4f * yRatioBack)

                val baseBuildingColors = listOf(
                    Color(0xFFB0BEC5), // Light Gray
                    Color(0xFF78909C), // Gray
                    Color(0xFF546E7A), // Dark Gray
                    Color(0xFF8D6E63), // Brown
                    Color(0xFFD7CCC8), // Beige
                    Color(0xFF263238), // Dark Glass
                    Color(0xFFE0E0E0), // White
                    Color(0xFF455A64)  // Slate
                )

                for (side in listOf(-1, 1)) {
                    val spawnType = bRandom.nextFloat()
                    if (spawnType > 0.95f) continue // Empty space on this side

                    val dx = (vanishingX - roadStartXAtBottom + width * 0.05f)
                    val sxFront = vanishingX + side * dx * perspFront
                    val syFront = vanishingY + perspFront * (height - vanishingY)
                    
                    val sxBack = vanishingX + side * dx * perspBack
                    val syBack = vanishingY + perspBack * (height - vanishingY)

                    val fogAlpha = (1f - (relZFront / 15f)).coerceIn(0f, 1f)
                    fun blendWithSky(c: Color, mix: Float): Color {
                        return Color(
                            red = c.red * mix + buildingCity.skyColor.red * (1 - mix),
                            green = c.green * mix + buildingCity.skyColor.green * (1 - mix),
                            blue = c.blue * mix + buildingCity.skyColor.blue * (1 - mix),
                            alpha = c.alpha
                        )
                    }

                    if (spawnType < 0.15f) {
                        // NPC (Pedestrian) - Enhanced
                        val npcHeight = height * 0.08f * perspFront
                        val npcWidth = width * 0.02f * perspFront
                        val shirtColor = listOf(Color.Red, Color.Blue, Color(0xFF4CAF50), Color(0xFFFF9800), Color.White).random(bRandom)
                        val pantsColor = listOf(Color.DarkGray, Color(0xFF1565C0), Color(0xFF3E2723)).random(bRandom)
                        val hasHat = bRandom.nextFloat() > 0.5f
                        
                        // Shadow
                        drawOval(Color.Black.copy(alpha = 0.2f * fogAlpha), topLeft = Offset(sxFront - npcWidth * 0.8f, syFront - npcWidth * 0.3f), size = Size(npcWidth * 1.6f, npcWidth * 0.6f))
                        
                        // Body
                        drawRect(blendWithSky(shirtColor, fogAlpha), Offset(sxFront - npcWidth/2, syFront - npcHeight * 0.9f), Size(npcWidth, npcHeight * 0.6f))
                        
                        // Head
                        val headRadius = npcWidth * 0.6f
                        val headCenterY = syFront - npcHeight * 0.9f - headRadius * 0.5f
                        drawCircle(blendWithSky(Color(0xFFFFCC80), fogAlpha), radius = headRadius, center = Offset(sxFront, headCenterY))
                        
                        if (hasHat) {
                            val hatColor = listOf(Color.Black, Color.Red, Color.Blue).random(bRandom)
                            val hatPath = androidx.compose.ui.graphics.Path().apply {
                                moveTo(sxFront - headRadius * 1.2f, headCenterY)
                                lineTo(sxFront + headRadius * 1.2f, headCenterY)
                                lineTo(sxFront + headRadius * 0.5f, headCenterY - headRadius)
                                lineTo(sxFront - headRadius * 0.5f, headCenterY - headRadius)
                                close()
                            }
                            drawPath(hatPath, blendWithSky(hatColor, fogAlpha))
                        }

                        // Legs
                        drawRect(blendWithSky(pantsColor, fogAlpha), Offset(sxFront - npcWidth * 0.4f, syFront - npcHeight * 0.3f), Size(npcWidth * 0.35f, npcHeight * 0.3f))
                        drawRect(blendWithSky(pantsColor, fogAlpha), Offset(sxFront + npcWidth * 0.05f, syFront - npcHeight * 0.3f), Size(npcWidth * 0.35f, npcHeight * 0.3f))
                        
                    } else if (spawnType < 0.3f) {
                        // Tree - Enhanced
                        val trunkHeight = height * 0.12f * perspFront
                        val trunkWidth = width * 0.015f * perspFront
                        val leavesRadius = height * 0.07f * perspFront
                        
                        // Shadow
                        drawOval(Color.Black.copy(alpha = 0.2f * fogAlpha), topLeft = Offset(sxFront - trunkWidth * 2f, syFront - trunkWidth), size = Size(trunkWidth * 4f, trunkWidth * 2f))
                        
                        drawRect(blendWithSky(Color(0xFF5D4037), fogAlpha), Offset(sxFront - trunkWidth/2, syFront - trunkHeight), Size(trunkWidth, trunkHeight)) // Trunk
                        
                        val leafColor = blendWithSky(Color(0xFF2E7D32), fogAlpha)
                        val leafHighlight = blendWithSky(Color(0xFF4CAF50), fogAlpha)
                        
                        val cy = syFront - trunkHeight - leavesRadius * 0.2f
                        drawCircle(leafColor, radius = leavesRadius, center = Offset(sxFront, cy))
                        drawCircle(leafColor, radius = leavesRadius * 0.8f, center = Offset(sxFront - leavesRadius * 0.6f, cy + leavesRadius * 0.3f))
                        drawCircle(leafColor, radius = leavesRadius * 0.8f, center = Offset(sxFront + leavesRadius * 0.6f, cy + leavesRadius * 0.3f))
                        drawCircle(leafColor, radius = leavesRadius * 0.9f, center = Offset(sxFront, cy - leavesRadius * 0.5f))
                        
                        // Highlights
                        drawCircle(leafHighlight, radius = leavesRadius * 0.4f, center = Offset(sxFront - leavesRadius * 0.2f, cy - leavesRadius * 0.6f))
                        
                    } else if (spawnType < 0.4f) {
                        // Street Light - Enhanced
                        val poleHeight = height * 0.25f * perspFront
                        val poleWidth = width * 0.008f * perspFront
                        val armLength = width * 0.04f * perspFront
                        
                        val poleColor = blendWithSky(Color(0xFF424242), fogAlpha)
                        drawRect(poleColor, Offset(sxFront - poleWidth/2, syFront - poleHeight), Size(poleWidth, poleHeight)) // Pole
                        
                        // Arm
                        val armPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(sxFront, syFront - poleHeight + poleWidth)
                            quadraticBezierTo(sxFront - side * armLength * 0.5f, syFront - poleHeight - armLength * 0.2f, sxFront - side * armLength, syFront - poleHeight)
                        }
                        drawPath(armPath, poleColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = poleWidth))
                        
                        // Light fixture
                        val lightColor = blendWithSky(Color(0xFFFFF9C4), fogAlpha) // lit up
                        drawRoundRect(
                            color = poleColor,
                            topLeft = Offset(sxFront - side * armLength - poleWidth * 1.5f, syFront - poleHeight - poleWidth),
                            size = Size(poleWidth * 3f, poleWidth * 1.5f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(poleWidth)
                        )
                        // Glow
                        drawCircle(lightColor.copy(alpha = 0.6f * fogAlpha), radius = poleWidth * 3f, center = Offset(sxFront - side * armLength, syFront - poleHeight))
                        drawCircle(Color.White.copy(alpha = 0.9f * fogAlpha), radius = poleWidth * 1f, center = Offset(sxFront - side * armLength, syFront - poleHeight))
                        
                    } else if (spawnType < 0.45f) {
                        // Animal (Dog) - Enhanced
                        val dogHeight = height * 0.045f * perspFront
                        val dogWidth = width * 0.05f * perspFront
                        val color = blendWithSky(listOf(Color(0xFF795548), Color(0xFFD7CCC8), Color.Black).random(bRandom), fogAlpha)
                        
                        // Shadow
                        drawOval(Color.Black.copy(alpha = 0.2f * fogAlpha), topLeft = Offset(sxFront - dogWidth * 0.6f, syFront - dogWidth * 0.2f), size = Size(dogWidth * 1.2f, dogWidth * 0.4f))
                        
                        drawRect(color, Offset(sxFront - dogWidth/2, syFront - dogHeight), Size(dogWidth, dogHeight * 0.5f)) // Body
                        
                        val headCenterX = sxFront + side * dogWidth * 0.4f
                        val headCenterY = syFront - dogHeight * 0.9f
                        drawCircle(color, radius = dogHeight * 0.4f, center = Offset(headCenterX, headCenterY)) // Head
                        
                        // Ears
                        val earPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(headCenterX, headCenterY - dogHeight * 0.2f)
                            lineTo(headCenterX - dogWidth * 0.1f, headCenterY - dogHeight * 0.6f)
                            lineTo(headCenterX + dogWidth * 0.1f, headCenterY - dogHeight * 0.4f)
                            close()
                        }
                        drawPath(earPath, color)
                        
                        // Tail
                        val tailPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(sxFront - side * dogWidth * 0.4f, syFront - dogHeight * 0.9f)
                            quadraticBezierTo(sxFront - side * dogWidth * 0.6f, syFront - dogHeight * 1.2f, sxFront - side * dogWidth * 0.7f, syFront - dogHeight * 0.8f)
                        }
                        drawPath(tailPath, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = dogWidth * 0.08f, cap = androidx.compose.ui.graphics.StrokeCap.Round))

                        // Legs
                        drawRect(color, Offset(sxFront - dogWidth * 0.4f, syFront - dogHeight * 0.5f), Size(dogWidth * 0.15f, dogHeight * 0.5f))
                        drawRect(color, Offset(sxFront + dogWidth * 0.2f, syFront - dogHeight * 0.5f), Size(dogWidth * 0.15f, dogHeight * 0.5f))
                        
                    } else if (spawnType < 0.6f) {
                        // Store - Enhanced
                        val bHeightFactor = 0.35f + bRandom.nextFloat() * 0.3f
                        val bWidthFactor = 0.2f + bRandom.nextFloat() * 0.15f
                        val buildingColor = listOf(Color(0xFFFFF9C4), Color(0xFFFFCCBC), Color(0xFFC8E6C9), Color(0xFFE1BEE7)).random(bRandom)
                        val roofColor = listOf(Color(0xFFD32F2F), Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFFBC02D)).random(bRandom)
                        
                        val wFront = side * width * bWidthFactor * perspFront
                        val hFront = height * 0.6f * perspFront * bHeightFactor
                        val wBack = side * width * bWidthFactor * perspBack
                        val hBack = height * 0.6f * perspBack * bHeightFactor

                        val pSide = androidx.compose.ui.graphics.Path().apply {
                            moveTo(sxFront, syFront)
                            lineTo(sxFront, syFront - hFront)
                            lineTo(sxBack, syBack - hBack)
                            lineTo(sxBack, syBack)
                            close()
                        }
                        val pFront = androidx.compose.ui.graphics.Path().apply {
                            moveTo(sxFront, syFront)
                            lineTo(sxFront + wFront, syFront)
                            lineTo(sxFront + wFront, syFront - hFront)
                            lineTo(sxFront, syFront - hFront)
                            close()
                        }
                        
                        // Scalloped Awning
                        val awningY = syFront - hFront * 0.6f
                        val pAwningTop = androidx.compose.ui.graphics.Path().apply {
                            moveTo(sxFront, awningY)
                            lineTo(sxFront + wFront, awningY)
                            lineTo(sxFront + wFront + side * 15f*perspFront, syFront - hFront * 0.4f)
                            lineTo(sxFront - side * 15f*perspFront, syFront - hFront * 0.4f)
                            close()
                        }
                        
                        drawPath(pSide, blendWithSky(buildingColor.copy(alpha = 0.8f), fogAlpha))
                        drawPath(pFront, blendWithSky(buildingColor, fogAlpha))
                        drawPath(pAwningTop, blendWithSky(roofColor, fogAlpha))
                        
                        val awningFrontY = syFront - hFront * 0.4f
                        val awningDrop = hFront * 0.05f
                        val scallops = 5
                        val scallopW = (wFront + side * 30f*perspFront) / scallops
                        val startX = sxFront - side * 15f*perspFront
                        for (i in 0 until scallops) {
                            val px = startX + i * scallopW
                            val color = if (i % 2 == 0) roofColor else Color.White
                            val pScallop = androidx.compose.ui.graphics.Path().apply {
                                moveTo(px, awningFrontY)
                                lineTo(px + scallopW, awningFrontY)
                                lineTo(px + scallopW, awningFrontY + awningDrop)
                                quadraticBezierTo(px + scallopW / 2, awningFrontY + awningDrop * 2, px, awningFrontY + awningDrop)
                                close()
                            }
                            drawPath(pScallop, blendWithSky(color, fogAlpha))
                        }
                        
                        // Big store window
                        val leftX = if (side == -1) sxFront + wFront else sxFront
                        val absW = kotlin.math.abs(wFront)
                        drawRect(
                            blendWithSky(Color(0xFFE0F7FA), fogAlpha),
                            topLeft = Offset(leftX + absW * 0.1f, syFront - hFront * 0.35f),
                            size = Size(absW * 0.8f, hFront * 0.3f)
                        )
                        // Window frame
                        drawRect(
                            blendWithSky(Color.DarkGray, fogAlpha),
                            topLeft = Offset(leftX + absW * 0.1f, syFront - hFront * 0.35f),
                            size = Size(absW * 0.8f, hFront * 0.3f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx() * perspFront)
                        )
                        
                        // Store Sign
                        val signW = absW * 0.6f
                        val signH = hFront * 0.15f
                        drawRoundRect(
                            color = blendWithSky(Color.White, fogAlpha),
                            topLeft = Offset(leftX + (absW - signW) / 2, syFront - hFront * 0.9f),
                            size = Size(signW, signH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx() * perspFront)
                        )
                        
                    } else {
                        // Building - Enhanced
                        val isSkyscraper = bRandom.nextFloat() > 0.7f
                        val bHeightFactor = if (isSkyscraper) 1.5f + bRandom.nextFloat() * 2.0f else 0.5f + bRandom.nextFloat() * 1.0f
                        val bWidthFactor = 0.2f + bRandom.nextFloat() * 0.25f
                        
                        val buildingColor = baseBuildingColors[bRandom.nextInt(baseBuildingColors.size)]
                        val isGlass = buildingColor == Color(0xFF263238)
                        val roofColor = if (isGlass) Color(0xFF37474F) else Color(0xFF424242)
                        
                        val wFront = side * width * bWidthFactor * perspFront
                        val hFront = height * 0.5f * perspFront * bHeightFactor
                        val wBack = side * width * bWidthFactor * perspBack
                        val hBack = height * 0.5f * perspBack * bHeightFactor

                        val pSide = androidx.compose.ui.graphics.Path().apply {
                            moveTo(sxFront, syFront)
                            lineTo(sxFront, syFront - hFront)
                            lineTo(sxBack, syBack - hBack)
                            lineTo(sxBack, syBack)
                            close()
                        }
                        val pFront = androidx.compose.ui.graphics.Path().apply {
                            moveTo(sxFront, syFront)
                            lineTo(sxFront + wFront, syFront)
                            lineTo(sxFront + wFront, syFront - hFront)
                            lineTo(sxFront, syFront - hFront)
                            close()
                        }
                        val pTop = androidx.compose.ui.graphics.Path().apply {
                            moveTo(sxFront, syFront - hFront)
                            lineTo(sxFront + wFront, syFront - hFront)
                            lineTo(sxBack + wBack, syBack - hBack)
                            lineTo(sxBack, syBack - hBack)
                            close()
                        }
                        
                        drawPath(pSide, blendWithSky(if (side == -1) buildingColor.copy(alpha = 0.8f) else buildingColor.copy(alpha = 0.95f), fogAlpha))
                        drawPath(pFront, blendWithSky(buildingColor, fogAlpha))
                        drawPath(pTop, blendWithSky(roofColor, fogAlpha))
                        
                        // Roof details (Antennas / AC Units)
                        if (isSkyscraper && bRandom.nextFloat() > 0.5f) {
                            val cx = sxFront + wFront * 0.5f
                            val cy = syFront - hFront
                            val antH = hFront * 0.15f
                            drawLine(blendWithSky(Color.LightGray, fogAlpha), Offset(cx, cy), Offset(cx, cy - antH), strokeWidth = 2.dp.toPx() * perspFront)
                            drawCircle(blendWithSky(Color.Red, fogAlpha), radius = 3.dp.toPx() * perspFront, center = Offset(cx, cy - antH))
                        } else if (bRandom.nextFloat() > 0.4f) {
                            val cx = sxFront + wFront * 0.3f
                            val cy = syFront - hFront
                            val acW = kotlin.math.abs(wFront) * 0.2f
                            val acH = hFront * 0.05f
                            drawRect(blendWithSky(Color.Gray, fogAlpha), Offset(if (side == -1) cx else cx - acW, cy - acH), Size(acW, acH))
                        }

                        val windowLightColor = if (isGlass) Color(0xFFE0F7FA) else listOf(Color(0xFFFFF59D), Color(0xFFFFE082), Color(0xFF80DEEA)).random(bRandom)
                        val windowColorMixed = blendWithSky(windowLightColor, fogAlpha)
                        val rows = (10 * bHeightFactor).toInt().coerceAtLeast(2)
                        val cols = (5 * (bWidthFactor / 0.15f)).toInt().coerceAtLeast(2)
                        val wx = if (side == -1) sxFront + wFront else sxFront
                        val absW = kotlin.math.abs(wFront)
                        val winW = absW * 0.7f / cols
                        val winH = hFront * 0.75f / rows
                        val padX = (absW - winW * cols) / (cols + 1)
                        val padY = (hFront * 0.85f - winH * rows) / (rows + 1)
                        
                        for (r in 0 until rows) {
                            for (c in 0 until cols) {
                                val winRandom = kotlin.random.Random(z + side * 100 + r * 10 + c)
                                if (winRandom.nextFloat() > (if (isGlass) 0.1f else 0.35f)) {
                                    val isLit = winRandom.nextFloat() > 0.6f
                                    val finalWinColor = if (isLit) windowColorMixed else blendWithSky(Color.Black.copy(alpha = 0.6f), fogAlpha)
                                    drawRect(
                                        color = finalWinColor,
                                        topLeft = Offset(wx + padX + c * (winW + padX), syFront - hFront + padY + r * (winH + padY)),
                                        size = Size(winW, winH)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Lane dividers
            for (i in 1..2) {
                val bottomX = roadStartXAtBottom + i * laneWidthAtBottom
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(vanishingX, vanishingY),
                    end = Offset(bottomX, height),
                    strokeWidth = 4.dp.toPx()
                )
            }

            // Speed lines
            for (i in 0 until 8) {
                val yOffset = ((state.distance * 2f) + i * (1.5f / 8f)) % 1.5f
                val perspective = yOffset * (0.6f + 0.4f * yOffset)
                val lineY = vanishingY + perspective * (height - vanishingY)
                val leftX = vanishingX + (roadStartXAtBottom - vanishingX) * perspective
                val rightX = vanishingX + ((width - roadStartXAtBottom) - vanishingX) * perspective
                
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(leftX, lineY),
                    end = Offset(rightX, lineY),
                    strokeWidth = 2.dp.toPx() * perspective
                )
            }

            // Draw Obstacles
            for (obs in state.obstacles.sortedBy { it.yRatio }) {
                if (obs.yRatio < 0) continue
                val laneCenter = roadStartXAtBottom + obs.lane * laneWidthAtBottom + laneWidthAtBottom / 2f
                
                val perspective = (obs.yRatio * obs.yRatio * obs.yRatio) // Allow > 1 to slide off screen
                val scale = perspective.coerceAtMost(5f) // Prevent infinite scaling
                val screenY = vanishingY + perspective * (height - vanishingY)
                val screenX = vanishingX + (laneCenter - vanishingX) * scale
                
                val obsWidth = 80.dp.toPx() * scale
                val obsHeight = 80.dp.toPx() * scale
                
                // Fade in smoothly at horizon
                val alpha = (obs.yRatio * 5f).coerceIn(0f, 1f)
                
                val colType = getCollisionType(obs.type)
                
                if (colType == ObstacleCollisionType.HIGH) {
                    // Floating obstacle (slide under) - Enhanced
                    val floatY = screenY - obsHeight * 0.9f
                    
                    if (obs.type == "Traffic Light") {
                        val poleColor = Color(0xFF424242).copy(alpha = alpha)
                        val boxColor = Color(0xFF212121).copy(alpha = alpha)
                        
                        // Pole
                        drawRect(poleColor, Offset(screenX + obsWidth * 0.4f, floatY), Size(obsWidth * 0.1f, screenY - floatY))
                        // Overhang arm
                        drawRect(poleColor, Offset(screenX - obsWidth * 0.6f, floatY), Size(obsWidth * 1.0f, obsWidth * 0.1f))
                        
                        // Light Box
                        val boxW = obsWidth * 0.4f
                        val boxH = obsHeight * 0.7f
                        drawRoundRect(
                            color = boxColor,
                            topLeft = Offset(screenX - boxW / 2, floatY + obsWidth * 0.1f),
                            size = Size(boxW, boxH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(obsWidth * 0.05f)
                        )
                        // Lights
                        val lightRadius = boxW * 0.35f
                        val lightAlpha = if (state.distance % 200 > 100) 1f else 0.3f // Blinking red light
                        drawCircle(Color.Red.copy(alpha = lightAlpha * alpha), radius = lightRadius, center = Offset(screenX, floatY + obsWidth * 0.1f + boxH * 0.25f))
                        drawCircle(Color.Yellow.copy(alpha = 0.3f * alpha), radius = lightRadius, center = Offset(screenX, floatY + obsWidth * 0.1f + boxH * 0.5f))
                        drawCircle(Color.Green.copy(alpha = 0.3f * alpha), radius = lightRadius, center = Offset(screenX, floatY + obsWidth * 0.1f + boxH * 0.75f))
                        
                    } else if (obs.type == "Bridge Sign") {
                        val poleColor = Color.DarkGray.copy(alpha = alpha)
                        val signColor = Color(0xFF1B5E20).copy(alpha = alpha)
                        
                        // Poles
                        drawRect(poleColor, Offset(screenX - obsWidth * 0.9f, floatY), Size(obsWidth * 0.15f, screenY - floatY))
                        drawRect(poleColor, Offset(screenX + obsWidth * 0.75f, floatY), Size(obsWidth * 0.15f, screenY - floatY))
                        
                        // Sign Board
                        drawRect(signColor, Offset(screenX - obsWidth, floatY - obsHeight * 0.5f), Size(obsWidth * 2f, obsHeight * 0.6f))
                        
                        // Text Lines (Faux text)
                        drawRect(Color.White.copy(alpha = alpha * 0.8f), Offset(screenX - obsWidth * 0.5f, floatY - obsHeight * 0.3f), Size(obsWidth, obsHeight * 0.1f))
                        drawRect(Color.White.copy(alpha = alpha * 0.8f), Offset(screenX - obsWidth * 0.3f, floatY - obsHeight * 0.1f), Size(obsWidth * 0.6f, obsHeight * 0.1f))
                    } else if (obs.type == "Scaffolding") {
                        val pipeColor = Color.Gray.copy(alpha = alpha)
                        val boardColor = Color(0xFF8D6E63).copy(alpha = alpha)
                        
                        // Vertical pipes
                        drawRect(pipeColor, Offset(screenX - obsWidth * 0.8f, floatY - obsHeight * 0.5f), Size(obsWidth * 0.1f, screenY - floatY + obsHeight * 0.5f))
                        drawRect(pipeColor, Offset(screenX + obsWidth * 0.7f, floatY - obsHeight * 0.5f), Size(obsWidth * 0.1f, screenY - floatY + obsHeight * 0.5f))
                        
                        // Horizontal boards
                        drawRect(boardColor, Offset(screenX - obsWidth, floatY - obsHeight * 0.4f), Size(obsWidth * 2f, obsHeight * 0.1f))
                        drawRect(boardColor, Offset(screenX - obsWidth, floatY), Size(obsWidth * 2f, obsHeight * 0.1f))
                        
                        // Cross braces
                        val pBrace1 = androidx.compose.ui.graphics.Path().apply {
                            moveTo(screenX - obsWidth * 0.8f, floatY - obsHeight * 0.4f)
                            lineTo(screenX + obsWidth * 0.8f, floatY)
                        }
                        drawPath(pBrace1, pipeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = obsWidth * 0.05f))
                        val pBrace2 = androidx.compose.ui.graphics.Path().apply {
                            moveTo(screenX + obsWidth * 0.8f, floatY - obsHeight * 0.4f)
                            lineTo(screenX - obsWidth * 0.8f, floatY)
                        }
                        drawPath(pBrace2, pipeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = obsWidth * 0.05f))
                    } else {
                        // Generic High Obstacle
                        val color = Color(0xFF795548).copy(alpha = alpha)
                        val leftX = screenX - obsWidth * 0.8f
                        val rightX = screenX + obsWidth * 0.8f
                        val topY = floatY - obsHeight * 0.4f
                        val dx = (vanishingX - screenX) * 0.15f
                        val dy = (vanishingY - topY) * 0.15f
                        
                        // Support pole
                        drawRect(Color.DarkGray.copy(alpha = alpha), Offset(screenX - obsWidth * 0.1f, floatY), Size(obsWidth * 0.2f, screenY - floatY))
                        
                        // Side Face
                        val bottomDy = (vanishingY - (topY + obsHeight * 0.6f)) * 0.15f
                        if (dx > 0) { // Right side visible
                            val pSide = androidx.compose.ui.graphics.Path().apply {
                                moveTo(rightX, topY)
                                lineTo(rightX + dx, topY + dy)
                                lineTo(rightX + dx, topY + obsHeight * 0.6f + bottomDy)
                                lineTo(rightX, topY + obsHeight * 0.6f)
                                close()
                            }
                            drawPath(pSide, color.copy(alpha = alpha * 0.7f))
                        } else { // Left side visible
                            val pSide = androidx.compose.ui.graphics.Path().apply {
                                moveTo(leftX, topY)
                                lineTo(leftX + dx, topY + dy)
                                lineTo(leftX + dx, topY + obsHeight * 0.6f + bottomDy)
                                lineTo(leftX, topY + obsHeight * 0.6f)
                                close()
                            }
                            drawPath(pSide, color.copy(alpha = alpha * 0.7f))
                        }
                        
                        // Top Face
                        val pTop = androidx.compose.ui.graphics.Path().apply {
                            moveTo(leftX, topY)
                            lineTo(rightX, topY)
                            lineTo(rightX + dx, topY + dy)
                            lineTo(leftX + dx, topY + dy)
                            close()
                        }
                        drawPath(pTop, color.copy(alpha = alpha * 0.5f))
                        
                        // Sign/Lantern Front Face
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(leftX, topY),
                            size = Size(obsWidth * 1.6f, obsHeight * 0.6f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(obsWidth * 0.1f)
                        )
                    }
                    
                } else if (colType == ObstacleCollisionType.LOW) {
                    // Small obstacle on ground (jump over) - Enhanced
                    if (obs.type == "Hotdog Stand" || obs.type == "Cafe Table") {
                        val baseColor = if (obs.type == "Hotdog Stand") Color(0xFFF44336).copy(alpha = alpha) else Color(0xFF795548).copy(alpha = alpha)
                        val topColor = if (obs.type == "Hotdog Stand") Color(0xFFFFEB3B).copy(alpha = alpha) else Color(0xFFD7CCC8).copy(alpha = alpha)
                        
                        // Shadow
                        drawOval(Color.Black.copy(alpha = 0.3f * alpha), topLeft = Offset(screenX - obsWidth, screenY - obsHeight * 0.2f), size = Size(obsWidth * 2f, obsHeight * 0.4f))
                        
                        // Base / Table Leg
                        if (obs.type == "Hotdog Stand") {
                            val hLeftX = screenX - obsWidth * 0.7f
                            val hRightX = screenX + obsWidth * 0.7f
                            val hTopY = screenY - obsHeight * 0.6f
                            val hDx = (vanishingX - screenX) * 0.2f
                            val hDy = (vanishingY - hTopY) * 0.2f
                            
                            // Side Face
                            val hBottomDy = (vanishingY - screenY) * 0.2f
                            if (hDx > 0) {
                                val pSide = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(hRightX, hTopY)
                                    lineTo(hRightX + hDx, hTopY + hDy)
                                    lineTo(hRightX + hDx, screenY + hBottomDy)
                                    lineTo(hRightX, screenY)
                                    close()
                                }
                                drawPath(pSide, baseColor.copy(alpha = alpha * 0.7f))
                            } else {
                                val pSide = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(hLeftX, hTopY)
                                    lineTo(hLeftX + hDx, hTopY + hDy)
                                    lineTo(hLeftX + hDx, screenY + hBottomDy)
                                    lineTo(hLeftX, screenY)
                                    close()
                                }
                                drawPath(pSide, baseColor.copy(alpha = alpha * 0.7f))
                            }
                            
                            // Top Face
                            val pTop = androidx.compose.ui.graphics.Path().apply {
                                moveTo(hLeftX, hTopY)
                                lineTo(hRightX, hTopY)
                                lineTo(hRightX + hDx, hTopY + hDy)
                                lineTo(hLeftX + hDx, hTopY + hDy)
                                close()
                            }
                            drawPath(pTop, baseColor.copy(alpha = alpha * 0.5f))

                            // Front Face
                            drawRoundRect(baseColor, Offset(hLeftX, hTopY), Size(obsWidth * 1.4f, obsHeight * 0.6f), androidx.compose.ui.geometry.CornerRadius(obsWidth * 0.1f))
                            
                            // Umbrella
                            val umbrellaPath = androidx.compose.ui.graphics.Path().apply {
                                moveTo(screenX - obsWidth, screenY - obsHeight * 1.2f)
                                lineTo(screenX + obsWidth, screenY - obsHeight * 1.2f)
                                lineTo(screenX, screenY - obsHeight * 1.6f)
                                close()
                            }
                            drawPath(umbrellaPath, topColor)
                            // Pole
                            drawRect(Color.Gray.copy(alpha = alpha), Offset(screenX - obsWidth * 0.05f, screenY - obsHeight * 1.2f), Size(obsWidth * 0.1f, obsHeight * 0.6f))
                        } else {
                            // Cafe Table
                            drawRect(baseColor, Offset(screenX - obsWidth * 0.1f, screenY - obsHeight * 0.5f), Size(obsWidth * 0.2f, obsHeight * 0.5f))
                            drawOval(topColor, Offset(screenX - obsWidth * 0.6f, screenY - obsHeight * 0.6f), Size(obsWidth * 1.2f, obsHeight * 0.2f))
                            // A cup
                            drawRect(Color.White.copy(alpha = alpha), Offset(screenX, screenY - obsHeight * 0.7f), Size(obsWidth * 0.15f, obsHeight * 0.15f))
                        }
                    } else if (obs.type == "Mailbox") {
                        val baseColor = Color(0xFF1976D2).copy(alpha = alpha) // Blue mailbox
                        
                        // Shadow
                        drawOval(Color.Black.copy(alpha = 0.3f * alpha), topLeft = Offset(screenX - obsWidth * 0.6f, screenY - obsHeight * 0.2f), size = Size(obsWidth * 1.2f, obsHeight * 0.4f))
                        
                        // Base
                        val mLeftX = screenX - obsWidth * 0.2f
                        val mRightX = screenX + obsWidth * 0.2f
                        val mTopY = screenY - obsHeight * 0.8f
                        val mDx = (vanishingX - screenX) * 0.1f
                        val mDy = (vanishingY - mTopY) * 0.1f
                        
                        // Side Face
                        val mBottomDy = (vanishingY - screenY) * 0.1f
                        if (mDx > 0) {
                            val pSide = androidx.compose.ui.graphics.Path().apply {
                                moveTo(mRightX, mTopY)
                                lineTo(mRightX + mDx, mTopY + mDy)
                                lineTo(mRightX + mDx, screenY + mBottomDy)
                                lineTo(mRightX, screenY)
                                close()
                            }
                            drawPath(pSide, Color.DarkGray.copy(alpha = alpha))
                        } else {
                            val pSide = androidx.compose.ui.graphics.Path().apply {
                                moveTo(mLeftX, mTopY)
                                lineTo(mLeftX + mDx, mTopY + mDy)
                                lineTo(mLeftX + mDx, screenY + mBottomDy)
                                lineTo(mLeftX, screenY)
                                close()
                            }
                            drawPath(pSide, Color.DarkGray.copy(alpha = alpha))
                        }
                        
                        // Front Face
                        drawRect(Color.Gray.copy(alpha = alpha), Offset(mLeftX, mTopY), Size(obsWidth * 0.4f, obsHeight * 0.8f))
                        
                        // Box
                        val pBox = androidx.compose.ui.graphics.Path().apply {
                            moveTo(screenX - obsWidth * 0.5f, screenY - obsHeight * 0.8f)
                            lineTo(screenX + obsWidth * 0.5f, screenY - obsHeight * 0.8f)
                            lineTo(screenX + obsWidth * 0.5f, screenY - obsHeight * 1.2f)
                            quadraticBezierTo(screenX, screenY - obsHeight * 1.5f, screenX - obsWidth * 0.5f, screenY - obsHeight * 1.2f)
                            close()
                        }
                        drawPath(pBox, baseColor)
                        drawRect(Color.White.copy(alpha = alpha), Offset(screenX - obsWidth * 0.3f, screenY - obsHeight * 1.1f), Size(obsWidth * 0.6f, obsHeight * 0.2f)) // Mail slot area
                        
                    } else if (obs.type == "Traffic Cone") {
                        val color = Color(0xFFFF5722).copy(alpha = alpha) // Orange
                        val pCone = androidx.compose.ui.graphics.Path().apply {
                            moveTo(screenX, screenY - obsHeight * 0.8f) // Top
                            lineTo(screenX + obsWidth * 0.4f, screenY) // Bottom right
                            lineTo(screenX - obsWidth * 0.4f, screenY) // Bottom left
                            close()
                        }
                        // Shadow
                        drawOval(Color.Black.copy(alpha = 0.3f * alpha), topLeft = Offset(screenX - obsWidth * 0.6f, screenY - obsHeight * 0.1f), size = Size(obsWidth * 1.2f, obsHeight * 0.2f))
                        // Base
                        drawOval(color, Offset(screenX - obsWidth * 0.5f, screenY - obsHeight * 0.1f), Size(obsWidth, obsHeight * 0.2f))
                        drawPath(pCone, color)
                        // White stripe
                        val pStripe = androidx.compose.ui.graphics.Path().apply {
                            moveTo(screenX - obsWidth * 0.2f, screenY - obsHeight * 0.4f)
                            lineTo(screenX + obsWidth * 0.2f, screenY - obsHeight * 0.4f)
                            lineTo(screenX + obsWidth * 0.25f, screenY - obsHeight * 0.3f)
                            lineTo(screenX - obsWidth * 0.25f, screenY - obsHeight * 0.3f)
                            close()
                        }
                        drawPath(pStripe, Color.White.copy(alpha = alpha))
                    } else {
                        // Hurdle (Low Obstacle)
                        val color = Color(0xFFFF9800).copy(alpha = alpha) // Orange
                        val barColor = Color.White.copy(alpha = alpha)
                        val postW = obsWidth * 0.15f
                        val hurdleH = obsHeight * 0.5f
                        val leftX = screenX - obsWidth * 0.7f
                        val rightX = screenX + obsWidth * 0.7f - postW
                        val topY = screenY - hurdleH
                        
                        val dx = (vanishingX - screenX) * 0.15f
                        val dy = (vanishingY - topY) * 0.15f
                        
                        // Shadow
                        drawOval(Color.Black.copy(alpha = 0.3f * alpha), topLeft = Offset(screenX - obsWidth * 0.8f, screenY - obsHeight * 0.1f), size = Size(obsWidth * 1.6f, obsHeight * 0.2f))
                        
                        // Left Post 3D Side
                        val pLeftSide = androidx.compose.ui.graphics.Path().apply {
                            moveTo(leftX, topY)
                            lineTo(leftX + dx, topY + dy)
                            lineTo(leftX + dx, screenY + dy)
                            lineTo(leftX, screenY)
                            close()
                        }
                        drawPath(pLeftSide, color.copy(alpha = alpha * 0.6f))
                        
                        // Right Post 3D Side
                        val pRightSide = androidx.compose.ui.graphics.Path().apply {
                            moveTo(rightX + postW, topY)
                            lineTo(rightX + postW + dx, topY + dy)
                            lineTo(rightX + postW + dx, screenY + dy)
                            lineTo(rightX + postW, screenY)
                            close()
                        }
                        drawPath(pRightSide, color.copy(alpha = alpha * 0.6f))
                        
                        // Posts Front
                        drawRect(color, Offset(leftX, topY), Size(postW, hurdleH))
                        drawRect(color, Offset(rightX, topY), Size(postW, hurdleH))
                        
                        // Horizontal Bar 3D Top
                        val barTopY = topY + hurdleH * 0.2f
                        val barH = hurdleH * 0.25f
                        val pBarTop = androidx.compose.ui.graphics.Path().apply {
                            moveTo(leftX, barTopY)
                            lineTo(rightX + postW, barTopY)
                            lineTo(rightX + postW + dx, barTopY + dy)
                            lineTo(leftX + dx, barTopY + dy)
                            close()
                        }
                        drawPath(pBarTop, barColor.copy(alpha = alpha * 0.8f))
                        
                        // Horizontal Bar Front
                        drawRect(barColor, Offset(leftX, barTopY), Size(rightX + postW - leftX, barH))
                        
                        // Orange Stripes on Bar
                        val stripeCount = 4
                        val stripeW = (rightX + postW - leftX) / (stripeCount * 2 - 1)
                        for (i in 0 until stripeCount) {
                            drawRect(color, Offset(leftX + (i * 2 * stripeW), barTopY), Size(stripeW, barH))
                        }
                    }
                } else {
                    // Solid obstacle (dodge) - Enhanced
                    val isVehicle = obs.type == "Taxi" || obs.type == "Bus"
                    val isAnimal = obs.type == "Camel" || obs.type == "Kangaroo"
                    
                    if (isVehicle) {
                        val bodyColor = if (obs.type == "Taxi") Color(0xFFFFC107).copy(alpha = alpha) else Color(0xFFD32F2F).copy(alpha = alpha)
                        val windowColor = Color(0xFF81D4FA).copy(alpha = alpha)
                        val wheelColor = Color(0xFF212121).copy(alpha = alpha)
                        
                        val isBus = obs.type == "Bus"
                        val bTopY = if (isBus) screenY - obsHeight * 1.4f else screenY - obsHeight * 0.8f
                        val bLeftX = screenX - obsWidth * 0.7f
                        val bRightX = screenX + obsWidth * 0.7f
                        val bHeight = if (isBus) obsHeight * 1.4f else obsHeight * 0.8f
                        
                        // Faux 3D offset towards vanishing point
                        val dx = (vanishingX - screenX) * 0.25f
                        val dy = (vanishingY - bTopY) * 0.25f
                        
                        // Shadow
                        drawOval(Color.Black.copy(alpha = 0.4f * alpha), topLeft = Offset(bLeftX - obsWidth * 0.2f, screenY - obsHeight * 0.2f), size = Size(obsWidth * 1.8f, obsHeight * 0.4f))
                        
                        // Side Face
                        val bottomDy = (vanishingY - screenY) * 0.25f
                        if (dx > 0) { // Right side visible
                            val pSide = androidx.compose.ui.graphics.Path().apply {
                                moveTo(bRightX, bTopY)
                                lineTo(bRightX + dx, bTopY + dy)
                                lineTo(bRightX + dx, screenY + bottomDy)
                                lineTo(bRightX, screenY)
                                close()
                            }
                            drawPath(pSide, bodyColor.copy(alpha = alpha * 0.7f))
                        } else { // Left side visible
                            val pSide = androidx.compose.ui.graphics.Path().apply {
                                moveTo(bLeftX, bTopY)
                                lineTo(bLeftX + dx, bTopY + dy)
                                lineTo(bLeftX + dx, screenY + bottomDy)
                                lineTo(bLeftX, screenY)
                                close()
                            }
                            drawPath(pSide, bodyColor.copy(alpha = alpha * 0.7f))
                        }
                        
                        // Top Face
                        val pTop = androidx.compose.ui.graphics.Path().apply {
                            moveTo(bLeftX, bTopY)
                            lineTo(bRightX, bTopY)
                            lineTo(bRightX + dx, bTopY + dy)
                            lineTo(bLeftX + dx, bTopY + dy)
                            close()
                        }
                        drawPath(pTop, bodyColor.copy(alpha = alpha * 0.5f))

                        // Draw vehicle body (Front Face)
                        drawRoundRect(
                            color = bodyColor,
                            topLeft = Offset(bLeftX, bTopY),
                            size = Size(obsWidth * 1.4f, bHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(obsWidth * 0.1f)
                        )
                        // Draw windshield/windows
                        drawRoundRect(
                            color = windowColor,
                            topLeft = Offset(screenX - obsWidth * 0.55f, bTopY + bHeight * 0.1f),
                            size = Size(obsWidth * 1.1f, bHeight * 0.4f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(obsWidth * 0.05f)
                        )
                        
                        // Grill & Headlights
                        val grillY = bTopY + bHeight * 0.6f
                        drawRect(Color.DarkGray.copy(alpha = alpha), Offset(screenX - obsWidth * 0.3f, grillY), Size(obsWidth * 0.6f, bHeight * 0.2f))
                        drawCircle(Color.White.copy(alpha = alpha), radius = obsWidth * 0.15f, center = Offset(screenX - obsWidth * 0.45f, grillY + bHeight * 0.1f)) // L Headlight
                        drawCircle(Color.White.copy(alpha = alpha), radius = obsWidth * 0.15f, center = Offset(screenX + obsWidth * 0.45f, grillY + bHeight * 0.1f)) // R Headlight
                        
                        if (obs.type == "Taxi") {
                            // Taxi Sign on top
                            drawRect(Color(0xFF212121).copy(alpha = alpha), Offset(screenX - obsWidth * 0.2f, bTopY - obsHeight * 0.15f), Size(obsWidth * 0.4f, obsHeight * 0.15f))
                        } else if (obs.type == "Bus") {
                            // Destination sign
                            drawRect(Color(0xFF212121).copy(alpha = alpha), Offset(screenX - obsWidth * 0.4f, bTopY + bHeight * 0.02f), Size(obsWidth * 0.8f, bHeight * 0.08f))
                        }
                        
                        // Wheels
                        drawCircle(wheelColor, radius = obsWidth * 0.25f, center = Offset(screenX - obsWidth * 0.45f, screenY - obsHeight * 0.1f))
                        drawCircle(wheelColor, radius = obsWidth * 0.25f, center = Offset(screenX + obsWidth * 0.45f, screenY - obsHeight * 0.1f))
                    } else if (isAnimal) {
                        val isCamel = obs.type == "Camel"
                        val bodyColor = if (isCamel) Color(0xFFC19A6B).copy(alpha = alpha) else Color(0xFFF57C00).copy(alpha = alpha)
                        
                        // Shadow
                        drawOval(Color.Black.copy(alpha = 0.3f * alpha), topLeft = Offset(screenX - obsWidth, screenY - obsHeight * 0.2f), size = Size(obsWidth * 2f, obsHeight * 0.4f))
                        
                        if (isCamel) {
                            // Camel Shape
                            drawOval(bodyColor, Offset(screenX - obsWidth * 0.6f, screenY - obsHeight * 0.7f), Size(obsWidth * 1.2f, obsHeight * 0.5f)) // Body
                            drawCircle(bodyColor, radius = obsWidth * 0.4f, center = Offset(screenX, screenY - obsHeight * 0.8f)) // Hump
                            drawRect(bodyColor, Offset(screenX - obsWidth * 0.4f, screenY - obsHeight * 0.3f), Size(obsWidth * 0.15f, obsHeight * 0.3f)) // Leg L
                            drawRect(bodyColor, Offset(screenX + obsWidth * 0.25f, screenY - obsHeight * 0.3f), Size(obsWidth * 0.15f, obsHeight * 0.3f)) // Leg R
                            
                            // Neck & Head
                            val pNeck = androidx.compose.ui.graphics.Path().apply {
                                moveTo(screenX - obsWidth * 0.5f, screenY - obsHeight * 0.5f)
                                quadraticBezierTo(screenX - obsWidth * 1.0f, screenY - obsHeight * 0.9f, screenX - obsWidth * 0.8f, screenY - obsHeight * 1.1f)
                                lineTo(screenX - obsWidth * 0.6f, screenY - obsHeight * 1.1f)
                                close()
                            }
                            drawPath(pNeck, bodyColor)
                            drawCircle(bodyColor, radius = obsWidth * 0.25f, center = Offset(screenX - obsWidth * 0.7f, screenY - obsHeight * 1.1f)) // Head
                        } else {
                            // Kangaroo Shape
                            drawOval(bodyColor, Offset(screenX - obsWidth * 0.5f, screenY - obsHeight * 0.6f), Size(obsWidth * 1.0f, obsHeight * 0.6f)) // Body
                            
                            // Tail
                            val pTail = androidx.compose.ui.graphics.Path().apply {
                                moveTo(screenX + obsWidth * 0.2f, screenY - obsHeight * 0.2f)
                                quadraticBezierTo(screenX + obsWidth * 1.2f, screenY - obsHeight * 0.1f, screenX + obsWidth * 1.0f, screenY)
                                lineTo(screenX + obsWidth * 0.2f, screenY)
                                close()
                            }
                            drawPath(pTail, bodyColor)
                            
                            // Head
                            drawCircle(bodyColor, radius = obsWidth * 0.3f, center = Offset(screenX - obsWidth * 0.3f, screenY - obsHeight * 0.8f))
                            
                            // Ears
                            val pEar = androidx.compose.ui.graphics.Path().apply {
                                moveTo(screenX - obsWidth * 0.3f, screenY - obsHeight * 0.8f)
                                lineTo(screenX - obsWidth * 0.4f, screenY - obsHeight * 1.1f)
                                lineTo(screenX - obsWidth * 0.1f, screenY - obsHeight * 1.1f)
                                close()
                            }
                            drawPath(pEar, bodyColor)
                            
                            // Big Feet
                            drawOval(bodyColor, Offset(screenX - obsWidth * 0.4f, screenY - obsHeight * 0.1f), Size(obsWidth * 0.6f, obsHeight * 0.1f))
                        }
                    } else if (obs.type == "Roadblock") {
                        val color = Color(0xFFFF9800).copy(alpha = alpha)
                        val leftX = screenX - obsWidth * 1.2f
                        val rightX = screenX + obsWidth * 1.2f
                        val topY = screenY - obsHeight * 0.8f
                        val dx = (vanishingX - screenX) * 0.25f
                        val dy = (vanishingY - topY) * 0.25f
                        
                        drawOval(Color.Black.copy(alpha = 0.3f * alpha), topLeft = Offset(screenX - obsWidth * 1.4f, screenY - obsHeight * 0.2f), size = Size(obsWidth * 2.8f, obsHeight * 0.4f))
                        
                        val bottomDy = (vanishingY - screenY) * 0.25f
                        if (dx > 0) {
                            val pSide = androidx.compose.ui.graphics.Path().apply {
                                moveTo(rightX, topY)
                                lineTo(rightX + dx, topY + dy)
                                lineTo(rightX + dx, screenY + bottomDy)
                                lineTo(rightX, screenY)
                                close()
                            }
                            drawPath(pSide, color.copy(alpha = alpha * 0.7f))
                        } else {
                            val pSide = androidx.compose.ui.graphics.Path().apply {
                                moveTo(leftX, topY)
                                lineTo(leftX + dx, topY + dy)
                                lineTo(leftX + dx, screenY + bottomDy)
                                lineTo(leftX, screenY)
                                close()
                            }
                            drawPath(pSide, color.copy(alpha = alpha * 0.7f))
                        }
                        
                        val pTop = androidx.compose.ui.graphics.Path().apply {
                            moveTo(leftX, topY)
                            lineTo(rightX, topY)
                            lineTo(rightX + dx, topY + dy)
                            lineTo(leftX + dx, topY + dy)
                            close()
                        }
                        drawPath(pTop, color.copy(alpha = alpha * 0.5f))
                        
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(leftX, topY),
                            size = Size(obsWidth * 2.4f, obsHeight * 0.8f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(obsWidth * 0.1f)
                        )
                        drawRect(Color.White.copy(alpha = alpha), Offset(screenX - obsWidth * 0.8f, topY), Size(obsWidth * 0.3f, obsHeight * 0.8f))
                        drawRect(Color.White.copy(alpha = alpha), Offset(screenX - obsWidth * 0.15f, topY), Size(obsWidth * 0.3f, obsHeight * 0.8f))
                        drawRect(Color.White.copy(alpha = alpha), Offset(screenX + obsWidth * 0.5f, topY), Size(obsWidth * 0.3f, obsHeight * 0.8f))
                    } else if (obs.type == "Trash Can") {
                        val color = Color(0xFF607D8B).copy(alpha = alpha)
                        drawOval(Color.Black.copy(alpha = 0.3f * alpha), topLeft = Offset(screenX - obsWidth * 0.6f, screenY - obsHeight * 0.2f), size = Size(obsWidth * 1.2f, obsHeight * 0.4f))
                        drawRoundRect(color, Offset(screenX - obsWidth * 0.5f, screenY - obsHeight * 0.9f), Size(obsWidth, obsHeight * 0.9f), androidx.compose.ui.geometry.CornerRadius(obsWidth * 0.1f))
                        drawOval(Color.DarkGray.copy(alpha = alpha), Offset(screenX - obsWidth * 0.5f, screenY - obsHeight * 0.95f), Size(obsWidth, obsHeight * 0.1f))
                        drawLine(Color.DarkGray.copy(alpha = alpha), Offset(screenX - obsWidth * 0.3f, screenY - obsHeight * 0.2f), Offset(screenX - obsWidth * 0.3f, screenY - obsHeight * 0.8f), strokeWidth = 2.dp.toPx() * scale)
                        drawLine(Color.DarkGray.copy(alpha = alpha), Offset(screenX, screenY - obsHeight * 0.2f), Offset(screenX, screenY - obsHeight * 0.8f), strokeWidth = 2.dp.toPx() * scale)
                        drawLine(Color.DarkGray.copy(alpha = alpha), Offset(screenX + obsWidth * 0.3f, screenY - obsHeight * 0.2f), Offset(screenX + obsWidth * 0.3f, screenY - obsHeight * 0.8f), strokeWidth = 2.dp.toPx() * scale)
                    } else if (obs.type == "Bench") {
                        val woodColor = Color(0xFF795548).copy(alpha = alpha)
                        val metalColor = Color(0xFF424242).copy(alpha = alpha)
                        drawOval(Color.Black.copy(alpha = 0.3f * alpha), topLeft = Offset(screenX - obsWidth * 1.1f, screenY - obsHeight * 0.1f), size = Size(obsWidth * 2.2f, obsHeight * 0.2f))
                        
                        // Legs
                        drawRect(metalColor, Offset(screenX - obsWidth * 0.8f, screenY - obsHeight * 0.4f), Size(obsWidth * 0.1f, obsHeight * 0.4f))
                        drawRect(metalColor, Offset(screenX + obsWidth * 0.7f, screenY - obsHeight * 0.4f), Size(obsWidth * 0.1f, obsHeight * 0.4f))
                        
                        // Seat
                        drawRect(woodColor, Offset(screenX - obsWidth, screenY - obsHeight * 0.4f), Size(obsWidth * 2f, obsHeight * 0.1f))
                        drawRect(woodColor, Offset(screenX - obsWidth, screenY - obsHeight * 0.3f), Size(obsWidth * 2f, obsHeight * 0.1f))
                        
                        // Backrest
                        drawRect(metalColor, Offset(screenX - obsWidth * 0.8f, screenY - obsHeight * 0.8f), Size(obsWidth * 0.1f, obsHeight * 0.4f))
                        drawRect(metalColor, Offset(screenX + obsWidth * 0.7f, screenY - obsHeight * 0.8f), Size(obsWidth * 0.1f, obsHeight * 0.4f))
                        drawRect(woodColor, Offset(screenX - obsWidth, screenY - obsHeight * 0.8f), Size(obsWidth * 2f, obsHeight * 0.1f))
                        drawRect(woodColor, Offset(screenX - obsWidth, screenY - obsHeight * 0.65f), Size(obsWidth * 2f, obsHeight * 0.1f))
                    } else {
                        // Boxy object 3D (Phone Box, Vending Machine, Kiosk)
                        val isPhoneBox = obs.type == "Phone Box"
                        val isVending = obs.type == "Vending Machine"
                        
                        val standColor = if (isPhoneBox) Color(0xFFD32F2F).copy(alpha = alpha) // Red
                            else if (isVending) Color(0xFF1976D2).copy(alpha = alpha) // Blue
                            else Color(0xFF4CAF50).copy(alpha = alpha) // Green kiosk
                        
                        val topY = screenY - obsHeight * 1.2f
                        val leftX = screenX - obsWidth / 2f
                        val rightX = screenX + obsWidth / 2f
                        
                        // Faux 3D offset towards vanishing point
                        val dx = (vanishingX - screenX) * 0.2f
                        val dy = (vanishingY - topY) * 0.2f
                        
                        // Shadow
                        drawOval(Color.Black.copy(alpha = 0.3f * alpha), topLeft = Offset(leftX - obsWidth * 0.5f, screenY - obsHeight * 0.2f), size = Size(obsWidth * 2f, obsHeight * 0.4f))
                        
                        // Side Face
                        val bottomDy = (vanishingY - screenY) * 0.2f
                        if (dx > 0) { // Right side visible
                            val pSide = androidx.compose.ui.graphics.Path().apply {
                                moveTo(rightX, topY)
                                lineTo(rightX + dx, topY + dy)
                                lineTo(rightX + dx, screenY + bottomDy)
                                lineTo(rightX, screenY)
                                close()
                            }
                            drawPath(pSide, standColor.copy(alpha = alpha * 0.7f))
                        } else { // Left side visible
                            val pSide = androidx.compose.ui.graphics.Path().apply {
                                moveTo(leftX, topY)
                                lineTo(leftX + dx, topY + dy)
                                lineTo(leftX + dx, screenY + bottomDy)
                                lineTo(leftX, screenY)
                                close()
                            }
                            drawPath(pSide, standColor.copy(alpha = alpha * 0.7f))
                        }
                        
                        // Top Face
                        val pTop = androidx.compose.ui.graphics.Path().apply {
                            moveTo(leftX, topY)
                            lineTo(rightX, topY)
                            lineTo(rightX + dx, topY + dy)
                            lineTo(leftX + dx, topY + dy)
                            close()
                        }
                        drawPath(pTop, standColor.copy(alpha = alpha * 0.5f))

                        // Main body (Front Face)
                        drawRoundRect(
                            color = standColor,
                            topLeft = Offset(leftX, topY),
                            size = Size(obsWidth, screenY - topY),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(obsWidth * 0.05f)
                        )
                        
                        // Details/Screen
                        if (isPhoneBox) {
                            // Glass panes
                            val glassColor = Color(0xFFE0F7FA).copy(alpha = alpha * 0.8f)
                            drawRect(glassColor, Offset(screenX - obsWidth * 0.3f, topY + obsHeight * 0.2f), Size(obsWidth * 0.6f, obsHeight * 0.6f))
                            // Phone Box sign
                            drawRect(Color.White.copy(alpha = alpha), Offset(screenX - obsWidth * 0.3f, topY + obsHeight * 0.05f), Size(obsWidth * 0.6f, obsHeight * 0.1f))
                        } else if (isVending) {
                            // Glass front with items
                            val glassColor = Color(0xFFE0F7FA).copy(alpha = alpha * 0.7f)
                            drawRect(glassColor, Offset(screenX - obsWidth * 0.4f, topY + obsHeight * 0.1f), Size(obsWidth * 0.6f, obsHeight * 0.8f))
                            // Vending items
                            val itemColor = Color.Red.copy(alpha = alpha)
                            drawRect(itemColor, Offset(screenX - obsWidth * 0.3f, topY + obsHeight * 0.2f), Size(obsWidth * 0.15f, obsHeight * 0.15f))
                            drawRect(Color.Green.copy(alpha = alpha), Offset(screenX, topY + obsHeight * 0.2f), Size(obsWidth * 0.15f, obsHeight * 0.15f))
                            drawRect(Color.Yellow.copy(alpha = alpha), Offset(screenX - obsWidth * 0.3f, topY + obsHeight * 0.5f), Size(obsWidth * 0.15f, obsHeight * 0.15f))
                            
                            // Coin slot panel
                            drawRect(Color.DarkGray.copy(alpha = alpha), Offset(screenX + obsWidth * 0.25f, topY + obsHeight * 0.4f), Size(obsWidth * 0.15f, obsHeight * 0.4f))
                        } else {
                            // Kiosk awning
                            val pAwning = androidx.compose.ui.graphics.Path().apply {
                                moveTo(leftX - obsWidth * 0.1f, topY + obsHeight * 0.3f)
                                lineTo(rightX + obsWidth * 0.1f, topY + obsHeight * 0.3f)
                                lineTo(rightX, topY)
                                lineTo(leftX, topY)
                                close()
                            }
                            drawPath(pAwning, Color(0xFFFFC107).copy(alpha = alpha)) // Yellow awning
                            
                            // Window
                            drawRect(Color(0xFF212121).copy(alpha = alpha), Offset(screenX - obsWidth * 0.3f, topY + obsHeight * 0.4f), Size(obsWidth * 0.6f, obsHeight * 0.4f))
                        }
                    }
                }
            }

            // Draw PowerUps
            for (pu in state.powerUps) {
                if (pu.yRatio < 0) continue
                val laneCenter = roadStartXAtBottom + pu.lane * laneWidthAtBottom + laneWidthAtBottom / 2f
                
                val perspective = (pu.yRatio * pu.yRatio * pu.yRatio)
                val scale = perspective.coerceAtMost(5f)
                val screenY = vanishingY + perspective * (height - vanishingY)
                val screenX = vanishingX + (laneCenter - vanishingX) * scale
                
                val puSize = 64.dp.toPx() * scale
                if (puSize < 1f) continue
                
                // Pulsing animation over time
                val pulse = 1f + 0.15f * kotlin.math.sin(state.distance * 15f)
                val finalSize = puSize * pulse
                
                // Fade in smoothly at horizon
                val alpha = (pu.yRatio * 5f).coerceIn(0f, 1f)
                
                // Ground Shadow
                drawOval(Color.Black.copy(alpha = 0.4f * alpha), topLeft = Offset(screenX - finalSize * 0.5f, screenY - finalSize * 0.1f), size = Size(finalSize, finalSize * 0.2f))
                
                if (pu.type == "Speed Boost") {
                    // Color is bright neon cyan/gold
                    val mainColor = Color(0xFF00E5FF).copy(alpha = alpha) // Neon Cyan
                    val ringColor = Color(0xFFFFD700).copy(alpha = alpha * 0.5f) // Golden ring
                    
                    // Draw a pulsing outer ring
                    drawCircle(
                        color = ringColor,
                        radius = finalSize * 0.7f,
                        center = Offset(screenX, screenY - finalSize * 0.5f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = (3.dp.toPx() * scale).coerceAtLeast(1f))
                    )
                    
                    // Draw a rotating 3D Cube for "3D modeling"
                    val timeAngle = (System.currentTimeMillis() % 4000) / 4000f * 2 * kotlin.math.PI.toFloat()
                    val s = finalSize * 0.45f
                    
                    val vertices = Array(8) { FloatArray(3) }
                    for (i in 0..7) {
                        val x = if (i % 2 == 1) s else -s
                        val y = if ((i / 2) % 2 == 1) s else -s
                        val z = if (i / 4 == 1) s else -s
                        
                        // Rotate Y
                        val rY = timeAngle
                        val rx1 = x * kotlin.math.cos(rY).toFloat() - z * kotlin.math.sin(rY).toFloat()
                        val rz1 = x * kotlin.math.sin(rY).toFloat() + z * kotlin.math.cos(rY).toFloat()
                        
                        // Rotate X
                        val rX = timeAngle * 0.5f
                        val ry1 = y * kotlin.math.cos(rX).toFloat() - rz1 * kotlin.math.sin(rX).toFloat()
                        val rz2 = y * kotlin.math.sin(rX).toFloat() + rz1 * kotlin.math.cos(rX).toFloat()
                        
                        vertices[i][0] = screenX + rx1
                        vertices[i][1] = screenY - finalSize * 0.5f + ry1
                        vertices[i][2] = rz2
                    }
                    
                    // Front(0,2,3,1), Back(5,7,6,4), Top(4,0,1,5), Bottom(2,6,7,3), Left(4,6,2,0), Right(1,3,7,5)
                    val faces = arrayOf(
                        intArrayOf(0, 2, 3, 1),
                        intArrayOf(5, 7, 6, 4),
                        intArrayOf(4, 0, 1, 5),
                        intArrayOf(2, 6, 7, 3),
                        intArrayOf(4, 6, 2, 0),
                        intArrayOf(1, 3, 7, 5)
                    )
                    
                    val faceColors = listOf(
                        Color(0xFF00E5FF), Color(0xFF00B0FF), Color(0xFF84FFFF),
                        Color(0xFF00838F), Color(0xFF18FFFF), Color(0xFF00E5FF)
                    )
                    
                    val faceZ = faces.mapIndexed { idx, face ->
                        val avgZ = (vertices[face[0]][2] + vertices[face[1]][2] + vertices[face[2]][2] + vertices[face[3]][2]) / 4f
                        Pair(idx, avgZ)
                    }.sortedBy { it.second } // Sort back to front
                    
                    for ((idx, _) in faceZ) {
                        val face = faces[idx]
                        
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(vertices[face[0]][0], vertices[face[0]][1])
                            lineTo(vertices[face[1]][0], vertices[face[1]][1])
                            lineTo(vertices[face[2]][0], vertices[face[2]][1])
                            lineTo(vertices[face[3]][0], vertices[face[3]][1])
                            close()
                        }
                        
                        drawPath(path, faceColors[idx].copy(alpha = alpha * 0.9f))
                        drawPath(path, Color.White.copy(alpha = alpha * 0.7f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = (2.dp.toPx() * scale).coerceAtLeast(1f)))
                    }
                    
                    // PowerUp type handled
                } else if (pu.type == "Shield") {
                    // Draw a Shield Bubble
                    val shieldColor = Color(0xFF64FFDA).copy(alpha = alpha) // Neon Teal
                    val innerColor = Color(0xFFE0F2F1).copy(alpha = alpha * 0.6f)
                    
                    drawCircle(
                        color = shieldColor.copy(alpha = alpha * 0.3f),
                        radius = finalSize * 0.7f,
                        center = Offset(screenX, screenY - finalSize * 0.5f)
                    )
                    drawCircle(
                        color = shieldColor,
                        radius = finalSize * 0.7f,
                        center = Offset(screenX, screenY - finalSize * 0.5f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = (4.dp.toPx() * scale).coerceAtLeast(1.5f))
                    )
                    // Inner reflection
                    drawCircle(
                        color = innerColor,
                        radius = finalSize * 0.4f,
                        center = Offset(screenX - finalSize * 0.15f, screenY - finalSize * 0.65f)
                    )
                }
            }

            // Draw Player
            val playerLaneCenter = roadStartXAtBottom + playerAnimatedX * laneWidthAtBottom + laneWidthAtBottom / 2f
            
            val transitionProgress = state.transitionProgress
            val playerYRatio = if (state.isTransitioning) {
                if (transitionProgress < 0.5f) {
                    val t = transitionProgress * 2f
                    0.8f - t * 0.8f // Run towards horizon
                } else {
                    val t = (transitionProgress - 0.5f) * 2f
                    1.5f - t * 0.7f // Run in from bottom
                }
            } else {
                0.8f
            }
            
            val playerScale = playerYRatio.coerceIn(0.01f, 1.5f)
            val jumpVisualOffset = if (state.isJumping) {
                kotlin.math.sin((state.jumpTime / 0.5f) * kotlin.math.PI.toFloat()) * (height * 0.2f)
            } else 0f
            val playerScreenY = vanishingY + playerYRatio * (height - vanishingY) - jumpVisualOffset
            val playerScreenX = vanishingX + (playerLaneCenter - vanishingX) * playerScale
            
            val pSize = 80.dp.toPx() * playerScale
            
            // Custom drawn animated player
            val h = pSize
            val isSprinting = state.sprintTimeLeft > 0f
            val runPhase = if (state.isGameOver || state.isJumping) 0f else {
                if (isSprinting) (state.distance * 18f) else (state.distance * 12f)
            }
            
            val bobbing = if (state.isGameOver || state.isJumping || state.isSliding) 0f else (kotlin.math.cos(runPhase * 2f) * (h * 0.03f))
            var hipsY = (playerScreenY - h * 0.42f) + bobbing
            var hipsX = playerScreenX + (if (state.isGameOver) -h * 0.08f else (kotlin.math.sin(runPhase) * (h * 0.02f)))
            
            var torsoLean = if (state.isGameOver) 0.25f else 0.12f
            var shouldersY = hipsY - h * 0.3f
            var shouldersX = hipsX + (if (state.isGameOver) -h * 0.12f else (torsoLean * h * 0.1f))
            
            if (state.isSliding) {
                hipsY = playerScreenY - h * 0.15f
                shouldersY = playerScreenY - h * 0.2f
                shouldersX = hipsX + h * 0.4f
                torsoLean = 0.6f
            }
            
            val headRadius = h * 0.11f
            val headY = shouldersY - headRadius * 1.1f
            val headX = shouldersX + (if (state.isGameOver) -h * 0.04f else (torsoLean * h * 0.04f))
            
            val lHipX = hipsX - h * 0.07f
            val rHipX = hipsX + h * 0.07f
            val lShoulderX = shouldersX - h * 0.1f
            val rShoulderX = shouldersX + h * 0.1f
            
            // Calculate leg and arm joint points
            var lKneeX: Float
            var lKneeY: Float
            var lFootX: Float
            var lFootY: Float
            
            var rKneeX: Float
            var rKneeY: Float
            var rFootX: Float
            var rFootY: Float
            
            var lElbowX: Float
            var lElbowY: Float
            var lHandX: Float
            var lHandY: Float
            
            var rElbowX: Float
            var rElbowY: Float
            var rHandX: Float
            var rHandY: Float
            
            var lLift = 0f; var lBack = 0f; var rLift = 0f; var rBack = 0f; var swingL = 0f; var swingR = 0f
            
            if (state.isGameOver) {
                // Collapsed fallen poses
                lKneeX = hipsX - h * 0.12f
                lKneeY = playerScreenY - h * 0.12f
                lFootX = hipsX - h * 0.25f
                lFootY = playerScreenY - h * 0.02f
                
                rKneeX = hipsX + h * 0.12f
                rKneeY = playerScreenY - h * 0.08f
                rFootX = hipsX + h * 0.3f
                rFootY = playerScreenY - h * 0.02f
                
                lElbowX = shouldersX - h * 0.12f
                lElbowY = shouldersY - h * 0.08f
                lHandX = shouldersX - h * 0.22f
                lHandY = shouldersY - h * 0.18f
                
                rElbowX = shouldersX + h * 0.12f
                rElbowY = shouldersY + h * 0.08f
                rHandX = shouldersX + h * 0.22f
                rHandY = shouldersY + h * 0.15f
            } else if (state.isJumping) {
                // Frame-based Jump Animation (4 interpolated keyframes: Launch, Up, Peak, Down)
                val jumpFrames = 4
                val jumpProgress = (state.jumpTime / 0.5f).coerceIn(0f, 0.999f)
                val exactFrame = jumpProgress * (jumpFrames - 1)
                val f1 = exactFrame.toInt()
                val f2 = (f1 + 1).coerceAtMost(jumpFrames - 1)
                val t = exactFrame - f1
                
                val lKneeYOffsets = listOf(0.02f, -0.05f, -0.08f, 0.02f)
                val rKneeYOffsets = listOf(-0.02f, 0.05f, 0.08f, -0.02f)
                val armSpread = listOf(0.05f, 0.15f, 0.2f, 0.1f)
                
                val lkY = androidx.compose.ui.util.lerp(lKneeYOffsets[f1], lKneeYOffsets[f2], t)
                val rkY = androidx.compose.ui.util.lerp(rKneeYOffsets[f1], rKneeYOffsets[f2], t)
                val spread = androidx.compose.ui.util.lerp(armSpread[f1], armSpread[f2], t)

                lKneeX = hipsX - h * 0.08f; lKneeY = hipsY + h * 0.1f + h * lkY
                lFootX = lKneeX - h * 0.05f; lFootY = lKneeY + h * 0.15f
                rKneeX = hipsX + h * 0.08f; rKneeY = hipsY + h * 0.1f + h * rkY
                rFootX = rKneeX + h * 0.05f; rFootY = rKneeY + h * 0.15f
                
                lElbowX = shouldersX - h * 0.1f - h * spread; lElbowY = shouldersY - h * spread
                lHandX = lElbowX - h * 0.05f; lHandY = lElbowY - h * 0.05f
                rElbowX = shouldersX + h * 0.1f + h * spread; rElbowY = shouldersY - h * spread
                rHandX = rElbowX + h * 0.05f; rHandY = rElbowY - h * 0.05f
            } else if (state.isSliding) {
                // Frame-based Slide Animation (3 interpolated keyframes: Drop, Slide, Recover)
                val slideFrames = 3
                val slideProgress = (state.slideTime / 0.5f).coerceIn(0f, 0.999f)
                val exactFrame = slideProgress * (slideFrames - 1)
                val f1 = exactFrame.toInt()
                val f2 = (f1 + 1).coerceAtMost(slideFrames - 1)
                val t = exactFrame - f1
                
                val legExtVals = listOf(0.05f, 0.15f, 0.05f)
                val armDropVals = listOf(0.0f, -0.05f, 0.0f)
                
                val ext = androidx.compose.ui.util.lerp(legExtVals[f1], legExtVals[f2], t)
                val drop = androidx.compose.ui.util.lerp(armDropVals[f1], armDropVals[f2], t)

                lKneeX = hipsX - h * 0.1f - h * ext; lKneeY = playerScreenY - h * 0.05f
                lFootX = lKneeX - h * 0.1f; lFootY = playerScreenY
                rKneeX = hipsX + h * 0.1f + h * ext; rKneeY = playerScreenY - h * 0.05f
                rFootX = rKneeX + h * 0.1f; rFootY = playerScreenY
                
                lElbowX = shouldersX - h * 0.1f; lElbowY = shouldersY + h * 0.05f + h * drop
                lHandX = lElbowX - h * 0.05f; lHandY = playerScreenY - h * 0.02f
                rElbowX = shouldersX + h * 0.1f; rElbowY = shouldersY + h * 0.05f + h * drop
                rHandX = rElbowX + h * 0.05f; rHandY = playerScreenY - h * 0.02f
            } else {
                // Frame-based Running Animation (6-frame interpolated cycle)
                val runFrames = 6
                val runSpeed = if (isSprinting) 24f else 18f
                val exactFrame = (state.distance * runSpeed) % runFrames
                val f1 = exactFrame.toInt()
                val f2 = (f1 + 1) % runFrames
                val t = exactFrame - f1
                
                // Keyframes for left leg (0 to 5)
                val lLiftVals = listOf(0.0f, 0.04f, 0.08f, 0.04f, 0.0f, -0.02f)
                val lBackVals = listOf(-0.02f, 0.0f, 0.02f, 0.05f, 0.02f, 0.0f)
                
                // Keyframes for right leg (shifted by 3 frames for opposite phase)
                val rLiftVals = listOf(0.04f, 0.0f, -0.02f, 0.0f, 0.04f, 0.08f)
                val rBackVals = listOf(0.05f, 0.02f, 0.0f, -0.02f, 0.0f, 0.02f)
                
                // Arm swing keyframes
                val lArmSwingVals = listOf(0.03f, 0.01f, -0.02f, -0.04f, -0.02f, 0.01f)
                val rArmSwingVals = listOf(-0.04f, -0.02f, 0.01f, 0.03f, 0.01f, -0.02f)

                lLift = androidx.compose.ui.util.lerp(lLiftVals[f1], lLiftVals[f2], t)
                lBack = androidx.compose.ui.util.lerp(lBackVals[f1], lBackVals[f2], t)
                rLift = androidx.compose.ui.util.lerp(rLiftVals[f1], rLiftVals[f2], t)
                rBack = androidx.compose.ui.util.lerp(rBackVals[f1], rBackVals[f2], t)
                swingL = androidx.compose.ui.util.lerp(lArmSwingVals[f1], lArmSwingVals[f2], t)
                swingR = androidx.compose.ui.util.lerp(rArmSwingVals[f1], rArmSwingVals[f2], t)

                lKneeX = lHipX
                lKneeY = hipsY + h * 0.2f - h * lLift
                lFootX = lKneeX
                lFootY = lKneeY + h * 0.22f - h * lLift * 1.5f - h * lBack
                
                rKneeX = rHipX
                rKneeY = hipsY + h * 0.2f - h * rLift
                rFootX = rKneeX
                rFootY = rKneeY + h * 0.22f - h * rLift * 1.5f - h * rBack
                
                lElbowX = lShoulderX - h * 0.05f + h * swingL
                lElbowY = shouldersY + h * 0.15f
                lHandX = lElbowX - h * 0.02f + h * swingL * 1.5f
                lHandY = lElbowY + h * 0.12f - h * kotlin.math.abs(swingL)
                
                rElbowX = rShoulderX + h * 0.05f + h * swingR
                rElbowY = shouldersY + h * 0.15f
                rHandX = rElbowX + h * 0.02f + h * swingR * 1.5f
                rHandY = rElbowY + h * 0.12f - h * kotlin.math.abs(swingR)
            }
            
            // Theme-appropriate colors
            val shirtColor = Color(0xFFFF5722) // Sporty orange
            val skinColor = Color(0xFFFFCC80) // Skin tone
            val pantsColor = Color(0xFF3F51B5) // Sporty blue
            val shoeColor = Color(0xFF00E676) // Neon green sneakers
            val hairColor = Color(0xFF4E342E) // Brown
            
            val limbStroke = h * 0.055f
            
            // Helper for 3D Shaded Limbs
            val draw3DLimb: (Offset, Offset, Float, Color) -> Unit = { start, end, width, color ->
                val dx = end.x - start.x
                val dy = end.y - start.y
                val length = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                val angle = kotlin.math.atan2(dy.toDouble(), dx.toDouble()).toFloat() * 180f / kotlin.math.PI.toFloat()
                
                withTransform({
                    translate(left = start.x, top = start.y)
                    rotate(degrees = angle)
                }) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(0f, -width / 2),
                        size = Size(length, width),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2)
                    )
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.25f),
                        topLeft = Offset(0f, 0f),
                        size = Size(length, width / 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2)
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.2f),
                        topLeft = Offset(0f, -width / 2),
                        size = Size(length, width / 4),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 4)
                    )
                }
            }
            
            // Sort limbs by depth to fix phasing
            val limbsToDraw = mutableListOf<Pair<Float, () -> Unit>>()
            
            // Left Arm
            val lArmZ = if (state.isGameOver) -0.1f else if (state.isJumping) -0.1f else if (state.isSliding) 0.1f else -swingL
            limbsToDraw.add(lArmZ to {
                draw3DLimb(Offset(lShoulderX, shouldersY), Offset(lElbowX, lElbowY), limbStroke * 1.5f, skinColor.copy(alpha = 0.8f))
                draw3DLimb(Offset(lElbowX, lElbowY), Offset(lHandX, lHandY), limbStroke * 1.5f, skinColor.copy(alpha = 0.8f))
            })
            
            // Left Leg
            val lLegZ = if (state.isGameOver) -0.1f else if (state.isJumping) -0.1f else if (state.isSliding) 0.1f else (lBack - lLift)
            limbsToDraw.add(lLegZ to {
                draw3DLimb(Offset(lHipX, hipsY), Offset(lKneeX, lKneeY), limbStroke * 1.8f, pantsColor.copy(alpha = 0.8f)) // Thigh
                draw3DLimb(Offset(lKneeX, lKneeY), Offset(lFootX, lFootY), limbStroke * 1.6f, skinColor.copy(alpha = 0.8f)) // Shin
                draw3DLimb(Offset(lFootX - limbStroke, lFootY), Offset(lFootX + limbStroke * 1.5f, lFootY + limbStroke), limbStroke * 1.8f, shoeColor.copy(alpha = 0.8f)) // Shoe
            })
            
            // Right Leg
            val rLegZ = if (state.isGameOver) 0.1f else if (state.isJumping) 0.1f else if (state.isSliding) 0.1f else (rBack - rLift)
            limbsToDraw.add(rLegZ to {
                draw3DLimb(Offset(rHipX, hipsY), Offset(rKneeX, rKneeY), limbStroke * 2.0f, pantsColor)
                draw3DLimb(Offset(rKneeX, rKneeY), Offset(rFootX, rFootY), limbStroke * 1.8f, skinColor)
                draw3DLimb(Offset(rFootX - limbStroke, rFootY), Offset(rFootX + limbStroke * 1.5f, rFootY + limbStroke), limbStroke * 2.0f, shoeColor)
            })
            
            // Right Arm
            val rArmZ = if (state.isGameOver) 0.1f else if (state.isJumping) 0.1f else if (state.isSliding) 0.1f else -swingR
            limbsToDraw.add(rArmZ to {
                draw3DLimb(Offset(rShoulderX, shouldersY), Offset(rElbowX, rElbowY), limbStroke * 1.6f, skinColor)
                draw3DLimb(Offset(rElbowX, rElbowY), Offset(rHandX, rHandY), limbStroke * 1.6f, skinColor)
            })
            
            // Torso
            limbsToDraw.add(0f to {
                val torsoWidth = (rHipX - lHipX) * 1.2f + limbStroke * 2.5f
                val torsoHeight = (hipsY - shouldersY) * 1.2f
                val torsoCenterX = (lShoulderX + rShoulderX) / 2f
                
                // Shorts
                drawRoundRect(
                    color = pantsColor,
                    topLeft = Offset(torsoCenterX - torsoWidth/2, hipsY - torsoHeight * 0.15f),
                    size = Size(torsoWidth, torsoHeight * 0.35f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(limbStroke)
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.25f),
                    topLeft = Offset(torsoCenterX, hipsY - torsoHeight * 0.15f),
                    size = Size(torsoWidth/2, torsoHeight * 0.35f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(limbStroke)
                )

                // Shirt
                val shirtW = torsoWidth * 1.1f
                val shirtTop = shouldersY - torsoHeight * 0.1f
                val shirtH = torsoHeight * 0.85f
                drawRoundRect(
                    color = shirtColor,
                    topLeft = Offset(torsoCenterX - shirtW/2, shirtTop),
                    size = Size(shirtW, shirtH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(limbStroke * 1.5f)
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.2f),
                    topLeft = Offset(torsoCenterX, shirtTop),
                    size = Size(shirtW/2, shirtH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(limbStroke * 1.5f)
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.2f),
                    topLeft = Offset(torsoCenterX - shirtW/2, shirtTop),
                    size = Size(shirtW * 0.2f, shirtH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(limbStroke * 1.5f)
                )
            })
            
            // Head & Hair/Cap
            limbsToDraw.add(0.1f to {
                drawCircle(color = skinColor, radius = headRadius * 1.1f, center = Offset(headX, headY))
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                        center = Offset(headX, headY),
                        radius = headRadius * 1.1f
                    ),
                    radius = headRadius * 1.1f,
                    center = Offset(headX, headY)
                )
                // Head highlight
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = headRadius * 0.3f,
                    center = Offset(headX - headRadius * 0.3f, headY - headRadius * 0.3f)
                )
                
                // Draw a cool 3D cap
                val capPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headX - headRadius * 1.2f, headY - headRadius * 0.2f)
                    quadraticBezierTo(headX, headY - headRadius * 1.6f, headX + headRadius * 1.2f, headY - headRadius * 0.2f)
                    lineTo(headX + headRadius * 1.8f, headY) // visor/brim
                    lineTo(headX + headRadius * 1.2f, headY + headRadius * 0.1f)
                    close()
                }
                drawPath(capPath, hairColor)
                // Cap shading
                val capShade = androidx.compose.ui.graphics.Path().apply {
                    moveTo(headX, headY - headRadius * 1.4f)
                    quadraticBezierTo(headX + headRadius * 0.6f, headY - headRadius * 1.5f, headX + headRadius * 1.2f, headY - headRadius * 0.2f)
                    lineTo(headX, headY - headRadius * 0.2f)
                    close()
                }
                drawPath(capShade, Color.Black.copy(alpha = 0.3f))
            })
            
            limbsToDraw.sortBy { it.first }
            for (limb in limbsToDraw) {
                limb.second()
            }

            // Invincibility shield and speed trail aura during City Sprint
            if (isSprinting) {
                val auraColor = Color(0xFF00E5FF).copy(alpha = 0.4f + 0.2f * kotlin.math.sin(state.distance * 25f))
                drawCircle(
                    color = auraColor,
                    radius = h * 0.75f,
                    center = Offset(playerScreenX, playerScreenY - h * 0.45f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                )
                
                drawCircle(
                    color = Color.White.copy(alpha = 0.6f),
                    radius = h * 0.6f,
                    center = Offset(playerScreenX, playerScreenY - h * 0.45f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
                
                // Draw flying neon speed spark trails behind runner
                val random = kotlin.random.Random(state.distance.toInt())
                for (i in 0 until 5) {
                    val sparkX = playerScreenX - h * 0.3f - random.nextFloat() * h * 0.5f
                    val sparkY = playerScreenY - h * 0.1f - random.nextFloat() * h * 0.7f
                    val sparkLen = h * 0.2f + random.nextFloat() * h * 0.2f
                    drawLine(
                        color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                        start = Offset(sparkX, sparkY),
                        end = Offset(sparkX - sparkLen, sparkY),
                        strokeWidth = 2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }

            // Shield Bubble Effect
            if (state.hasShield) {
                val shieldRadius = h * 0.85f
                val shieldCenter = Offset(playerScreenX, playerScreenY - h * 0.45f)
                val pulse = 0.8f + 0.2f * kotlin.math.sin(state.distance * 10f)
                
                drawCircle(
                    color = Color(0xFF64FFDA).copy(alpha = 0.25f * pulse),
                    radius = shieldRadius,
                    center = shieldCenter
                )
                drawCircle(
                    color = Color(0xFF1DE9B6).copy(alpha = 0.8f),
                    radius = shieldRadius,
                    center = shieldCenter,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                )
                // Highlight reflection on shield
                drawArc(
                    color = Color.White.copy(alpha = 0.5f),
                    startAngle = 200f,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = Offset(shieldCenter.x - shieldRadius, shieldCenter.y - shieldRadius),
                    size = Size(shieldRadius * 2, shieldRadius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }
        
        // HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 48.dp, end = 24.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LOC: ${state.currentCity.cityName.uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "PTS: ${String.format("%06d", state.score + (state.distance * 10).toInt())}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary, // Cyan
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Black
                )
            }
            IconButton(
                onClick = { viewModel.pauseGame() },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = "||",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // City Sprint Countdown Overlay
        if (state.sprintTimeLeft > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "⚡ CITY SPRINT: ${((state.sprintTimeLeft * 10).toInt() / 10f)}s",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
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
