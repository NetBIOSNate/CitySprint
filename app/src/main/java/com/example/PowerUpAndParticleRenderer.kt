package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class ParticleBurstType {
    SPEED_COLLECT,
    SHIELD_COLLECT,
    SPEED_SMASH,
    SHIELD_DEFLECT
}

data class PowerUpParticleBurst(
    val id: Int,
    val type: ParticleBurstType,
    val lane: Float,
    val ageSeconds: Float = 0f,
    val durationSeconds: Float = 0.75f
)

object PowerUpAndParticleRenderer {

    private val sharedPath = Path()
    private val boltPath = Path()
    private val shieldCrestPath = Path()

    /**
     * Draws a stylized 3D Lightning Bolt + Winged Turbo Icon centered at (cx, cy) with radius [r].
     */
    fun DrawScope.drawSpeedBoostIcon(
        cx: Float,
        cy: Float,
        r: Float,
        alpha: Float = 1f,
        timePhase: Float = 0f
    ) {
        // Outer electric plasma halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFD740).copy(alpha = 0.55f * alpha),
                    Color(0xFF00E5FF).copy(alpha = 0.30f * alpha),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = r * 1.35f
            ),
            radius = r * 1.35f,
            center = Offset(cx, cy)
        )

        // Aerodynamic speed chevrons/wings flanking the bolt
        val wingPulse = 1f + 0.08f * sin(timePhase * 8f)
        for (side in -1..1 step 2) {
            val s = side.toFloat()
            sharedPath.reset()
            sharedPath.moveTo(cx + s * r * 0.28f, cy - r * 0.15f)
            sharedPath.lineTo(cx + s * r * 0.92f * wingPulse, cy - r * 0.52f)
            sharedPath.lineTo(cx + s * r * 0.72f * wingPulse, cy + r * 0.05f)
            sharedPath.lineTo(cx + s * r * 0.85f * wingPulse, cy + r * 0.32f)
            sharedPath.lineTo(cx + s * r * 0.22f, cy + r * 0.25f)
            sharedPath.close()
            drawPath(sharedPath, Color(0xFF00E5FF).copy(alpha = 0.85f * alpha))
        }

        // 3D Dark Shadow / Extrusion of Lightning Bolt
        val extrudeX = r * 0.07f
        val extrudeY = r * 0.09f
        buildLightningBoltPath(boltPath, cx + extrudeX, cy + extrudeY, r)
        drawPath(boltPath, Color(0xFFE65100).copy(alpha = 0.9f * alpha))

        // Main Golden-Yellow Lightning Bolt
        buildLightningBoltPath(boltPath, cx, cy, r)
        drawPath(
            path = boltPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFFFFF).copy(alpha = alpha),
                    Color(0xFFFFEA00).copy(alpha = alpha),
                    Color(0xFFFF9100).copy(alpha = alpha)
                ),
                start = Offset(cx - r * 0.5f, cy - r),
                end = Offset(cx + r * 0.5f, cy + r)
            )
        )

        // Crisp White-Hot Inner Core Outline
        drawPath(
            path = boltPath,
            color = Color.White.copy(alpha = 0.92f * alpha),
            style = Stroke(
                width = (r * 0.07f).coerceAtLeast(1.2f),
                join = StrokeJoin.Round
            )
        )
    }

    private fun buildLightningBoltPath(path: Path, cx: Float, cy: Float, r: Float) {
        path.reset()
        path.moveTo(cx + r * 0.18f, cy - r * 0.92f)
        path.lineTo(cx - r * 0.52f, cy + r * 0.06f)
        path.lineTo(cx - r * 0.04f, cy + r * 0.06f)
        path.lineTo(cx - r * 0.26f, cy + r * 0.94f)
        path.lineTo(cx + r * 0.54f, cy - r * 0.12f)
        path.lineTo(cx + r * 0.06f, cy - r * 0.12f)
        path.close()
    }

    /**
     * Draws a stylized 3D Faceted Aegis Shield Icon centered at (cx, cy) with radius [r].
     */
    fun DrawScope.drawShieldIcon(
        cx: Float,
        cy: Float,
        r: Float,
        alpha: Float = 1f,
        timePhase: Float = 0f
    ) {
        // Outer emerald-cyan barrier glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF64FFDA).copy(alpha = 0.5f * alpha),
                    Color(0xFF00BFA5).copy(alpha = 0.25f * alpha),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = r * 1.35f
            ),
            radius = r * 1.35f,
            center = Offset(cx, cy)
        )

        // Outer metallic-teal Aegis Shield silhouette
        buildShieldCrestPath(shieldCrestPath, cx, cy, r * 0.92f)
        drawPath(
            path = shieldCrestPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF84FFFF).copy(alpha = alpha),
                    Color(0xFF1DE9B6).copy(alpha = alpha),
                    Color(0xFF00695C).copy(alpha = alpha)
                ),
                start = Offset(cx - r, cy - r),
                end = Offset(cx + r, cy + r)
            )
        )

        // Inner dark cyber-armor core plate
        buildShieldCrestPath(sharedPath, cx, cy, r * 0.74f)
        drawPath(
            path = sharedPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF004D40).copy(alpha = 0.92f * alpha),
                    Color(0xFF00251A).copy(alpha = 0.95f * alpha)
                ),
                startY = cy - r * 0.7f,
                endY = cy + r * 0.7f
            )
        )

        // Right-half 3D facet shading
        sharedPath.reset()
        sharedPath.moveTo(cx, cy - r * 0.88f)
        sharedPath.lineTo(cx + r * 0.76f, cy - r * 0.58f)
        sharedPath.quadraticTo(
            cx + r * 0.72f,
            cy + r * 0.28f,
            cx,
            cy + r * 0.92f
        )
        sharedPath.close()
        drawPath(sharedPath, Color.Black.copy(alpha = 0.22f * alpha))

        // Glowing Energy Chevron / Cross Emblem in the center of the shield
        val emblemPulse = 0.75f + 0.25f * sin(timePhase * 6f)
        val emblemColor = Color(0xFFA7FFEB).copy(alpha = alpha * emblemPulse)
        val strokeW = (r * 0.14f).coerceAtLeast(1.5f)
        drawLine(
            color = emblemColor,
            start = Offset(cx, cy - r * 0.48f),
            end = Offset(cx, cy + r * 0.52f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = emblemColor,
            start = Offset(cx - r * 0.36f, cy - r * 0.04f),
            end = Offset(cx, cy - r * 0.28f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = emblemColor,
            start = Offset(cx + r * 0.36f, cy - r * 0.04f),
            end = Offset(cx, cy - r * 0.28f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )

        // Bright Rim Highlight
        drawPath(
            path = shieldCrestPath,
            color = Color.White.copy(alpha = 0.88f * alpha),
            style = Stroke(width = (r * 0.08f).coerceAtLeast(1.2f), join = StrokeJoin.Round)
        )
    }

    private fun buildShieldCrestPath(path: Path, cx: Float, cy: Float, r: Float) {
        path.reset()
        path.moveTo(cx, cy - r * 0.96f)
        path.lineTo(cx + r * 0.82f, cy - r * 0.62f)
        path.quadraticTo(
            cx + r * 0.78f,
            cy + r * 0.32f,
            cx,
            cy + r * 0.98f
        )
        path.quadraticTo(
            cx - r * 0.78f,
            cy + r * 0.32f,
            cx - r * 0.82f,
            cy - r * 0.62f
        )
        path.close()
    }

    /**
     * Draws all 3D world-space Power-Ups on the road with recognizable icons and local particle emitters.
     */
    fun DrawScope.drawPowerUps(
        powerUps: List<PowerUp>,
        distance: Float,
        height: Float,
        vanishingX: Float,
        vanishingY: Float,
        roadStartXAtBottom: Float,
        laneWidthAtBottom: Float
    ) {
        val timePhase = distance * 4.5f

        for (pu in powerUps) {
            if (pu.yRatio < 0f) continue
            val laneCenter = roadStartXAtBottom + pu.lane * laneWidthAtBottom + laneWidthAtBottom * 0.5f

            val perspective = pu.yRatio * pu.yRatio * pu.yRatio
            val scale = perspective.coerceAtMost(5f)
            val screenY = vanishingY + perspective * (height - vanishingY)
            val screenX = vanishingX + (laneCenter - vanishingX) * scale

            val baseSize = 66.dp.toPx() * scale
            if (baseSize < 1.5f) continue

            val bobOffset = sin(timePhase * 3.2f + pu.id) * (baseSize * 0.12f)
            val pulse = 1f + 0.10f * sin(timePhase * 4f + pu.id)
            val finalSize = baseSize * pulse
            val alpha = (pu.yRatio * 4.5f).coerceIn(0f, 1f)
            val centerY = screenY - finalSize * 0.62f + bobOffset

            val isSpeedBoost = pu.type == "Speed Boost"
            val primaryHue = if (isSpeedBoost) Color(0xFFFFD740) else Color(0xFF64FFDA)
            val secondaryHue = if (isSpeedBoost) Color(0xFF00E5FF) else Color(0xFF00BFA5)

            // 1. Ground Holographic Emitter Pad & Expanding Ripple Rings
            drawOval(
                color = Color.Black.copy(alpha = 0.42f * alpha),
                topLeft = Offset(screenX - finalSize * 0.55f, screenY - finalSize * 0.12f),
                size = Size(finalSize * 1.1f, finalSize * 0.24f)
            )
            val groundRipple = (timePhase * 1.8f + pu.id * 0.3f) % 1f
            val rippleW = finalSize * (0.5f + groundRipple * 0.85f)
            val rippleH = rippleW * 0.24f
            drawOval(
                color = primaryHue.copy(alpha = (1f - groundRipple) * 0.7f * alpha),
                topLeft = Offset(screenX - rippleW * 0.5f, screenY - rippleH * 0.5f),
                size = Size(rippleW, rippleH),
                style = Stroke(width = (2.dp.toPx() * scale).coerceAtLeast(1f))
            )

            // Vertical holographic light beam from ground to icon
            sharedPath.reset()
            sharedPath.moveTo(screenX - finalSize * 0.28f, screenY)
            sharedPath.lineTo(screenX - finalSize * 0.45f, centerY)
            sharedPath.lineTo(screenX + finalSize * 0.45f, centerY)
            sharedPath.lineTo(screenX + finalSize * 0.28f, screenY)
            sharedPath.close()
            drawPath(
                path = sharedPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryHue.copy(alpha = 0.0f),
                        secondaryHue.copy(alpha = 0.22f * alpha),
                        primaryHue.copy(alpha = 0.35f * alpha)
                    ),
                    startY = centerY - finalSize * 0.4f,
                    endY = screenY
                )
            )

            // 2. Orbiting & Rising Particle System around the Collectible
            val particleCount = 10
            for (pIdx in 0 until particleCount) {
                val seed = (pu.id * 17 + pIdx * 31)
                val pPhase = (timePhase * 2.1f + pIdx * (1f / particleCount)) % 1f
                val angle = timePhase * 3.5f + pIdx * (2f * PI.toFloat() / particleCount)

                if (isSpeedBoost) {
                    // Rising electric sparks & speed chevrons
                    val orbitR = finalSize * (0.48f + 0.25f * sin(seed.toFloat()))
                    val px = screenX + cos(angle) * orbitR
                    val py = screenY - pPhase * (finalSize * 1.35f)
                    val sparkAlpha = sin(pPhase * PI.toFloat()).coerceIn(0f, 1f) * alpha
                    val sparkLen = finalSize * 0.18f

                    drawLine(
                        color = if (pIdx % 2 == 0) Color(0xFFFFEA00).copy(alpha = sparkAlpha)
                        else Color(0xFF00E5FF).copy(alpha = sparkAlpha),
                        start = Offset(px, py),
                        end = Offset(px, py - sparkLen),
                        strokeWidth = (2.2.dp.toPx() * scale).coerceAtLeast(1f),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = sparkAlpha),
                        radius = (2.2.dp.toPx() * scale).coerceAtLeast(1f),
                        center = Offset(px, py - sparkLen)
                    )
                } else {
                    // Orbiting hexagonal energy motes & barrier sparkles
                    val orbitX = cos(angle) * finalSize * 0.68f
                    val orbitY = sin(angle * 1.5f) * finalSize * 0.38f
                    val px = screenX + orbitX
                    val py = centerY + orbitY
                    val moteAlpha = (0.45f + 0.55f * sin(angle)).coerceIn(0.15f, 1f) * alpha
                    val moteR = (3.5.dp.toPx() * scale).coerceAtLeast(1.2f)

                    drawCircle(
                        color = Color(0xFF64FFDA).copy(alpha = moteAlpha * 0.45f),
                        radius = moteR * 2.1f,
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = moteAlpha),
                        radius = moteR,
                        center = Offset(px, py)
                    )
                }
            }

            // 3. Rotating Gyroscopic Energy Ring
            val ringTilt = sin(timePhase * 2.8f + pu.id) * 0.35f
            drawOval(
                color = secondaryHue.copy(alpha = 0.65f * alpha),
                topLeft = Offset(screenX - finalSize * 0.66f, centerY - finalSize * (0.22f + abs(ringTilt) * 0.35f)),
                size = Size(finalSize * 1.32f, finalSize * (0.44f + abs(ringTilt) * 0.7f)),
                style = Stroke(width = (2.2.dp.toPx() * scale).coerceAtLeast(1f))
            )

            // 4. Central Visual Icon (Lightning Bolt Emblem vs Aegis Shield Emblem)
            if (isSpeedBoost) {
                drawSpeedBoostIcon(
                    cx = screenX,
                    cy = centerY,
                    r = finalSize * 0.48f,
                    alpha = alpha,
                    timePhase = timePhase
                )
            } else {
                drawShieldIcon(
                    cx = screenX,
                    cy = centerY,
                    r = finalSize * 0.48f,
                    alpha = alpha,
                    timePhase = timePhase
                )
            }
        }
    }

    /**
     * Draws active power-up particle streams on the runner as well as one-shot collection/deflection bursts.
     */
    fun DrawScope.drawActiveEffectsAndBursts(
        state: GameState,
        playerAnimatedX: Float,
        width: Float,
        height: Float,
        vanishingX: Float,
        vanishingY: Float,
        roadStartXAtBottom: Float,
        laneWidthAtBottom: Float
    ) {
        val playerLaneCenter = roadStartXAtBottom + playerAnimatedX * laneWidthAtBottom + laneWidthAtBottom * 0.5f
        val playerYRatio = 0.8f
        val playerScale = playerYRatio
        val jumpVisualOffset = if (state.isJumping) {
            sin((state.jumpTime / 0.5f) * PI.toFloat()) * (height * 0.19f)
        } else 0f

        val groundY = vanishingY + playerYRatio * (height - vanishingY)
        val playerScreenY = groundY - jumpVisualOffset
        val playerScreenX = vanishingX + (playerLaneCenter - vanishingX) * playerScale
        val h = 92.dp.toPx() * playerScale
        val runnerCenterY = playerScreenY - h * 0.46f
        val timePhase = state.distance * 5f

        // 1. Continuous 'Speed Boost' Warp Lines, Lightning Trail & Foot Sparks
        if (state.sprintTimeLeft > 0f) {
            val intensity = (state.sprintTimeLeft / 1.0f).coerceIn(0.25f, 1f)

            // Radial warp-speed streaks along the sides of the road
            for (i in 0 until 14) {
                val streamProgress = ((state.distance * 3.5f + i * 0.19f) % 1f)
                val side = if (i % 2 == 0) -1f else 1f
                val angleSpread = (0.18f + (i % 7) * 0.12f)
                val targetX = width * 0.5f + side * width * (0.38f + (i % 3) * 0.08f)
                val targetY = vanishingY + angleSpread * (height - vanishingY)

                val pStart = (streamProgress * streamProgress).coerceIn(0f, 1f)
                val pEnd = ((streamProgress + 0.22f).coerceAtMost(1f)).let { it * it }

                val sx = vanishingX + (targetX - vanishingX) * pStart
                val sy = vanishingY + (targetY - vanishingY) * pStart
                val ex = vanishingX + (targetX - vanishingX) * pEnd
                val ey = vanishingY + (targetY - vanishingY) * pEnd

                val streakColor = if (i % 3 == 0) Color(0xFFFFD740) else Color(0xFF00E5FF)
                drawLine(
                    color = streakColor.copy(alpha = 0.55f * intensity * streamProgress),
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = (2.5.dp.toPx() * streamProgress).coerceAtLeast(1f),
                    cap = StrokeCap.Round
                )
            }

            // Trailing golden-cyan plasma sparks & lightning arcs behind the runner
            for (p in 0 until 16) {
                val life = ((state.distance * 9f + p * 0.13f) % 1f)
                val spreadX = sin(p * 2.7f + state.distance * 12f) * (h * 0.42f)
                val px = playerScreenX + spreadX * (0.4f + life * 0.8f)
                val py = playerScreenY - h * 0.1f - (1f - life) * (h * 0.78f) + life * (h * 0.32f)
                val pAlpha = (1f - life) * 0.85f * intensity
                val pRadius = (3.8.dp.toPx() * (1f - life * 0.5f)).coerceAtLeast(1f)

                val sparkColor = if (p % 2 == 0) Color(0xFFFFEA00) else Color(0xFF00E5FF)
                drawLine(
                    color = sparkColor.copy(alpha = pAlpha),
                    start = Offset(px, py),
                    end = Offset(px - spreadX * 0.15f, py + h * 0.18f),
                    strokeWidth = pRadius * 0.85f,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = Color.White.copy(alpha = pAlpha),
                    radius = pRadius * 0.55f,
                    center = Offset(px, py)
                )
            }

            // Floating mini Speed Boost lightning badge above runner's shoulder
            drawSpeedBoostIcon(
                cx = playerScreenX + h * 0.48f,
                cy = runnerCenterY - h * 0.35f + sin(timePhase * 4f) * (h * 0.05f),
                r = h * 0.15f,
                alpha = 0.9f * intensity,
                timePhase = timePhase
            )
        }

        // 2. Continuous 'Shield' Hexagonal Forcefield & Orbiting Barrier Motes
        if (state.hasShield) {
            val shieldRadius = h * 0.80f
            val shieldCenter = Offset(playerScreenX, runnerCenterY)

            // Orbiting barrier plasma satellites with glowing trails
            val satelliteCount = 8
            for (sIdx in 0 until satelliteCount) {
                val angle = timePhase * 2.4f + sIdx * (2f * PI.toFloat() / satelliteCount)
                val rx = cos(angle) * shieldRadius * 0.96f
                val ry = sin(angle * 2f) * shieldRadius * 0.52f
                val satX = shieldCenter.x + rx
                val satY = shieldCenter.y + ry
                val depthAlpha = (0.55f + 0.45f * sin(angle)).coerceIn(0.25f, 1f)

                // Comet tail
                val prevAngle = angle - 0.32f
                val tailX = shieldCenter.x + cos(prevAngle) * shieldRadius * 0.96f
                val tailY = shieldCenter.y + sin(prevAngle * 2f) * shieldRadius * 0.52f
                drawLine(
                    color = Color(0xFF64FFDA).copy(alpha = depthAlpha * 0.65f),
                    start = Offset(satX, satY),
                    end = Offset(tailX, tailY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = Color(0xFF1DE9B6).copy(alpha = depthAlpha * 0.45f),
                    radius = 7.dp.toPx(),
                    center = Offset(satX, satY)
                )
                drawCircle(
                    color = Color.White.copy(alpha = depthAlpha),
                    radius = 3.2.dp.toPx(),
                    center = Offset(satX, satY)
                )
            }

            // Floating mini Aegis Shield icon badge on left shoulder
            drawShieldIcon(
                cx = playerScreenX - h * 0.48f,
                cy = runnerCenterY - h * 0.35f + cos(timePhase * 4f) * (h * 0.05f),
                r = h * 0.15f,
                alpha = 0.92f,
                timePhase = timePhase
            )
        }

        // 3. One-Shot Collection & Smash Particle Bursts
        for (burst in state.particleBursts) {
            val progress = (burst.ageSeconds / burst.durationSeconds).coerceIn(0f, 1f)
            val invProgress = 1f - progress
            val burstLaneCenter = roadStartXAtBottom + burst.lane * laneWidthAtBottom + laneWidthAtBottom * 0.5f
            val bx = vanishingX + (burstLaneCenter - vanishingX) * playerScale
            val by = runnerCenterY

            val isSpeedType = burst.type == ParticleBurstType.SPEED_COLLECT ||
                burst.type == ParticleBurstType.SPEED_SMASH
            val primaryColor = if (isSpeedType) Color(0xFFFFD740) else Color(0xFF64FFDA)
            val secondaryColor = if (isSpeedType) Color(0xFF00E5FF) else Color(0xFF1DE9B6)

            // Expanding Shockwave Ring
            val shockRadius = h * (0.25f + progress * 1.65f)
            drawCircle(
                color = primaryColor.copy(alpha = invProgress * 0.85f),
                radius = shockRadius,
                center = Offset(bx, by),
                style = Stroke(width = (6.dp.toPx() * invProgress).coerceAtLeast(1f))
            )
            drawCircle(
                color = secondaryColor.copy(alpha = invProgress * 0.55f),
                radius = shockRadius * 0.72f,
                center = Offset(bx, by),
                style = Stroke(width = (3.dp.toPx() * invProgress).coerceAtLeast(1f))
            )

            // 24 Radial Explosive Particles (Sparks, Bolts, or Hex Shards)
            val numParticles = 24
            for (i in 0 until numParticles) {
                val baseAngle = (i.toFloat() / numParticles) * 2f * PI.toFloat() + (burst.id * 0.4f)
                val speedTier = 0.55f + (i % 3) * 0.32f
                val dist = h * (0.15f + progress * 1.55f * speedTier)
                val gravityDrop = progress * progress * (h * 0.35f)

                val px = bx + cos(baseAngle) * dist
                val py = by + sin(baseAngle) * dist + gravityDrop
                val pColor = if (i % 2 == 0) primaryColor else secondaryColor
                val pSize = (5.5.dp.toPx() * invProgress).coerceAtLeast(1.2f)

                // Velocity streak line
                val tailDist = (dist - h * 0.22f * invProgress).coerceAtLeast(0f)
                val tx = bx + cos(baseAngle) * tailDist
                val ty = by + sin(baseAngle) * tailDist + gravityDrop * 0.8f
                drawLine(
                    color = pColor.copy(alpha = invProgress * 0.9f),
                    start = Offset(tx, ty),
                    end = Offset(px, py),
                    strokeWidth = pSize * 0.65f,
                    cap = StrokeCap.Round
                )

                // Particle head (Diamond/Shard)
                sharedPath.reset()
                sharedPath.moveTo(px, py - pSize)
                sharedPath.lineTo(px + pSize, py)
                sharedPath.lineTo(px, py + pSize)
                sharedPath.lineTo(px - pSize, py)
                sharedPath.close()
                drawPath(sharedPath, Color.White.copy(alpha = invProgress))
                drawPath(sharedPath, pColor.copy(alpha = invProgress * 0.85f))
            }

            // Rising central icon flash on collection
            if (progress < 0.65f) {
                val iconProgress = progress / 0.65f
                val iconAlpha = (1f - iconProgress)
                val iconY = by - h * (0.4f + iconProgress * 0.55f)
                val iconRadius = h * (0.24f + iconProgress * 0.14f)
                if (isSpeedType) {
                    drawSpeedBoostIcon(bx, iconY, iconRadius, alpha = iconAlpha, timePhase = progress * 10f)
                } else {
                    drawShieldIcon(bx, iconY, iconRadius, alpha = iconAlpha, timePhase = progress * 10f)
                }
            }
        }
    }
}

