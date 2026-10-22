package com.example

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object RunnerRenderer {

    private val sharedPath = Path()

    private fun DrawScope.drawTaperedLimb(
        start: Offset,
        end: Offset,
        startRadius: Float,
        endRadius: Float,
        baseColor: Color,
        shadeColor: Color,
        highlightColor: Color
    ) {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len < 0.5f) return

        val nx = -dy / len
        val ny = dx / len

        // Main anatomical tapered segment
        sharedPath.reset()
        sharedPath.moveTo(start.x + nx * startRadius, start.y + ny * startRadius)
        sharedPath.lineTo(end.x + nx * endRadius, end.y + ny * endRadius)
        sharedPath.lineTo(end.x - nx * endRadius, end.y - ny * endRadius)
        sharedPath.lineTo(start.x - nx * startRadius, start.y - ny * startRadius)
        sharedPath.close()
        drawPath(sharedPath, baseColor)

        // Right-side 3D muscle contour shadow
        sharedPath.reset()
        sharedPath.moveTo(start.x, start.y)
        sharedPath.lineTo(end.x, end.y)
        sharedPath.lineTo(end.x - nx * endRadius, end.y - ny * endRadius)
        sharedPath.lineTo(start.x - nx * startRadius, start.y - ny * startRadius)
        sharedPath.close()
        drawPath(sharedPath, shadeColor)

        // Left-side rim highlight
        drawLine(
            color = highlightColor,
            start = Offset(start.x + nx * startRadius * 0.65f, start.y + ny * startRadius * 0.65f),
            end = Offset(end.x + nx * endRadius * 0.65f, end.y + ny * endRadius * 0.65f),
            strokeWidth = (startRadius * 0.35f).coerceAtLeast(1f),
            cap = StrokeCap.Round
        )

        // Rounded joints at both ends
        drawCircle(baseColor, radius = startRadius, center = start)
        drawCircle(baseColor, radius = endRadius, center = end)
    }

    private fun DrawScope.drawRunningShoe(
        ankle: Offset,
        toe: Offset,
        scale: Float,
        liftRatio: Float,
        primaryColor: Color,
        soleColor: Color,
        accentColor: Color
    ) {
        val shoeW = 11.dp.toPx() * scale
        val shoeH = 7.dp.toPx() * scale

        // Upper shoe body
        sharedPath.reset()
        sharedPath.moveTo(ankle.x - shoeW * 0.45f, ankle.y - shoeH * 0.3f)
        sharedPath.lineTo(ankle.x + shoeW * 0.45f, ankle.y - shoeH * 0.3f)
        sharedPath.lineTo(toe.x + shoeW * 0.55f, toe.y + shoeH * 0.35f)
        sharedPath.lineTo(toe.x - shoeW * 0.55f, toe.y + shoeH * 0.35f)
        sharedPath.close()
        drawPath(sharedPath, primaryColor)

        // Cushioned foam midsole
        drawRoundRect(
            color = soleColor,
            topLeft = Offset(toe.x - shoeW * 0.58f, toe.y + shoeH * 0.15f),
            size = Size(shoeW * 1.16f, shoeH * 0.38f),
            cornerRadius = CornerRadius(shoeH * 0.2f)
        )

        // Accent stripe
        drawLine(
            color = accentColor,
            start = Offset(ankle.x - shoeW * 0.25f, ankle.y),
            end = Offset(toe.x + shoeW * 0.25f, toe.y + shoeH * 0.1f),
            strokeWidth = (2.dp.toPx() * scale).coerceAtLeast(1f),
            cap = StrokeCap.Round
        )

        // If foot is kicked up in backswing, show the dark treaded outsole facing the camera
        if (liftRatio > 0.025f) {
            val treadH = shoeH * (0.4f + liftRatio * 4f).coerceAtMost(1.1f)
            drawRoundRect(
                color = Color(0xFF1C262B),
                topLeft = Offset(toe.x - shoeW * 0.48f, toe.y - treadH * 0.3f),
                size = Size(shoeW * 0.96f, treadH),
                cornerRadius = CornerRadius(shoeW * 0.25f)
            )
        }
    }

    /**
     * Draws a live animated 3D preview of a specific [RunnerCosmetic] for the Cosmetic Shop stage.
     */
    fun DrawScope.drawRunnerPreview(
        cosmetic: RunnerCosmetic,
        previewDistance: Float,
        centerX: Float,
        groundY: Float,
        scale: Float = 1.1f
    ) {
        val previewState = GameState(
            isPlaying = true,
            distance = previewDistance
        )
        drawRunnerCharacterAt(
            state = previewState,
            cosmetic = cosmetic,
            playerScreenX = centerX,
            playerScreenY = groundY,
            groundY = groundY,
            playerScale = scale,
            jumpVisualOffset = 0f,
            screenHeight = size.height
        )
    }

    fun DrawScope.drawRunner(
        state: GameState,
        playerAnimatedX: Float,
        width: Float,
        height: Float,
        vanishingX: Float,
        vanishingY: Float,
        roadStartXAtBottom: Float,
        laneWidthAtBottom: Float,
        cosmetic: RunnerCosmetic = CosmeticShopManager.equippedCosmetic.value
    ) {
        val playerLaneCenter = roadStartXAtBottom + playerAnimatedX * laneWidthAtBottom + laneWidthAtBottom * 0.5f

        val transitionProgress = state.transitionProgress
        val playerYRatio = if (state.isTransitioning) {
            if (transitionProgress < 0.5f) {
                val t = transitionProgress * 2f
                0.8f - t * 0.8f
            } else {
                val t = (transitionProgress - 0.5f) * 2f
                1.5f - t * 0.7f
            }
        } else {
            0.8f
        }

        val playerScale = playerYRatio.coerceIn(0.02f, 1.5f)
        val jumpVisualOffset = if (state.isJumping) {
            sin((state.jumpTime / 0.5f) * kotlin.math.PI.toFloat()) * (height * 0.19f)
        } else 0f

        val groundY = vanishingY + playerYRatio * (height - vanishingY)
        val playerScreenY = groundY - jumpVisualOffset
        val playerScreenX = vanishingX + (playerLaneCenter - vanishingX) * playerScale

        drawRunnerCharacterAt(
            state = state,
            cosmetic = cosmetic,
            playerScreenX = playerScreenX,
            playerScreenY = playerScreenY,
            groundY = groundY,
            playerScale = playerScale,
            jumpVisualOffset = jumpVisualOffset,
            screenHeight = height
        )
    }

    private fun DrawScope.drawRunnerCharacterAt(
        state: GameState,
        cosmetic: RunnerCosmetic,
        playerScreenX: Float,
        playerScreenY: Float,
        groundY: Float,
        playerScale: Float,
        jumpVisualOffset: Float,
        screenHeight: Float
    ) {
        val h = 92.dp.toPx() * playerScale
        val wScale = cosmetic.buildWidthScale
        val isSprinting = state.sprintTimeLeft > 0f

        // 1. Dynamic Ground Shadow under the Runner
        val jumpShadowScale = (1f - (jumpVisualOffset / (screenHeight * 0.25f).coerceAtLeast(1f))).coerceIn(0.35f, 1f)
        drawOval(
            color = Color.Black.copy(alpha = 0.38f * jumpShadowScale),
            topLeft = Offset(
                playerScreenX - h * 0.28f * wScale * jumpShadowScale,
                groundY - h * 0.06f * jumpShadowScale
            ),
            size = Size(h * 0.56f * wScale * jumpShadowScale, h * 0.13f * jumpShadowScale)
        )

        // 2. Biomechanical Stride Cycle & Body Kinematics
        val runCycle = if (state.isGameOver || state.isJumping || state.isSliding) {
            0f
        } else {
            val freq = if (isSprinting) 21f else 15f
            state.distance * freq
        }

        val strideSin = sin(runCycle)
        val doubleCos = cos(runCycle * 2f)

        // Vertical center-of-mass bounce & subtle lateral weight transfer
        val verticalBob = if (state.isGameOver || state.isJumping || state.isSliding) 0f else doubleCos * (h * 0.026f)
        val lateralSway = if (state.isGameOver || state.isJumping || state.isSliding) 0f else strideSin * (h * 0.018f)

        // Counter-rotation between pelvis and shoulders during running
        val shoulderTwist = if (state.isGameOver || state.isJumping || state.isSliding) 0f else strideSin * (h * 0.032f)
        val hipTwist = -shoulderTwist * 0.65f

        var pelvisX = playerScreenX + lateralSway
        var pelvisY = playerScreenY - h * 0.45f + verticalBob

        var thoraxX = pelvisX + (if (state.isGameOver) -h * 0.10f else lateralSway * 0.5f)
        var thoraxY = pelvisY - h * 0.27f

        if (state.isSliding) {
            pelvisY = playerScreenY - h * 0.16f
            pelvisX = playerScreenX - h * 0.04f
            thoraxY = playerScreenY - h * 0.31f
            thoraxX = playerScreenX + h * 0.05f
        } else if (state.isJumping) {
            pelvisY = playerScreenY - h * 0.48f
            thoraxY = pelvisY - h * 0.26f
        }

        val neckBaseX = thoraxX
        val neckBaseY = thoraxY - h * 0.07f
        val headCenterX = neckBaseX + (if (state.isSliding) h * 0.03f else 0f)
        val headCenterY = neckBaseY - h * 0.105f

        val lHip = Offset(pelvisX - h * 0.075f * wScale, pelvisY + hipTwist * 0.22f)
        val rHip = Offset(pelvisX + h * 0.075f * wScale, pelvisY - hipTwist * 0.22f)

        val lShoulder = Offset(thoraxX - h * 0.145f * wScale, thoraxY + shoulderTwist * 0.25f)
        val rShoulder = Offset(thoraxX + h * 0.145f * wScale, thoraxY - shoulderTwist * 0.25f)

        var lKnee: Offset
        var lAnkle: Offset
        var lToe: Offset
        var rKnee: Offset
        var rAnkle: Offset
        var rToe: Offset

        var lElbow: Offset
        var lWrist: Offset
        var rElbow: Offset
        var rWrist: Offset

        var lLegDepth: Float
        var rLegDepth: Float
        var lArmDepth: Float
        var rArmDepth: Float
        var lFootLift = 0f
        var rFootLift = 0f

        if (state.isSliding) {
            lKnee = Offset(lHip.x - h * 0.16f, playerScreenY - h * 0.09f)
            lAnkle = Offset(lKnee.x - h * 0.15f, playerScreenY - h * 0.04f)
            lToe = Offset(lAnkle.x - h * 0.05f, playerScreenY - h * 0.02f)

            rKnee = Offset(rHip.x + h * 0.13f, playerScreenY - h * 0.13f)
            rAnkle = Offset(rHip.x + h * 0.02f, playerScreenY - h * 0.05f)
            rToe = Offset(rAnkle.x + h * 0.04f, playerScreenY - h * 0.03f)

            lElbow = Offset(lShoulder.x - h * 0.13f, lShoulder.y + h * 0.12f)
            lWrist = Offset(lElbow.x - h * 0.06f, playerScreenY - h * 0.05f)

            rElbow = Offset(rShoulder.x + h * 0.16f, rShoulder.y - h * 0.03f)
            rWrist = Offset(rElbow.x + h * 0.12f, rElbow.y - h * 0.06f)

            lLegDepth = 0.2f
            rLegDepth = -0.1f
            lArmDepth = 0.25f
            rArmDepth = -0.2f
        } else if (state.isJumping) {
            lKnee = Offset(lHip.x - h * 0.03f, lHip.y + h * 0.12f)
            lAnkle = Offset(lKnee.x + h * 0.01f, lKnee.y + h * 0.12f)
            lToe = Offset(lAnkle.x, lAnkle.y + h * 0.03f)
            lFootLift = 0.14f

            rKnee = Offset(rHip.x + h * 0.03f, rHip.y + h * 0.18f)
            rAnkle = Offset(rKnee.x + h * 0.01f, rKnee.y + h * 0.17f)
            rToe = Offset(rAnkle.x, rAnkle.y + h * 0.03f)
            rFootLift = 0.05f

            lElbow = Offset(lShoulder.x - h * 0.11f, lShoulder.y - h * 0.04f)
            lWrist = Offset(lElbow.x - h * 0.05f, lElbow.y - h * 0.13f)

            rElbow = Offset(rShoulder.x + h * 0.11f, rShoulder.y - h * 0.04f)
            rWrist = Offset(rElbow.x + h * 0.05f, rElbow.y - h * 0.13f)

            lLegDepth = 0.15f
            rLegDepth = -0.15f
            lArmDepth = -0.1f
            rArmDepth = -0.1f
        } else {
            val lForward = strideSin
            val rForward = -strideSin

            lFootLift = ((-lForward).coerceAtLeast(0f)) * 0.17f
            rFootLift = ((-rForward).coerceAtLeast(0f)) * 0.17f

            val lKneeLift = (lForward.coerceAtLeast(0f)) * 0.06f
            val rKneeLift = (rForward.coerceAtLeast(0f)) * 0.06f

            lKnee = Offset(
                x = lHip.x + lForward * (h * 0.015f),
                y = lHip.y + h * (0.20f - lKneeLift)
            )
            lAnkle = Offset(
                x = lKnee.x + lForward * (h * 0.01f),
                y = lKnee.y + h * (0.21f - lFootLift * 1.4f)
            )
            lToe = Offset(lAnkle.x, lAnkle.y + h * 0.02f)

            rKnee = Offset(
                x = rHip.x - rForward * (h * 0.015f),
                y = rHip.y + h * (0.20f - rKneeLift)
            )
            rAnkle = Offset(
                x = rKnee.x - rForward * (h * 0.01f),
                y = rKnee.y + h * (0.21f - rFootLift * 1.4f)
            )
            rToe = Offset(rAnkle.x, rAnkle.y + h * 0.02f)

            val lArmBack = strideSin
            val rArmBack = -strideSin

            lElbow = Offset(
                x = lShoulder.x - h * 0.035f - lArmBack * (h * 0.025f),
                y = lShoulder.y + h * (0.14f - lArmBack * 0.025f)
            )
            lWrist = Offset(
                x = lElbow.x + h * (0.03f - lArmBack * 0.03f),
                y = lElbow.y + h * (0.09f - abs(lArmBack) * 0.04f)
            )

            rElbow = Offset(
                x = rShoulder.x + h * 0.035f + rArmBack * (h * 0.025f),
                y = rShoulder.y + h * (0.14f - rArmBack * 0.025f)
            )
            rWrist = Offset(
                x = rElbow.x - h * (0.03f - rArmBack * 0.03f),
                y = rElbow.y + h * (0.09f - abs(rArmBack) * 0.04f)
            )

            lLegDepth = -lForward * 0.2f
            rLegDepth = -rForward * 0.2f
            lArmDepth = lArmBack * 0.25f
            rArmDepth = rArmBack * 0.25f
        }

        // 3. Cosmetic Outfit & 3D Runner Model Palette
        val skinBase = cosmetic.skinBase
        val skinShade = cosmetic.skinShade
        val skinHighlight = Color.White.copy(alpha = 0.28f)

        val shortsBase = cosmetic.shortsBase
        val shortsShade = cosmetic.shortsShade
        val compressionColor = cosmetic.compressionColor

        val jerseyBase = cosmetic.jerseyBase
        val jerseyShade = cosmetic.jerseyShade
        val jerseyPanel = cosmetic.jerseyPanel
        val reflectiveStripe = cosmetic.reflectiveStripe

        val shoePrimary = cosmetic.shoePrimary
        val shoeSole = cosmetic.shoeSole
        val shoeAccent = cosmetic.shoeAccent
        val sockColor = cosmetic.sockColor

        val hairColor = cosmetic.hairColor
        val capColor = cosmetic.capColor
        val capAccent = cosmetic.capAccent
        val isCyborg = cosmetic.accessoryStyle == RunnerAccessoryStyle.ROYAL_CYBORG_CREST

        // Anatomical radii scaled by cosmetic buildWidthScale
        val hipR = h * 0.056f * wScale
        val kneeR = h * 0.038f * wScale
        val ankleR = h * 0.024f * wScale
        val shoulderR = h * 0.044f * wScale
        val elbowR = h * 0.031f * wScale
        val wristR = h * 0.022f * wScale

        val drawCommands = ArrayList<Pair<Float, () -> Unit>>(7)

        // Left Leg
        drawCommands.add(lLegDepth to {
            drawTaperedLimb(lHip, lKnee, hipR, kneeR, compressionColor, Color.Black.copy(alpha = 0.35f), Color.White.copy(alpha = 0.12f))
            drawTaperedLimb(lKnee, lAnkle, kneeR * 1.05f, ankleR, skinBase, skinShade.copy(alpha = 0.45f), skinHighlight)
            if (isCyborg) {
                drawCircle(reflectiveStripe, radius = kneeR * 0.55f, center = lKnee)
            }
            val sockTop = Offset(
                lKnee.x + (lAnkle.x - lKnee.x) * 0.68f,
                lKnee.y + (lAnkle.y - lKnee.y) * 0.68f
            )
            drawTaperedLimb(sockTop, lAnkle, ankleR * 1.18f, ankleR * 1.05f, sockColor, Color.Gray.copy(alpha = 0.3f), Color.White)
            drawRunningShoe(lAnkle, lToe, playerScale, lFootLift, shoePrimary, shoeSole, shoeAccent)
        })

        // Right Leg
        drawCommands.add(rLegDepth to {
            drawTaperedLimb(rHip, rKnee, hipR, kneeR, compressionColor, Color.Black.copy(alpha = 0.35f), Color.White.copy(alpha = 0.12f))
            drawTaperedLimb(rKnee, rAnkle, kneeR * 1.05f, ankleR, skinBase, skinShade.copy(alpha = 0.45f), skinHighlight)
            if (isCyborg) {
                drawCircle(reflectiveStripe, radius = kneeR * 0.55f, center = rKnee)
            }
            val sockTop = Offset(
                rKnee.x + (rAnkle.x - rKnee.x) * 0.68f,
                rKnee.y + (rAnkle.y - rKnee.y) * 0.68f
            )
            drawTaperedLimb(sockTop, rAnkle, ankleR * 1.18f, ankleR * 1.05f, sockColor, Color.Gray.copy(alpha = 0.3f), Color.White)
            drawRunningShoe(rAnkle, rToe, playerScale, rFootLift, shoePrimary, shoeSole, shoeAccent)
        })

        // Left Arm
        drawCommands.add(lArmDepth to {
            drawTaperedLimb(lShoulder, lElbow, shoulderR, elbowR, skinBase, skinShade.copy(alpha = 0.45f), skinHighlight)
            val sleeveEnd = Offset(
                lShoulder.x + (lElbow.x - lShoulder.x) * 0.42f,
                lShoulder.y + (lElbow.y - lShoulder.y) * 0.42f
            )
            drawTaperedLimb(lShoulder, sleeveEnd, shoulderR * 1.08f, shoulderR * 0.92f, jerseyBase, jerseyShade.copy(alpha = 0.4f), Color.White.copy(alpha = 0.18f))
            drawTaperedLimb(lElbow, lWrist, elbowR, wristR, skinBase, skinShade.copy(alpha = 0.45f), skinHighlight)
            if (isCyborg) {
                drawCircle(reflectiveStripe, radius = elbowR * 0.55f, center = lElbow)
            }
            drawCircle(shortsBase, radius = wristR * 1.15f, center = lWrist)
            drawCircle(skinBase, radius = wristR * 1.25f, center = Offset(lWrist.x, lWrist.y + wristR * 0.6f))
        })

        // Right Arm
        drawCommands.add(rArmDepth to {
            drawTaperedLimb(rShoulder, rElbow, shoulderR, elbowR, skinBase, skinShade.copy(alpha = 0.45f), skinHighlight)
            val sleeveEnd = Offset(
                rShoulder.x + (rElbow.x - rShoulder.x) * 0.42f,
                rShoulder.y + (rElbow.y - rShoulder.y) * 0.42f
            )
            drawTaperedLimb(rShoulder, sleeveEnd, shoulderR * 1.08f, shoulderR * 0.92f, jerseyBase, jerseyShade.copy(alpha = 0.4f), Color.White.copy(alpha = 0.18f))
            drawTaperedLimb(rElbow, rWrist, elbowR, wristR, skinBase, skinShade.copy(alpha = 0.45f), skinHighlight)
            if (isCyborg) {
                drawCircle(reflectiveStripe, radius = elbowR * 0.55f, center = rElbow)
            }
            drawCircle(shortsBase, radius = wristR * 1.15f, center = rWrist)
            drawCircle(skinBase, radius = wristR * 1.25f, center = Offset(rWrist.x, rWrist.y + wristR * 0.6f))
        })

        // Athletic Torso, Running Shorts & 3D Back Gear (Depth 0f)
        drawCommands.add(0f to {
            val waistLeft = pelvisX - h * 0.105f * wScale
            val waistRight = pelvisX + h * 0.105f * wScale
            val waistY = pelvisY - h * 0.04f

            // Running Shorts
            sharedPath.reset()
            sharedPath.moveTo(waistLeft, waistY)
            sharedPath.lineTo(waistRight, waistY)
            sharedPath.lineTo(rHip.x + hipR * 1.1f, pelvisY + h * 0.085f)
            sharedPath.lineTo(pelvisX + h * 0.01f, pelvisY + h * 0.055f)
            sharedPath.lineTo(pelvisX - h * 0.01f, pelvisY + h * 0.055f)
            sharedPath.lineTo(lHip.x - hipR * 1.1f, pelvisY + h * 0.085f)
            sharedPath.close()
            drawPath(sharedPath, shortsBase)

            // Right-half shadow on shorts
            sharedPath.reset()
            sharedPath.moveTo(pelvisX, waistY)
            sharedPath.lineTo(waistRight, waistY)
            sharedPath.lineTo(rHip.x + hipR * 1.1f, pelvisY + h * 0.085f)
            sharedPath.lineTo(pelvisX, pelvisY + h * 0.055f)
            sharedPath.close()
            drawPath(sharedPath, shortsShade.copy(alpha = 0.55f))

            // Anatomical V-Tapered Athletic Jersey
            val shLeftX = lShoulder.x - shoulderR * 0.35f
            val shRightX = rShoulder.x + shoulderR * 0.35f
            val shLeftY = lShoulder.y - shoulderR * 0.35f
            val shRightY = rShoulder.y - shoulderR * 0.35f

            sharedPath.reset()
            sharedPath.moveTo(shLeftX, shLeftY)
            sharedPath.lineTo(shRightX, shRightY)
            sharedPath.quadraticTo(
                waistRight + h * 0.02f,
                (shRightY + waistY) * 0.5f,
                waistRight,
                waistY + h * 0.02f
            )
            sharedPath.lineTo(waistLeft, waistY + h * 0.02f)
            sharedPath.quadraticTo(
                waistLeft - h * 0.02f,
                (shLeftY + waistY) * 0.5f,
                shLeftX,
                shLeftY
            )
            sharedPath.close()
            drawPath(sharedPath, jerseyBase)

            // 3D Torso Side Shading
            sharedPath.reset()
            sharedPath.moveTo(thoraxX, (shLeftY + shRightY) * 0.5f)
            sharedPath.lineTo(shRightX, shRightY)
            sharedPath.quadraticTo(
                waistRight + h * 0.02f,
                (shRightY + waistY) * 0.5f,
                waistRight,
                waistY + h * 0.02f
            )
            sharedPath.lineTo(pelvisX, waistY + h * 0.02f)
            sharedPath.close()
            drawPath(sharedPath, jerseyShade.copy(alpha = 0.45f))

            // Spine Panel & Reflective Stripe
            val spineTop = Offset(thoraxX, (shLeftY + shRightY) * 0.5f + h * 0.02f)
            val spineBot = Offset(pelvisX, waistY)
            drawLine(
                color = jerseyPanel,
                start = spineTop,
                end = spineBot,
                strokeWidth = h * 0.075f * wScale,
                cap = StrokeCap.Round
            )
            drawLine(
                color = reflectiveStripe,
                start = Offset(spineTop.x, spineTop.y + h * 0.02f),
                end = Offset(spineBot.x, spineBot.y - h * 0.03f),
                strokeWidth = (h * 0.016f).coerceAtLeast(1.5f),
                cap = StrokeCap.Round
            )

            // Model-Specific 3D Back Accessories
            when (cosmetic.accessoryStyle) {
                RunnerAccessoryStyle.CYBER_NINJA_SCARF -> {
                    // Twin crossed cyber-katana scabbards & glowing hilts on the back
                    val scabbardColor = Color(0xFF0D0D18)
                    drawLine(
                        color = scabbardColor,
                        start = Offset(shLeftX - h * 0.03f, shLeftY - h * 0.06f),
                        end = Offset(waistRight - h * 0.02f, waistY - h * 0.02f),
                        strokeWidth = (h * 0.028f).coerceAtLeast(2f),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = reflectiveStripe,
                        start = Offset(shLeftX - h * 0.03f, shLeftY - h * 0.06f),
                        end = Offset(shLeftX + h * 0.03f, shLeftY + h * 0.01f),
                        strokeWidth = (h * 0.032f).coerceAtLeast(2f),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = scabbardColor,
                        start = Offset(shRightX + h * 0.03f, shRightY - h * 0.06f),
                        end = Offset(waistLeft + h * 0.02f, waistY - h * 0.02f),
                        strokeWidth = (h * 0.028f).coerceAtLeast(2f),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFF00E5FF),
                        start = Offset(shRightX + h * 0.03f, shRightY - h * 0.06f),
                        end = Offset(shRightX - h * 0.03f, shRightY + h * 0.01f),
                        strokeWidth = (h * 0.032f).coerceAtLeast(2f),
                        cap = StrokeCap.Round
                    )
                }
                RunnerAccessoryStyle.PARKOUR_HOODIE_PACK -> {
                    // Diagonal cross-body strap & compact urban sling backpack
                    drawLine(
                        color = Color(0xFF1C262B),
                        start = Offset(shLeftX + h * 0.02f, shLeftY),
                        end = Offset(waistRight - h * 0.02f, waistY),
                        strokeWidth = (h * 0.032f).coerceAtLeast(2f)
                    )
                    val packW = h * 0.14f
                    val packH = h * 0.17f
                    drawRoundRect(
                        color = Color(0xFF263238),
                        topLeft = Offset(thoraxX - packW * 0.5f, thoraxY - h * 0.01f),
                        size = Size(packW, packH),
                        cornerRadius = CornerRadius(h * 0.03f)
                    )
                    drawLine(
                        color = reflectiveStripe,
                        start = Offset(thoraxX - packW * 0.35f, thoraxY + packH * 0.5f),
                        end = Offset(thoraxX + packW * 0.35f, thoraxY + packH * 0.5f),
                        strokeWidth = (h * 0.018f).coerceAtLeast(1.5f),
                        cap = StrokeCap.Round
                    )
                }
                RunnerAccessoryStyle.ROYAL_CYBORG_CREST -> {
                    // Dual metallic jet-thruster pods on upper back with glowing exhaust ports
                    val podW = h * 0.055f
                    val podH = h * 0.14f
                    for (side in -1..1 step 2) {
                        val px = thoraxX + side * h * 0.065f - podW * 0.5f
                        val py = thoraxY - h * 0.02f
                        drawRoundRect(
                            color = Color(0xFF546E7A),
                            topLeft = Offset(px, py),
                            size = Size(podW, podH),
                            cornerRadius = CornerRadius(podW * 0.35f)
                        )
                        drawCircle(
                            color = Color(0xFF00E5FF),
                            radius = podW * 0.42f,
                            center = Offset(px + podW * 0.5f, py + podH)
                        )
                    }
                }
                RunnerAccessoryStyle.OLYMPIC_VISOR_PONYTAIL -> {
                    // Championship Gold Athlete Bib on back
                    val bibW = h * 0.13f
                    val bibH = h * 0.09f
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(thoraxX - bibW * 0.5f, thoraxY + h * 0.03f),
                        size = Size(bibW, bibH),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                    drawLine(
                        color = Color(0xFF004D40),
                        start = Offset(thoraxX - bibW * 0.3f, thoraxY + h * 0.075f),
                        end = Offset(thoraxX + bibW * 0.3f, thoraxY + h * 0.075f),
                        strokeWidth = (h * 0.02f).coerceAtLeast(1.5f)
                    )
                }
                else -> {}
            }

            // Collarband
            drawOval(
                color = jerseyPanel,
                topLeft = Offset(neckBaseX - h * 0.065f, shLeftY - h * 0.015f),
                size = Size(h * 0.13f, h * 0.04f)
            )
        })

        // Anatomical Neck, Head, Hair & Model-Specific 3D Headgear (Depth +0.15f)
        drawCommands.add(0.15f to {
            val headR = h * 0.095f

            // Neck column
            drawTaperedLimb(
                start = Offset(neckBaseX, neckBaseY + h * 0.025f),
                end = Offset(headCenterX, headCenterY + headR * 0.55f),
                startRadius = headR * 0.46f,
                endRadius = headR * 0.42f,
                baseColor = skinBase,
                shadeColor = skinShade.copy(alpha = 0.5f),
                highlightColor = skinHighlight
            )

            // Left & Right Ears
            drawOval(
                color = skinShade,
                topLeft = Offset(headCenterX - headR * 1.12f, headCenterY - headR * 0.18f),
                size = Size(headR * 0.32f, headR * 0.46f)
            )
            drawOval(
                color = skinShade,
                topLeft = Offset(headCenterX + headR * 0.80f, headCenterY - headR * 0.18f),
                size = Size(headR * 0.32f, headR * 0.46f)
            )

            // Cranial Head Oval
            drawOval(
                color = skinBase,
                topLeft = Offset(headCenterX - headR * 0.95f, headCenterY - headR * 1.05f),
                size = Size(headR * 1.90f, headR * 2.10f)
            )

            // Textured Hair at Nape
            sharedPath.reset()
            sharedPath.moveTo(headCenterX - headR * 0.92f, headCenterY - headR * 0.12f)
            sharedPath.lineTo(headCenterX + headR * 0.92f, headCenterY - headR * 0.12f)
            sharedPath.lineTo(headCenterX + headR * 0.72f, headCenterY + headR * 0.58f)
            sharedPath.quadraticTo(
                headCenterX,
                headCenterY + headR * 0.72f,
                headCenterX - headR * 0.72f,
                headCenterY + headR * 0.58f
            )
            sharedPath.close()
            drawPath(sharedPath, hairColor)

            // Fitted Crown / Helmet Base
            sharedPath.reset()
            sharedPath.moveTo(headCenterX - headR * 0.98f, headCenterY - headR * 0.08f)
            sharedPath.quadraticTo(
                headCenterX - headR * 0.95f,
                headCenterY - headR * 1.18f,
                headCenterX,
                headCenterY - headR * 1.18f
            )
            sharedPath.quadraticTo(
                headCenterX + headR * 0.95f,
                headCenterY - headR * 1.18f,
                headCenterX + headR * 0.98f,
                headCenterY - headR * 0.08f
            )
            sharedPath.close()
            drawPath(sharedPath, capColor)

            // Right-Side 3D Shading on Headgear
            sharedPath.reset()
            sharedPath.moveTo(headCenterX, headCenterY - headR * 1.18f)
            sharedPath.quadraticTo(
                headCenterX + headR * 0.95f,
                headCenterY - headR * 1.18f,
                headCenterX + headR * 0.98f,
                headCenterY - headR * 0.08f
            )
            sharedPath.lineTo(headCenterX, headCenterY - headR * 0.08f)
            sharedPath.close()
            drawPath(sharedPath, Color.Black.copy(alpha = 0.26f))

            // Model-Specific 3D Headgear & Dynamic Hair/Cloth Elements
            when (cosmetic.accessoryStyle) {
                RunnerAccessoryStyle.CYBER_NINJA_SCARF -> {
                    // Glowing neon wrap-around cyber headband & billowing twin scarf tails
                    drawLine(
                        color = capAccent,
                        start = Offset(headCenterX - headR * 0.98f, headCenterY - headR * 0.06f),
                        end = Offset(headCenterX + headR * 0.98f, headCenterY - headR * 0.06f),
                        strokeWidth = (3.2.dp.toPx() * playerScale).coerceAtLeast(1.5f),
                        cap = StrokeCap.Round
                    )
                    val flutter1 = sin(runCycle * 1.3f) * headR * 0.85f
                    val flutter2 = cos(runCycle * 1.3f) * headR * 0.65f
                    sharedPath.reset()
                    sharedPath.moveTo(headCenterX + headR * 0.35f, headCenterY)
                    sharedPath.quadraticTo(
                        headCenterX + headR * 1.1f + flutter1 * 0.5f,
                        headCenterY + headR * 0.9f,
                        headCenterX + headR * 1.65f + flutter1,
                        headCenterY + headR * 1.95f
                    )
                    sharedPath.lineTo(headCenterX + headR * 1.25f + flutter1, headCenterY + headR * 2.1f)
                    sharedPath.quadraticTo(
                        headCenterX + headR * 0.7f,
                        headCenterY + headR * 1.0f,
                        headCenterX + headR * 0.1f,
                        headCenterY + headR * 0.15f
                    )
                    sharedPath.close()
                    drawPath(sharedPath, capAccent)

                    drawLine(
                        color = Color(0xFF00E5FF),
                        start = Offset(headCenterX - headR * 0.2f, headCenterY + headR * 0.1f),
                        end = Offset(headCenterX - headR * 1.1f + flutter2, headCenterY + headR * 1.6f),
                        strokeWidth = (3.dp.toPx() * playerScale).coerceAtLeast(1.5f),
                        cap = StrokeCap.Round
                    )
                }
                RunnerAccessoryStyle.PARKOUR_HOODIE_PACK -> {
                    // Sculpted 3D Hoodie cowl around neck & shoulders
                    drawOval(
                        color = capColor,
                        topLeft = Offset(headCenterX - headR * 1.25f, headCenterY + headR * 0.35f),
                        size = Size(headR * 2.5f, headR * 0.75f)
                    )
                    drawLine(
                        color = capAccent,
                        start = Offset(headCenterX - headR * 0.85f, headCenterY + headR * 0.65f),
                        end = Offset(headCenterX + headR * 0.85f, headCenterY + headR * 0.65f),
                        strokeWidth = (2.dp.toPx() * playerScale).coerceAtLeast(1f),
                        cap = StrokeCap.Round
                    )
                }
                RunnerAccessoryStyle.DESERT_NOMAD_KEFFIYEH -> {
                    // Flowing white desert keffiyeh headcloth with gold agal cord
                    val sway = sin(runCycle) * headR * 0.35f
                    sharedPath.reset()
                    sharedPath.moveTo(headCenterX - headR * 0.96f, headCenterY - headR * 0.1f)
                    sharedPath.lineTo(headCenterX + headR * 0.96f, headCenterY - headR * 0.1f)
                    sharedPath.lineTo(headCenterX + headR * 1.18f + sway, headCenterY + headR * 1.35f)
                    sharedPath.lineTo(headCenterX - headR * 1.18f + sway, headCenterY + headR * 1.35f)
                    sharedPath.close()
                    drawPath(sharedPath, capColor)
                    // Double gold agal cord around crown
                    drawLine(
                        color = capAccent,
                        start = Offset(headCenterX - headR * 0.95f, headCenterY - headR * 0.32f),
                        end = Offset(headCenterX + headR * 0.95f, headCenterY - headR * 0.32f),
                        strokeWidth = (2.8.dp.toPx() * playerScale).coerceAtLeast(1.5f),
                        cap = StrokeCap.Round
                    )
                }
                RunnerAccessoryStyle.ROYAL_CYBORG_CREST -> {
                    // Tall golden cyber-plume helmet crest & glowing visor strip
                    sharedPath.reset()
                    sharedPath.moveTo(headCenterX, headCenterY - headR * 1.75f)
                    sharedPath.lineTo(headCenterX + headR * 0.32f, headCenterY - headR * 0.45f)
                    sharedPath.lineTo(headCenterX - headR * 0.32f, headCenterY - headR * 0.45f)
                    sharedPath.close()
                    drawPath(sharedPath, capAccent)
                    drawLine(
                        color = Color(0xFF00E5FF),
                        start = Offset(headCenterX - headR * 0.96f, headCenterY - headR * 0.12f),
                        end = Offset(headCenterX + headR * 0.96f, headCenterY - headR * 0.12f),
                        strokeWidth = (2.8.dp.toPx() * playerScale).coerceAtLeast(1.5f)
                    )
                }
                RunnerAccessoryStyle.OLYMPIC_VISOR_PONYTAIL -> {
                    // Bouncing athletic ponytail + golden sport visor band
                    val bounceY = sin(runCycle * 2f) * headR * 0.25f
                    val swayX = cos(runCycle) * headR * 0.35f
                    drawLine(
                        color = capAccent,
                        start = Offset(headCenterX - headR * 0.96f, headCenterY - headR * 0.12f),
                        end = Offset(headCenterX + headR * 0.96f, headCenterY - headR * 0.12f),
                        strokeWidth = (3.dp.toPx() * playerScale).coerceAtLeast(1.5f)
                    )
                    sharedPath.reset()
                    sharedPath.moveTo(headCenterX - headR * 0.22f, headCenterY)
                    sharedPath.quadraticTo(
                        headCenterX + swayX * 0.6f,
                        headCenterY + headR * 0.7f + bounceY,
                        headCenterX + swayX,
                        headCenterY + headR * 1.45f + bounceY
                    )
                    sharedPath.lineTo(headCenterX - headR * 0.25f + swayX, headCenterY + headR * 1.45f + bounceY)
                    sharedPath.close()
                    drawPath(sharedPath, hairColor)
                }
                RunnerAccessoryStyle.CLASSIC_CAP -> {
                    drawArc(
                        color = hairColor,
                        startAngle = 195f,
                        sweepAngle = 150f,
                        useCenter = true,
                        topLeft = Offset(headCenterX - headR * 0.38f, headCenterY - headR * 0.36f),
                        size = Size(headR * 0.76f, headR * 0.56f)
                    )
                    drawLine(
                        color = capAccent,
                        start = Offset(headCenterX - headR * 0.94f, headCenterY - headR * 0.10f),
                        end = Offset(headCenterX + headR * 0.94f, headCenterY - headR * 0.10f),
                        strokeWidth = (2.dp.toPx() * playerScale).coerceAtLeast(1f)
                    )
                }
            }
        })

        drawCommands.sortBy { it.first }
        for (cmd in drawCommands) {
            cmd.second()
        }

        // 4. City Sprint & Shield Visual Effects on Runner
        if (isSprinting) {
            val auraColor = Color(0xFF00E5FF).copy(alpha = 0.4f + 0.2f * sin(state.distance * 25f))
            drawCircle(
                color = auraColor,
                radius = h * 0.72f,
                center = Offset(playerScreenX, playerScreenY - h * 0.46f),
                style = Stroke(width = 3.5.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = h * 0.58f,
                center = Offset(playerScreenX, playerScreenY - h * 0.46f),
                style = Stroke(width = 1.8.dp.toPx())
            )
        }

        if (state.hasShield) {
            val shieldRadius = h * 0.82f
            val shieldCenter = Offset(playerScreenX, playerScreenY - h * 0.46f)
            val pulse = 0.82f + 0.18f * sin(state.distance * 10f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF64FFDA).copy(alpha = 0.28f * pulse)
                    ),
                    center = shieldCenter,
                    radius = shieldRadius
                ),
                radius = shieldRadius,
                center = shieldCenter
            )
            drawCircle(
                color = Color(0xFF1DE9B6).copy(alpha = 0.85f),
                radius = shieldRadius,
                center = shieldCenter,
                style = Stroke(width = 3.dp.toPx())
            )
            drawArc(
                color = Color.White.copy(alpha = 0.55f),
                startAngle = 205f,
                sweepAngle = 55f,
                useCenter = false,
                topLeft = Offset(shieldCenter.x - shieldRadius * 0.88f, shieldCenter.y - shieldRadius * 0.88f),
                size = Size(shieldRadius * 1.76f, shieldRadius * 1.76f),
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