/**
 * HUD status badges with custom-drawn visual icons for active Speed Boost and Shield power-ups.
 */
@Composable
fun PowerUpHudBadges(
    sprintTimeLeft: Float,
    hasShield: Boolean,
    distance: Float,
    modifier: Modifier = Modifier
) {
    if (sprintTimeLeft <= 0f && !hasShield) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (sprintTimeLeft > 0f) {
            val progress = (sprintTimeLeft / 6.0f).coerceIn(0f, 1f)
            Row(
                modifier = Modifier
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xDD002B36),
                                Color(0xDD004D61)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .border(
                        width = 1.5.dp,
                        color = Color(0xFFFFD740).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("speed_boost_hud_badge"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(32.dp)) {
                        val strokeW = 2.5.dp.toPx()
                        // Circular countdown ring
                        drawArc(
                            color = Color.White.copy(alpha = 0.2f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokeW)
                        )
                        drawArc(
                            color = Color(0xFFFFD740),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                        with(PowerUpAndParticleRenderer) {
                            drawSpeedBoostIcon(
                                cx = size.width * 0.5f,
                                cy = size.height * 0.5f,
                                r = size.minDimension * 0.34f,
                                alpha = 1f,
                                timePhase = distance * 3f
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "SPEED BOOST",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFD740),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${((sprintTimeLeft * 10).toInt() / 10f)}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (hasShield) {
            Row(
                modifier = Modifier
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xDD00251A),
                                Color(0xDD004D40)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .border(
                        width = 1.5.dp,
                        color = Color(0xFF64FFDA).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("shield_hud_badge"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(32.dp)) {
                        val pulse = 0.85f + 0.15f * sin(distance * 8f)
                        drawCircle(
                            color = Color(0xFF64FFDA).copy(alpha = 0.35f * pulse),
                            radius = size.minDimension * 0.48f,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        with(PowerUpAndParticleRenderer) {
                            drawShieldIcon(
                                cx = size.width * 0.5f,
                                cy = size.height * 0.5f,
                                r = size.minDimension * 0.35f,
                                alpha = 1f,
                                timePhase = distance * 3f
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "AEGIS SHIELD",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64FFDA),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "ACTIVE",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
