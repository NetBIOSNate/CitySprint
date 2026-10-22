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
import kotlin.math.sin

object ObstacleRenderer {

    private val sharedPath = Path()

    fun DrawScope.drawObstacles(
        obstacles: List<Obstacle>,
        distance: Float,
        width: Float,
        height: Float,
        vanishingX: Float,
        vanishingY: Float,
        roadStartXAtBottom: Float,
        laneWidthAtBottom: Float
    ) {
        for (obs in obstacles.sortedBy { it.yRatio }) {
            if (obs.yRatio < 0f) continue
            val laneCenter = roadStartXAtBottom + obs.lane * laneWidthAtBottom + laneWidthAtBottom * 0.5f

            val perspective = obs.yRatio * obs.yRatio * obs.yRatio
            val scale = perspective.coerceAtMost(5f)
            val screenY = vanishingY + perspective * (height - vanishingY)
            val screenX = vanishingX + (laneCenter - vanishingX) * scale

            val obsWidth = 84.dp.toPx() * scale
            val obsHeight = 84.dp.toPx() * scale
            if (obsWidth < 1.5f) continue

            val alpha = (obs.yRatio * 5f).coerceIn(0f, 1f)
            val colType = getCollisionType(obs.type)

            // Guarantee pronounced 3D lateral depth even in the middle lane (lane == 1)
            // Left lane shows right 3D side; Right lane shows left 3D side;
            // Center lane uses a 3/4 oblique yaw angle so its 3D side wall is always visible.
            val rawDx = (vanishingX - screenX) * 0.28f
            val minObliqueDx = obsWidth * 0.30f
            val dx3D = when {
                obs.lane == 0 -> rawDx.coerceAtLeast(minObliqueDx)
                obs.lane == 2 -> rawDx.coerceAtMost(-minObliqueDx)
                else -> if (obs.id % 2 == 0) minObliqueDx * 0.85f else -minObliqueDx * 0.85f
            }

            when (colType) {
                ObstacleCollisionType.HIGH -> drawHighObstacle(
                    obs = obs,
                    screenX = screenX,
                    screenY = screenY,
                    obsWidth = obsWidth,
                    obsHeight = obsHeight,
                    dx3D = dx3D,
                    vanishingY = vanishingY,
                    distance = distance,
                    alpha = alpha
                )
                ObstacleCollisionType.LOW -> drawLowObstacle(
                    obs = obs,
                    screenX = screenX,
                    screenY = screenY,
                    obsWidth = obsWidth,
                    obsHeight = obsHeight,
                    dx3D = dx3D,
                    vanishingY = vanishingY,
                    alpha = alpha
                )
                ObstacleCollisionType.SOLID -> drawSolidObstacle(
                    obs = obs,
                    screenX = screenX,
                    screenY = screenY,
                    obsWidth = obsWidth,
                    obsHeight = obsHeight,
                    dx3D = dx3D,
                    vanishingY = vanishingY,
                    scale = scale,
                    alpha = alpha
                )
            }
        }
    }

    /**
     * Renders a true 3D perspective hexahedron (box) with:
     * - Receding 3D Side Face (left or right depending on dx)
     * - Receding 3D Top Face (when below the horizon) OR Receding 3D Bottom/Underside Face (when above the horizon)
     * - Shaded Front Face
     * - Crisp 3D specular edge highlights along the 3D seams so the volume pops visually.
     */
    private fun DrawScope.draw3DBox(
        leftX: Float,
        rightX: Float,
        topY: Float,
        bottomY: Float,
        dx: Float,
        dyTop: Float,
        dyBottom: Float,
        frontColor: Color,
        sideColor: Color,
        topColor: Color,
        bottomColor: Color = sideColor,
        cornerRadius: Float = 0f,
        drawEdges: Boolean = true
    ) {
        // 1. Receding 3D Side Face
        sharedPath.reset()
        if (dx >= 0f) {
            sharedPath.moveTo(rightX, topY)
            sharedPath.lineTo(rightX + dx, topY + dyTop)
            sharedPath.lineTo(rightX + dx, bottomY + dyBottom)
            sharedPath.lineTo(rightX, bottomY)
        } else {
            sharedPath.moveTo(leftX, topY)
            sharedPath.lineTo(leftX + dx, topY + dyTop)
            sharedPath.lineTo(leftX + dx, bottomY + dyBottom)
            sharedPath.lineTo(leftX, bottomY)
        }
        sharedPath.close()
        drawPath(sharedPath, sideColor)

        // 2. Receding 3D Top Face (visible when dyTop < 0) OR 3D Underside Face (visible when dyBottom > 0)
        if (dyTop < 0f) {
            sharedPath.reset()
            sharedPath.moveTo(leftX, topY)
            sharedPath.lineTo(rightX, topY)
            sharedPath.lineTo(rightX + dx, topY + dyTop)
            sharedPath.lineTo(leftX + dx, topY + dyTop)
            sharedPath.close()
            drawPath(sharedPath, topColor)
        }
        if (dyBottom > 0f) {
            sharedPath.reset()
            sharedPath.moveTo(leftX, bottomY)
            sharedPath.lineTo(rightX, bottomY)
            sharedPath.lineTo(rightX + dx, bottomY + dyBottom)
            sharedPath.lineTo(leftX + dx, bottomY + dyBottom)
            sharedPath.close()
            drawPath(sharedPath, bottomColor)
        }

        // 3. Front Face
        if (cornerRadius > 0f) {
            drawRoundRect(
                color = frontColor,
                topLeft = Offset(leftX, topY),
                size = Size((rightX - leftX).coerceAtLeast(1f), (bottomY - topY).coerceAtLeast(1f)),
                cornerRadius = CornerRadius(cornerRadius)
            )
        } else {
            drawRect(
                color = frontColor,
                topLeft = Offset(leftX, topY),
                size = Size((rightX - leftX).coerceAtLeast(1f), (bottomY - topY).coerceAtLeast(1f))
            )
        }

        // 4. 3D Crease & Rim Highlights to accentuate 3D geometry
        if (drawEdges && (rightX - leftX) > 6f) {
            val edgeStroke = ((rightX - leftX) * 0.022f).coerceIn(1f, 3.5f)
            val highlight = Color.White.copy(alpha = 0.28f * frontColor.alpha)
            val darkEdge = Color.Black.copy(alpha = 0.28f * frontColor.alpha)

            // Top front edge highlight
            drawLine(highlight, Offset(leftX, topY), Offset(rightX, topY), strokeWidth = edgeStroke)
            // Vertical crease between front face and receding 3D side face
            val creaseX = if (dx >= 0f) rightX else leftX
            drawLine(highlight, Offset(creaseX, topY), Offset(creaseX, bottomY), strokeWidth = edgeStroke)
            // Far 3D outer profile edge
            drawLine(darkEdge, Offset(creaseX + dx, topY + dyTop), Offset(creaseX + dx, bottomY + dyBottom), strokeWidth = edgeStroke)
        }
    }

    /**
     * Renders a true 3D vertical cylinder with shaded curved wall, 3D bottom ring, and 3D top elliptical cap.
     */
    private fun DrawScope.draw3DCylinder(
        centerX: Float,
        topY: Float,
        bottomY: Float,
        radiusX: Float,
        ellipseRy: Float,
        baseColor: Color,
        highlightColor: Color,
        shadowColor: Color,
        topCapColor: Color
    ) {
        val h = (bottomY - topY).coerceAtLeast(1f)
        // Bottom ellipse base
        drawOval(
            color = shadowColor,
            topLeft = Offset(centerX - radiusX, bottomY - ellipseRy),
            size = Size(radiusX * 2f, ellipseRy * 2f)
        )
        // Cylindrical body with horizontal 3D specular-to-shadow gradient
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(shadowColor, highlightColor, baseColor, shadowColor),
                startX = centerX - radiusX,
                endX = centerX + radiusX
            ),
            topLeft = Offset(centerX - radiusX, topY),
            size = Size(radiusX * 2f, h)
        )
        // Top 3D elliptical cap
        drawOval(
            color = topCapColor,
            topLeft = Offset(centerX - radiusX, topY - ellipseRy),
            size = Size(radiusX * 2f, ellipseRy * 2f)
        )
        // Top rim highlight
        drawOval(
            color = Color.White.copy(alpha = 0.30f * baseColor.alpha),
            topLeft = Offset(centerX - radiusX, topY - ellipseRy),
            size = Size(radiusX * 2f, ellipseRy * 2f),
            style = Stroke(width = (radiusX * 0.06f).coerceAtLeast(1f))
        )
    }

    /**
     * Renders a 3D wheel with tire tread thickness (dxWidth), sidewall, and metallic rim.
     */
    private fun DrawScope.draw3DWheel(
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
        treadDx: Float,
        alpha: Float
    ) {
        val tireTread = Color(0xFF141414).copy(alpha = alpha)
        val tireWall = Color(0xFF262626).copy(alpha = alpha)
        val rimSilver = Color(0xFFCFD8DC).copy(alpha = alpha)

        // Tire tread thickness (back cylinder)
        drawOval(
            color = tireTread,
            topLeft = Offset(centerX - radiusX + treadDx, centerY - radiusY),
            size = Size(radiusX * 2f, radiusY * 2f)
        )
        drawRect(
            color = tireTread,
            topLeft = Offset(if (treadDx >= 0f) centerX else centerX + treadDx, centerY - radiusY),
            size = Size(abs(treadDx), radiusY * 2f)
        )
        // Front tire sidewall
        drawOval(
            color = tireWall,
            topLeft = Offset(centerX - radiusX, centerY - radiusY),
            size = Size(radiusX * 2f, radiusY * 2f)
        )
        // Alloy rim & hubcap
        drawOval(
            color = rimSilver,
            topLeft = Offset(centerX - radiusX * 0.55f, centerY - radiusY * 0.55f),
            size = Size(radiusX * 1.1f, radiusY * 1.1f)
        )
        drawOval(
            color = tireTread,
            topLeft = Offset(centerX - radiusX * 0.22f, centerY - radiusY * 0.22f),
            size = Size(radiusX * 0.44f, radiusY * 0.44f)
        )
    }

    private fun DrawScope.drawHighObstacle(
        obs: Obstacle,
        screenX: Float,
        screenY: Float,
        obsWidth: Float,
        obsHeight: Float,
        dx3D: Float,
        vanishingY: Float,
        distance: Float,
        alpha: Float
    ) {
        val clearanceY = screenY - obsHeight * 0.78f
        val topY = screenY - obsHeight * 1.48f
        val postW = obsWidth * 0.13f

        // 3D depth vector for overhead structures
        val postDx = dx3D * 0.42f
        val dyTop = ((vanishingY - topY) * 0.14f).coerceIn(-obsHeight * 0.22f, obsHeight * 0.22f)
        val dyClearance = ((vanishingY - clearanceY) * 0.14f).coerceIn(-obsHeight * 0.18f, obsHeight * 0.22f)
        val dyGround = -obsHeight * 0.08f
        // Ensure visible 3D underside on overhead beams
        val beamDyBottom = dyClearance.coerceAtLeast(obsHeight * 0.11f)
        val beamDyTop = dyTop.coerceAtMost(-obsHeight * 0.08f)

        // Ground shadow under both 3D support columns and overhead span
        drawOval(
            color = Color.Black.copy(alpha = 0.34f * alpha),
            topLeft = Offset(screenX - obsWidth * 0.98f, screenY - obsHeight * 0.11f),
            size = Size(obsWidth * 1.96f, obsHeight * 0.22f)
        )

        when (obs.type) {
            "Traffic Light" -> {
                val steelFront = Color(0xFF546E7A).copy(alpha = alpha)
                val steelSide = Color(0xFF37474F).copy(alpha = alpha)
                val steelTop = Color(0xFF78909C).copy(alpha = alpha)
                val steelBottom = Color(0xFF263238).copy(alpha = alpha)
                val yellowFront = Color(0xFFFBC02D).copy(alpha = alpha)
                val yellowSide = Color(0xFFF57F17).copy(alpha = alpha)

                // Left & Right 3D Steel Gantry Columns + 3D Concrete Footings
                val leftColX = screenX - obsWidth * 0.88f
                val rightColX = screenX + obsWidth * 0.75f
                for (colX in listOf(leftColX, rightColX)) {
                    // 3D Column shaft
                    draw3DBox(
                        leftX = colX,
                        rightX = colX + postW,
                        topY = topY,
                        bottomY = screenY,
                        dx = postDx,
                        dyTop = dyTop,
                        dyBottom = dyGround,
                        frontColor = steelFront,
                        sideColor = steelSide,
                        topColor = steelTop
                    )
                    // 3D bolted base pedestal
                    draw3DBox(
                        leftX = colX - postW * 0.25f,
                        rightX = colX + postW * 1.25f,
                        topY = screenY - obsHeight * 0.14f,
                        bottomY = screenY,
                        dx = postDx * 1.1f,
                        dyTop = dyGround,
                        dyBottom = dyGround,
                        frontColor = steelSide,
                        sideColor = steelBottom,
                        topColor = steelTop
                    )
                }

                // Overhead 3D Cantilevered Mast Box Beam
                draw3DBox(
                    leftX = screenX - obsWidth * 0.92f,
                    rightX = screenX + obsWidth * 0.92f,
                    topY = topY,
                    bottomY = topY + obsHeight * 0.14f,
                    dx = postDx * 1.2f,
                    dyTop = beamDyTop,
                    dyBottom = beamDyBottom * 0.7f,
                    frontColor = steelFront,
                    sideColor = steelSide,
                    topColor = steelTop,
                    bottomColor = steelBottom
                )

                // Lower 3D Clearance Cross-Bar with Street Sign Box ("BROADWAY")
                draw3DBox(
                    leftX = screenX - obsWidth * 0.88f,
                    rightX = screenX + obsWidth * 0.88f,
                    topY = clearanceY - obsHeight * 0.12f,
                    bottomY = clearanceY,
                    dx = postDx,
                    dyTop = beamDyTop * 0.6f,
                    dyBottom = beamDyBottom,
                    frontColor = steelFront,
                    sideColor = steelSide,
                    topColor = steelTop,
                    bottomColor = steelBottom
                )

                // 3D Extruded Street Name Sign ("BROADWAY")
                val signLeft = screenX - obsWidth * 0.74f
                val signRight = screenX + obsWidth * 0.04f
                val signTop = clearanceY - obsHeight * 0.36f
                val signBot = clearanceY - obsHeight * 0.14f
                draw3DBox(
                    leftX = signLeft,
                    rightX = signRight,
                    topY = signTop,
                    bottomY = signBot,
                    dx = postDx * 0.6f,
                    dyTop = -obsHeight * 0.04f,
                    dyBottom = obsHeight * 0.04f,
                    frontColor = Color(0xFF1B5E20).copy(alpha = alpha),
                    sideColor = Color(0xFF0D3B10).copy(alpha = alpha),
                    topColor = Color(0xFF2E7D32).copy(alpha = alpha),
                    bottomColor = Color(0xFF0D3B10).copy(alpha = alpha),
                    cornerRadius = obsWidth * 0.02f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.92f * alpha),
                    start = Offset(signLeft + obsWidth * 0.10f, (signTop + signBot) * 0.5f),
                    end = Offset(signRight - obsWidth * 0.10f, (signTop + signBot) * 0.5f),
                    strokeWidth = (obsHeight * 0.05f).coerceAtLeast(1.5f)
                )

                // 3D 3-Lens Traffic Signal Housing Box with Hood Visors
                val boxLeft = screenX + obsWidth * 0.14f
                val boxRight = screenX + obsWidth * 0.50f
                val boxTop = topY + obsHeight * 0.06f
                val boxBot = clearanceY - obsHeight * 0.02f
                draw3DBox(
                    leftX = boxLeft,
                    rightX = boxRight,
                    topY = boxTop,
                    bottomY = boxBot,
                    dx = postDx * 0.9f,
                    dyTop = -obsHeight * 0.06f,
                    dyBottom = obsHeight * 0.06f,
                    frontColor = yellowFront,
                    sideColor = yellowSide,
                    topColor = Color(0xFFFFEE58).copy(alpha = alpha),
                    bottomColor = Color(0xFFE65100).copy(alpha = alpha),
                    cornerRadius = obsWidth * 0.04f
                )
                val boxW = boxRight - boxLeft
                val boxH = boxBot - boxTop
                drawRoundRect(
                    color = Color(0xFF1A1A1A).copy(alpha = alpha),
                    topLeft = Offset(boxLeft + boxW * 0.12f, boxTop + boxH * 0.06f),
                    size = Size(boxW * 0.76f, boxH * 0.88f),
                    cornerRadius = CornerRadius(obsWidth * 0.03f)
                )
                val rLens = boxW * 0.23f
                val cx = (boxLeft + boxRight) * 0.5f
                val pulseRed = 0.75f + 0.25f * sin(distance * 10f)
                // 3D lens visors + glowing bulbs
                val lensYs = floatArrayOf(boxTop + boxH * 0.23f, boxTop + boxH * 0.50f, boxTop + boxH * 0.77f)
                val lensColors = arrayOf(
                    Color(0xFFFF1744).copy(alpha = pulseRed * alpha),
                    Color(0xFFFFEA00).copy(alpha = 0.45f * alpha),
                    Color(0xFF00E676).copy(alpha = 0.45f * alpha)
                )
                for (i in 0..2) {
                    drawArc(
                        color = yellowSide,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(cx - rLens * 1.25f, lensYs[i] - rLens * 1.25f),
                        size = Size(rLens * 2.5f, rLens * 2.5f),
                        style = Stroke(width = (obsWidth * 0.035f).coerceAtLeast(1.5f))
                    )
                    drawCircle(lensColors[i], radius = rLens, center = Offset(cx, lensYs[i]))
                }
            }

            "Bridge Sign" -> {
                val trussFront = Color(0xFF607D8B).copy(alpha = alpha)
                val trussSide = Color(0xFF37474F).copy(alpha = alpha)
                val trussTop = Color(0xFF90A4AE).copy(alpha = alpha)

                // Left & Right 3D Steel Gantry Pillars
                draw3DBox(
                    leftX = screenX - obsWidth * 0.92f,
                    rightX = screenX - obsWidth * 0.92f + postW,
                    topY = topY,
                    bottomY = screenY,
                    dx = postDx,
                    dyTop = dyTop,
                    dyBottom = dyGround,
                    frontColor = trussFront,
                    sideColor = trussSide,
                    topColor = trussTop
                )
                draw3DBox(
                    leftX = screenX + obsWidth * 0.79f,
                    rightX = screenX + obsWidth * 0.79f + postW,
                    topY = topY,
                    bottomY = screenY,
                    dx = postDx,
                    dyTop = dyTop,
                    dyBottom = dyGround,
                    frontColor = trussFront,
                    sideColor = trussSide,
                    topColor = trussTop
                )

                // 3D Steel Space-Frame Truss behind the highway sign
                draw3DBox(
                    leftX = screenX - obsWidth * 0.96f,
                    rightX = screenX + obsWidth * 0.96f,
                    topY = topY,
                    bottomY = clearanceY,
                    dx = dx3D * 0.55f,
                    dyTop = beamDyTop,
                    dyBottom = beamDyBottom,
                    frontColor = Color(0xFF004D40).copy(alpha = alpha),
                    sideColor = Color(0xFF00251A).copy(alpha = alpha),
                    topColor = trussTop,
                    bottomColor = Color(0xFF263238).copy(alpha = alpha),
                    cornerRadius = obsWidth * 0.03f
                )
                // Reflective White Highway Sign Border & Text Bars
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.92f * alpha),
                    topLeft = Offset(screenX - obsWidth * 0.88f, topY + obsHeight * 0.06f),
                    size = Size(obsWidth * 1.76f, (clearanceY - topY) - obsHeight * 0.12f),
                    cornerRadius = CornerRadius(obsWidth * 0.025f),
                    style = Stroke(width = (obsWidth * 0.025f).coerceAtLeast(1f))
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.92f * alpha),
                    start = Offset(screenX - obsWidth * 0.62f, topY + (clearanceY - topY) * 0.38f),
                    end = Offset(screenX + obsWidth * 0.62f, topY + (clearanceY - topY) * 0.38f),
                    strokeWidth = (obsHeight * 0.08f).coerceAtLeast(2f)
                )
                drawLine(
                    color = Color(0xFFFFD54F).copy(alpha = 0.92f * alpha),
                    start = Offset(screenX - obsWidth * 0.40f, topY + (clearanceY - topY) * 0.68f),
                    end = Offset(screenX + obsWidth * 0.40f, topY + (clearanceY - topY) * 0.68f),
                    strokeWidth = (obsHeight * 0.06f).coerceAtLeast(1.5f)
                )
            }

            "Scaffolding" -> {
                val pipeFront = Color(0xFF90A4AE).copy(alpha = alpha)
                val pipeSide = Color(0xFF546E7A).copy(alpha = alpha)
                val plankFront = Color(0xFF8D6E63).copy(alpha = alpha)
                val plankSide = Color(0xFF5D4037).copy(alpha = alpha)
                val plankTop = Color(0xFFA1887F).copy(alpha = alpha)
                val plankUnderside = Color(0xFF3E2723).copy(alpha = alpha)

                val leftPostX = screenX - obsWidth * 0.88f
                val rightPostX = screenX + obsWidth * 0.78f
                val pW = obsWidth * 0.09f

                // Back-plane 3D scaffolding posts (offset in 3D depth!)
                drawRect(pipeSide, Offset(leftPostX + postDx, topY + dyTop), Size(pW * 0.85f, (screenY - topY)))
                drawRect(pipeSide, Offset(rightPostX + postDx, topY + dyTop), Size(pW * 0.85f, (screenY - topY)))

                // 3D Side Cross-Bracing connecting front and back scaffolding towers
                val braceStroke = (obsWidth * 0.035f).coerceAtLeast(1.2f)
                drawLine(pipeSide, Offset(leftPostX, topY), Offset(leftPostX + postDx, clearanceY + dyClearance), strokeWidth = braceStroke)
                drawLine(pipeSide, Offset(rightPostX, topY), Offset(rightPostX + postDx, clearanceY + dyClearance), strokeWidth = braceStroke)

                // Front 3D Scaffolding Posts
                draw3DBox(
                    leftX = leftPostX,
                    rightX = leftPostX + pW,
                    topY = topY,
                    bottomY = screenY,
                    dx = postDx * 0.35f,
                    dyTop = dyTop * 0.35f,
                    dyBottom = dyGround * 0.35f,
                    frontColor = pipeFront,
                    sideColor = pipeSide,
                    topColor = pipeFront
                )
                draw3DBox(
                    leftX = rightPostX,
                    rightX = rightPostX + pW,
                    topY = topY,
                    bottomY = screenY,
                    dx = postDx * 0.35f,
                    dyTop = dyTop * 0.35f,
                    dyBottom = dyGround * 0.35f,
                    frontColor = pipeFront,
                    sideColor = pipeSide,
                    topColor = pipeFront
                )

                // Diagonal steel X-bracing between front posts
                drawLine(pipeFront, Offset(leftPostX, topY), Offset(rightPostX + pW, clearanceY), strokeWidth = (obsWidth * 0.042f).coerceAtLeast(1.5f))
                drawLine(pipeFront, Offset(rightPostX + pW, topY), Offset(leftPostX, clearanceY), strokeWidth = (obsWidth * 0.042f).coerceAtLeast(1.5f))

                // Upper & Lower 3D Wooden Scaffolding Decks (with visible 3D underside & side depth)
                draw3DBox(
                    leftX = screenX - obsWidth * 0.95f,
                    rightX = screenX + obsWidth * 0.95f,
                    topY = topY,
                    bottomY = topY + obsHeight * 0.12f,
                    dx = postDx,
                    dyTop = beamDyTop,
                    dyBottom = beamDyBottom * 0.6f,
                    frontColor = plankFront,
                    sideColor = plankSide,
                    topColor = plankTop,
                    bottomColor = plankUnderside
                )
                draw3DBox(
                    leftX = screenX - obsWidth * 0.95f,
                    rightX = screenX + obsWidth * 0.95f,
                    topY = clearanceY - obsHeight * 0.14f,
                    bottomY = clearanceY,
                    dx = postDx,
                    dyTop = beamDyTop * 0.6f,
                    dyBottom = beamDyBottom,
                    frontColor = plankFront,
                    sideColor = plankSide,
                    topColor = plankTop,
                    bottomColor = plankUnderside
                )
                // Yellow Hazard Toe-Board strip
                drawRect(
                    color = Color(0xFFFBC02D).copy(alpha = alpha),
                    topLeft = Offset(screenX - obsWidth * 0.92f, clearanceY - obsHeight * 0.05f),
                    size = Size(obsWidth * 1.84f, obsHeight * 0.05f)
                )
            }

            "Lantern" -> {
                val vermilionFront = Color(0xFFD32F2F).copy(alpha = alpha)
                val vermilionSide = Color(0xFF9A0007).copy(alpha = alpha)
                val darkWoodFront = Color(0xFF2B2523).copy(alpha = alpha)
                val darkWoodSide = Color(0xFF141110).copy(alpha = alpha)

                // Left & Right 3D Vermilion Torii Pillars
                draw3DBox(
                    leftX = screenX - obsWidth * 0.88f,
                    rightX = screenX - obsWidth * 0.88f + postW,
                    topY = topY,
                    bottomY = screenY,
                    dx = postDx,
                    dyTop = dyTop,
                    dyBottom = dyGround,
                    frontColor = vermilionFront,
                    sideColor = vermilionSide,
                    topColor = vermilionFront
                )
                draw3DBox(
                    leftX = screenX + obsWidth * 0.75f,
                    rightX = screenX + obsWidth * 0.75f + postW,
                    topY = topY,
                    bottomY = screenY,
                    dx = postDx,
                    dyTop = dyTop,
                    dyBottom = dyGround,
                    frontColor = vermilionFront,
                    sideColor = vermilionSide,
                    topColor = vermilionFront
                )

                // Upper 3D Kasagi Crossbeam & Lower 3D Nuki Tie-Beam
                draw3DBox(
                    leftX = screenX - obsWidth * 0.98f,
                    rightX = screenX + obsWidth * 0.98f,
                    topY = topY - obsHeight * 0.06f,
                    bottomY = topY + obsHeight * 0.11f,
                    dx = postDx * 1.1f,
                    dyTop = beamDyTop,
                    dyBottom = beamDyBottom * 0.6f,
                    frontColor = vermilionFront,
                    sideColor = vermilionSide,
                    topColor = darkWoodFront,
                    bottomColor = darkWoodSide
                )
                draw3DBox(
                    leftX = screenX - obsWidth * 0.88f,
                    rightX = screenX + obsWidth * 0.88f,
                    topY = topY + obsHeight * 0.20f,
                    bottomY = topY + obsHeight * 0.29f,
                    dx = postDx,
                    dyTop = beamDyTop * 0.5f,
                    dyBottom = beamDyBottom * 0.5f,
                    frontColor = vermilionFront,
                    sideColor = vermilionSide,
                    topColor = vermilionSide,
                    bottomColor = vermilionSide
                )

                // Three 3D Volumetric Chochin Paper Lanterns with 3D Ribs and Top/Bottom Wooden Rings
                for (i in -1..1) {
                    val lx = screenX + i * obsWidth * 0.44f
                    val lyTop = topY + obsHeight * 0.30f
                    val lH = (clearanceY - lyTop).coerceAtLeast(4f)
                    val lW = obsWidth * 0.32f
                    // 3D shaded lantern spheroid
                    drawOval(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF9A0007).copy(alpha = alpha),
                                Color(0xFFFF1744).copy(alpha = alpha),
                                Color(0xFFFFF59D).copy(alpha = alpha),
                                Color(0xFFD50000).copy(alpha = alpha),
                                Color(0xFF7F0000).copy(alpha = alpha)
                            ),
                            startX = lx - lW * 0.5f,
                            endX = lx + lW * 0.5f
                        ),
                        topLeft = Offset(lx - lW * 0.5f, lyTop),
                        size = Size(lW, lH)
                    )
                    // 3D curved horizontal bamboo ribs
                    for (rib in 1..2) {
                        val ry = lyTop + lH * (rib * 0.28f)
                        drawArc(
                            color = Color(0xFF7F0000).copy(alpha = 0.7f * alpha),
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(lx - lW * 0.48f, ry - lH * 0.08f),
                            size = Size(lW * 0.96f, lH * 0.16f),
                            style = Stroke(width = (obsWidth * 0.018f).coerceAtLeast(1f))
                        )
                    }
                    // Top & bottom 3D wooden end-caps
                    drawOval(darkWoodFront, Offset(lx - lW * 0.26f, lyTop - lH * 0.04f), Size(lW * 0.52f, lH * 0.12f))
                    drawOval(darkWoodSide, Offset(lx - lW * 0.26f, lyTop + lH * 0.90f), Size(lW * 0.52f, lH * 0.12f))
                }
            }

            else -> {
                // Ornate 3D Archway (Cairo), Street Lamp Gantry (Paris), or Boomerang Portal (Sydney)
                val isArch = obs.type == "Archway"
                val frameFront = if (isArch) Color(0xFFD7B377).copy(alpha = alpha) else Color(0xFF455A64).copy(alpha = alpha)
                val frameSide = if (isArch) Color(0xFF9C7A44).copy(alpha = alpha) else Color(0xFF263238).copy(alpha = alpha)
                val frameTop = if (isArch) Color(0xFFEAD2A8).copy(alpha = alpha) else Color(0xFF78909C).copy(alpha = alpha)

                // Left & Right 3D Columns
                draw3DBox(
                    leftX = screenX - obsWidth * 0.90f,
                    rightX = screenX - obsWidth * 0.90f + postW * 1.2f,
                    topY = topY,
                    bottomY = screenY,
                    dx = postDx,
                    dyTop = dyTop,
                    dyBottom = dyGround,
                    frontColor = frameFront,
                    sideColor = frameSide,
                    topColor = frameTop
                )
                draw3DBox(
                    leftX = screenX + obsWidth * 0.76f,
                    rightX = screenX + obsWidth * 0.76f + postW * 1.2f,
                    topY = topY,
                    bottomY = screenY,
                    dx = postDx,
                    dyTop = dyTop,
                    dyBottom = dyGround,
                    frontColor = frameFront,
                    sideColor = frameSide,
                    topColor = frameTop
                )

                // Overhead 3D Lintel Box with 3D Underside
                draw3DBox(
                    leftX = screenX - obsWidth * 0.92f,
                    rightX = screenX + obsWidth * 0.92f,
                    topY = topY,
                    bottomY = clearanceY,
                    dx = dx3D * 0.50f,
                    dyTop = beamDyTop,
                    dyBottom = beamDyBottom,
                    frontColor = frameFront,
                    sideColor = frameSide,
                    topColor = frameTop,
                    bottomColor = frameSide,
                    cornerRadius = obsWidth * 0.04f
                )
                // 3D Extruded Center Medallion / Lantern
                val rMed = (clearanceY - topY) * 0.28f
                val cyMed = (topY + clearanceY) * 0.5f
                drawCircle(frameSide, radius = rMed * 1.15f, center = Offset(screenX + postDx * 0.25f, cyMed))
                drawCircle(Color(0xFFFFF59D).copy(alpha = 0.95f * alpha), radius = rMed, center = Offset(screenX, cyMed))
            }
        }
    }

    private fun DrawScope.drawLowObstacle(
        obs: Obstacle,
        screenX: Float,
        screenY: Float,
        obsWidth: Float,
        obsHeight: Float,
        dx3D: Float,
        vanishingY: Float,
        alpha: Float
    ) {
        // Ground contact shadow shifted slightly along 3D depth
        drawOval(
            color = Color.Black.copy(alpha = 0.38f * alpha),
            topLeft = Offset(screenX - obsWidth * 0.85f + dx3D * 0.15f, screenY - obsHeight * 0.15f),
            size = Size(obsWidth * 1.75f, obsHeight * 0.30f)
        )

        val dyTopBase = ((vanishingY - (screenY - obsHeight * 0.6f)) * 0.16f).coerceAtMost(-obsHeight * 0.18f)
        val dyBottomBase = ((vanishingY - screenY) * 0.16f).coerceAtMost(-obsHeight * 0.14f)

        when (obs.type) {
            "Hotdog Stand" -> {
                val cartTopY = screenY - obsHeight * 0.58f
                val cartBotY = screenY - obsHeight * 0.08f
                val leftX = screenX - obsWidth * 0.66f
                val rightX = screenX + obsWidth * 0.66f

                // Far-side 3D wheel (receding in depth)
                val wheelR = obsWidth * 0.16f
                draw3DWheel(
                    centerX = (if (dx3D >= 0f) rightX else leftX) + dx3D * 0.7f,
                    centerY = screenY - wheelR * 0.85f + dyBottomBase * 0.6f,
                    radiusX = wheelR * 0.85f,
                    radiusY = wheelR * 0.85f,
                    treadDx = dx3D * 0.18f,
                    alpha = alpha
                )

                // Lower 3D Red Vendor Cart Box
                draw3DBox(
                    leftX = leftX,
                    rightX = rightX,
                    topY = cartTopY + obsHeight * 0.12f,
                    bottomY = cartBotY,
                    dx = dx3D,
                    dyTop = dyTopBase,
                    dyBottom = dyBottomBase,
                    frontColor = Color(0xFFD32F2F).copy(alpha = alpha),
                    sideColor = Color(0xFF8E0000).copy(alpha = alpha),
                    topColor = Color(0xFFB71C1C).copy(alpha = alpha),
                    cornerRadius = obsWidth * 0.04f
                )

                // Upper 3D Brushed Stainless-Steel Countertop Box with Top Service Surface
                draw3DBox(
                    leftX = leftX - obsWidth * 0.03f,
                    rightX = rightX + obsWidth * 0.03f,
                    topY = cartTopY,
                    bottomY = cartTopY + obsHeight * 0.14f,
                    dx = dx3D * 1.04f,
                    dyTop = dyTopBase,
                    dyBottom = dyTopBase,
                    frontColor = Color(0xFFECEFF1).copy(alpha = alpha),
                    sideColor = Color(0xFF90A4AE).copy(alpha = alpha),
                    topColor = Color(0xFFFFFFFF).copy(alpha = alpha)
                )

                // 3D Glass Sneeze Guard Box on top of the stainless counter
                draw3DBox(
                    leftX = leftX + obsWidth * 0.12f,
                    rightX = rightX - obsWidth * 0.12f,
                    topY = cartTopY - obsHeight * 0.16f,
                    bottomY = cartTopY,
                    dx = dx3D * 0.55f,
                    dyTop = dyTopBase * 0.55f,
                    dyBottom = dyTopBase * 0.55f,
                    frontColor = Color(0xFFB3E5FC).copy(alpha = 0.55f * alpha),
                    sideColor = Color(0xFF4FC3F7).copy(alpha = 0.45f * alpha),
                    topColor = Color(0xFFE1F5FE).copy(alpha = 0.65f * alpha)
                )

                // Yellow "NYC DOGS" Menu Board on Front Face
                drawRect(
                    color = Color(0xFFFFEB3B).copy(alpha = alpha),
                    topLeft = Offset(screenX - obsWidth * 0.48f, cartTopY + obsHeight * 0.20f),
                    size = Size(obsWidth * 0.96f, obsHeight * 0.22f)
                )

                // Front-Left & Front-Right 3D Spoked Pushcart Wheels
                draw3DWheel(leftX + obsWidth * 0.18f, screenY - wheelR * 0.65f, wheelR, wheelR, dx3D * 0.18f, alpha)
                draw3DWheel(rightX - obsWidth * 0.18f, screenY - wheelR * 0.65f, wheelR, wheelR, dx3D * 0.18f, alpha)

                // 3D Multi-Faceted Vendor Umbrella with 3D Depth Panels
                val poleTopY = screenY - obsHeight * 1.34f
                val umbCenterX = screenX + dx3D * 0.35f
                drawLine(
                    color = Color(0xFF90A4AE).copy(alpha = alpha),
                    start = Offset(umbCenterX, cartTopY + dyTopBase * 0.4f),
                    end = Offset(umbCenterX, poleTopY),
                    strokeWidth = (obsWidth * 0.055f).coerceAtLeast(1.5f)
                )
                val umbBotY = screenY - obsHeight * 0.94f
                val apexY = poleTopY - obsHeight * 0.10f
                // Underside shadow of umbrella canopy
                drawOval(
                    color = Color(0xFF3E2723).copy(alpha = 0.85f * alpha),
                    topLeft = Offset(umbCenterX - obsWidth * 0.86f, umbBotY - obsHeight * 0.08f),
                    size = Size(obsWidth * 1.72f, obsHeight * 0.18f)
                )
                // Left 3D Canopy Facet (Sunlit Yellow)
                sharedPath.reset()
                sharedPath.moveTo(umbCenterX, apexY)
                sharedPath.lineTo(umbCenterX - obsWidth * 0.88f, umbBotY)
                sharedPath.lineTo(umbCenterX - obsWidth * 0.28f, umbBotY + obsHeight * 0.04f)
                sharedPath.close()
                drawPath(sharedPath, Color(0xFFFFD54F).copy(alpha = alpha))

                // Right 3D Canopy Facet (Shaded Amber-Gold)
                sharedPath.reset()
                sharedPath.moveTo(umbCenterX, apexY)
                sharedPath.lineTo(umbCenterX + obsWidth * 0.28f, umbBotY + obsHeight * 0.04f)
                sharedPath.lineTo(umbCenterX + obsWidth * 0.88f, umbBotY)
                sharedPath.close()
                drawPath(sharedPath, Color(0xFFFFA000).copy(alpha = alpha))

                // Center 3D Canopy Facet (Bright Red)
                sharedPath.reset()
                sharedPath.moveTo(umbCenterX, apexY)
                sharedPath.lineTo(umbCenterX - obsWidth * 0.30f, umbBotY + obsHeight * 0.04f)
                sharedPath.lineTo(umbCenterX + obsWidth * 0.30f, umbBotY + obsHeight * 0.04f)
                sharedPath.close()
                drawPath(sharedPath, Color(0xFFD32F2F).copy(alpha = alpha))
            }

            "Cafe Table" -> {
                val ironColor = Color(0xFF263238).copy(alpha = alpha)
                val ironHighlight = Color(0xFF546E7A).copy(alpha = alpha)
                val tableY = screenY - obsHeight * 0.56f
                val tableRx = obsWidth * 0.68f
                val tableRy = obsHeight * 0.16f

                // 3D Tripod Wrought-Iron Base (3 legs extending in 3D space)
                val columnBotY = screenY - obsHeight * 0.10f
                drawLine(ironColor, Offset(screenX, columnBotY), Offset(screenX - obsWidth * 0.42f, screenY), strokeWidth = (obsWidth * 0.065f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
                drawLine(ironColor, Offset(screenX, columnBotY), Offset(screenX + obsWidth * 0.42f, screenY), strokeWidth = (obsWidth * 0.065f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
                drawLine(ironHighlight, Offset(screenX, columnBotY), Offset(screenX + dx3D * 0.6f, screenY + dyBottomBase * 0.5f), strokeWidth = (obsWidth * 0.055f).coerceAtLeast(1.5f), cap = StrokeCap.Round)

                // 3D Cylindrical Center Pedestal Column
                draw3DCylinder(
                    centerX = screenX,
                    topY = tableY,
                    bottomY = columnBotY,
                    radiusX = obsWidth * 0.07f,
                    ellipseRy = obsHeight * 0.025f,
                    baseColor = ironColor,
                    highlightColor = ironHighlight,
                    shadowColor = Color(0xFF11171A).copy(alpha = alpha),
                    topCapColor = ironColor
                )

                // 3D Thick Disc Tabletop (Brass Cylinder Rim + Parisian Marble Top Ellipse)
                draw3DCylinder(
                    centerX = screenX,
                    topY = tableY - obsHeight * 0.07f,
                    bottomY = tableY,
                    radiusX = tableRx,
                    ellipseRy = tableRy,
                    baseColor = Color(0xFFC59B27).copy(alpha = alpha),
                    highlightColor = Color(0xFFFFE082).copy(alpha = alpha),
                    shadowColor = Color(0xFF8C6D1F).copy(alpha = alpha),
                    topCapColor = Color(0xFFFDFBF7).copy(alpha = alpha)
                )

                // 3D Espresso Cup Cylinder & Saucer on tabletop
                val cupX = screenX - obsWidth * 0.20f
                val cupY = tableY - obsHeight * 0.08f
                drawOval(Color(0xFFCFD8DC).copy(alpha = alpha), Offset(cupX - obsWidth * 0.16f, cupY - obsHeight * 0.03f), Size(obsWidth * 0.32f, obsHeight * 0.09f))
                draw3DCylinder(
                    centerX = cupX,
                    topY = cupY - obsHeight * 0.14f,
                    bottomY = cupY,
                    radiusX = obsWidth * 0.09f,
                    ellipseRy = obsHeight * 0.03f,
                    baseColor = Color(0xFFECEFF1).copy(alpha = alpha),
                    highlightColor = Color.White.copy(alpha = alpha),
                    shadowColor = Color(0xFF90A4AE).copy(alpha = alpha),
                    topCapColor = Color(0xFF3E2723).copy(alpha = alpha)
                )
            }

            "Traffic Cone" -> {
                // 3D Square Weighted Rubber Base Slab (with Front Face, 3D Side Face, and 3D Top Face)
                val baseLeft = screenX - obsWidth * 0.50f
                val baseRight = screenX + obsWidth * 0.50f
                val baseTopY = screenY - obsHeight * 0.11f
                draw3DBox(
                    leftX = baseLeft,
                    rightX = baseRight,
                    topY = baseTopY,
                    bottomY = screenY,
                    dx = dx3D * 0.65f,
                    dyTop = dyBottomBase * 0.75f,
                    dyBottom = dyBottomBase * 0.75f,
                    frontColor = Color(0xFF262626).copy(alpha = alpha),
                    sideColor = Color(0xFF121212).copy(alpha = alpha),
                    topColor = Color(0xFF3D3D3D).copy(alpha = alpha)
                )

                // 3D Conical Frustum shifted onto the center of the 3D base top surface
                val coneCenterX = screenX + dx3D * 0.32f
                val coneBaseY = baseTopY + dyBottomBase * 0.38f
                val coneTopY = screenY - obsHeight * 0.90f
                val botRx = obsWidth * 0.36f
                val botRy = obsHeight * 0.09f
                val topRx = obsWidth * 0.08f
                val topRy = obsHeight * 0.03f

                // Elliptical bottom base of cone on rubber pad
                drawOval(
                    color = Color(0xFFBF360C).copy(alpha = alpha),
                    topLeft = Offset(coneCenterX - botRx, coneBaseY - botRy),
                    size = Size(botRx * 2f, botRy * 2f)
                )

                // 3D Tapered Cone Body with Left-Highlight to Right-Shadow 3D Cylindrical Gradient
                sharedPath.reset()
                sharedPath.moveTo(coneCenterX - topRx, coneTopY)
                sharedPath.lineTo(coneCenterX + topRx, coneTopY)
                sharedPath.lineTo(coneCenterX + botRx, coneBaseY)
                sharedPath.lineTo(coneCenterX - botRx, coneBaseY)
                sharedPath.close()
                drawPath(
                    path = sharedPath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFE64A19).copy(alpha = alpha),
                            Color(0xFFFF7043).copy(alpha = alpha),
                            Color(0xFFFF5722).copy(alpha = alpha),
                            Color(0xFFBF360C).copy(alpha = alpha)
                        ),
                        startX = coneCenterX - botRx,
                        endX = coneCenterX + botRx
                    )
                )

                // Curved 3D Reflective White Collars wrapping around the cone's circular cross-section
                for (band in 0..1) {
                    val t0 = 0.24f + band * 0.30f
                    val t1 = t0 + 0.15f
                    val y0 = coneTopY + (coneBaseY - coneTopY) * t0
                    val y1 = coneTopY + (coneBaseY - coneTopY) * t1
                    val w0 = topRx + (botRx - topRx) * t0
                    val w1 = topRx + (botRx - topRx) * t1
                    val curveSag0 = botRy * (0.3f + 0.7f * t0)
                    val curveSag1 = botRy * (0.3f + 0.7f * t1)

                    sharedPath.reset()
                    sharedPath.moveTo(coneCenterX - w0, y0)
                    sharedPath.quadraticBezierTo(coneCenterX, y0 + curveSag0, coneCenterX + w0, y0)
                    sharedPath.lineTo(coneCenterX + w1, y1)
                    sharedPath.quadraticBezierTo(coneCenterX, y1 + curveSag1, coneCenterX - w1, y1)
                    sharedPath.close()
                    drawPath(
                        path = sharedPath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFCFD8DC).copy(alpha = alpha),
                                Color.White.copy(alpha = alpha),
                                Color(0xFFB0BEC5).copy(alpha = alpha)
                            ),
                            startX = coneCenterX - w1,
                            endX = coneCenterX + w1
                        )
                    )
                }

                // 3D Elliptical Hollow Top Rim of the Cone
                drawOval(
                    color = Color(0xFFFF8A65).copy(alpha = alpha),
                    topLeft = Offset(coneCenterX - topRx, coneTopY - topRy),
                    size = Size(topRx * 2f, topRy * 2f)
                )
                drawOval(
                    color = Color(0xFF3E2723).copy(alpha = alpha),
                    topLeft = Offset(coneCenterX - topRx * 0.6f, coneTopY - topRy * 0.6f),
                    size = Size(topRx * 1.2f, topRy * 1.2f)
                )
            }

            "Mailbox" -> {
                // Iconic 3D Cylindrical Royal Mail Pillar Box
                val radiusX = obsWidth * 0.38f
                val ellipseRy = obsHeight * 0.09f
                val topY = screenY - obsHeight * 0.86f
                val baseTopY = screenY - obsHeight * 0.16f

                // 1. 3D Black Cast-Iron Plinth Base Cylinder
                draw3DCylinder(
                    centerX = screenX,
                    topY = baseTopY,
                    bottomY = screenY,
                    radiusX = radiusX * 1.08f,
                    ellipseRy = ellipseRy * 1.08f,
                    baseColor = Color(0xFF212121).copy(alpha = alpha),
                    highlightColor = Color(0xFF484848).copy(alpha = alpha),
                    shadowColor = Color(0xFF0D0D0D).copy(alpha = alpha),
                    topCapColor = Color(0xFF2C2C2C).copy(alpha = alpha)
                )

                // 2. 3D Red Cylindrical Body
                draw3DCylinder(
                    centerX = screenX,
                    topY = topY,
                    bottomY = baseTopY,
                    radiusX = radiusX,
                    ellipseRy = ellipseRy,
                    baseColor = Color(0xFFC62828).copy(alpha = alpha),
                    highlightColor = Color(0xFFEF5350).copy(alpha = alpha),
                    shadowColor = Color(0xFF7F0000).copy(alpha = alpha),
                    topCapColor = Color(0xFFD32F2F).copy(alpha = alpha)
                )

                // 3. 3D Overhanging Fluted Cap Cylinder & Dome
                draw3DCylinder(
                    centerX = screenX,
                    topY = topY - obsHeight * 0.08f,
                    bottomY = topY,
                    radiusX = radiusX * 1.10f,
                    ellipseRy = ellipseRy * 1.10f,
                    baseColor = Color(0xFFB71C1C).copy(alpha = alpha),
                    highlightColor = Color(0xFFE53935).copy(alpha = alpha),
                    shadowColor = Color(0xFF680000).copy(alpha = alpha),
                    topCapColor = Color(0xFFEF5350).copy(alpha = alpha)
                )

                // Recessed 3D Mail Slot & Royal Cypher Plate
                val slotOffset = if (dx3D >= 0f) radiusX * 0.15f else -radiusX * 0.15f
                drawRoundRect(
                    color = Color(0xFF141414).copy(alpha = alpha),
                    topLeft = Offset(screenX + slotOffset - radiusX * 0.50f, topY + obsHeight * 0.14f),
                    size = Size(radiusX * 1.0f, obsHeight * 0.09f),
                    cornerRadius = CornerRadius(obsWidth * 0.02f)
                )
                drawRect(
                    color = Color(0xFFF5F5F5).copy(alpha = alpha),
                    topLeft = Offset(screenX + slotOffset - radiusX * 0.40f, topY + obsHeight * 0.28f),
                    size = Size(radiusX * 0.80f, obsHeight * 0.18f)
                )
                drawCircle(
                    color = Color(0xFFFFD54F).copy(alpha = alpha),
                    radius = radiusX * 0.18f,
                    center = Offset(screenX + slotOffset, topY + obsHeight * 0.56f)
                )
            }

            "Bonsai" -> {
                // 3D Rectangular Ceramic Bonsai Planter Box with Top Soil Bed
                val potTopY = screenY - obsHeight * 0.34f
                val potLeft = screenX - obsWidth * 0.54f
                val potRight = screenX + obsWidth * 0.54f
                draw3DBox(
                    leftX = potLeft,
                    rightX = potRight,
                    topY = potTopY,
                    bottomY = screenY,
                    dx = dx3D * 0.70f,
                    dyTop = dyBottomBase * 0.75f,
                    dyBottom = dyBottomBase * 0.75f,
                    frontColor = Color(0xFF1565C0).copy(alpha = alpha),
                    sideColor = Color(0xFF0D47A1).copy(alpha = alpha),
                    topColor = Color(0xFF4E342E).copy(alpha = alpha), // Dark soil bed inside planter
                    cornerRadius = obsWidth * 0.03f
                )
                // 3D Overhanging Ceramic Rim Lip
                draw3DBox(
                    leftX = potLeft - obsWidth * 0.05f,
                    rightX = potRight + obsWidth * 0.05f,
                    topY = potTopY - obsHeight * 0.06f,
                    bottomY = potTopY + obsHeight * 0.02f,
                    dx = dx3D * 0.74f,
                    dyTop = dyBottomBase * 0.78f,
                    dyBottom = dyBottomBase * 0.78f,
                    frontColor = Color(0xFF1976D2).copy(alpha = alpha),
                    sideColor = Color(0xFF0D47A1).copy(alpha = alpha),
                    topColor = Color(0xFF42A5F5).copy(alpha = alpha)
                )

                // Sculpted 3D Twisted Bonsai Trunk (Shadow pass + Highlight pass)
                val trunkBaseX = screenX + dx3D * 0.32f
                val trunkBaseY = potTopY + dyBottomBase * 0.35f
                sharedPath.reset()
                sharedPath.moveTo(trunkBaseX, trunkBaseY)
                sharedPath.quadraticBezierTo(
                    trunkBaseX - obsWidth * 0.30f,
                    trunkBaseY - obsHeight * 0.26f,
                    trunkBaseX + obsWidth * 0.12f,
                    trunkBaseY - obsHeight * 0.50f
                )
                drawPath(sharedPath, Color(0xFF3E2723).copy(alpha = alpha), style = Stroke(width = (obsWidth * 0.16f).coerceAtLeast(2.5f), cap = StrokeCap.Round))
                drawPath(sharedPath, Color(0xFF6D4C41).copy(alpha = alpha), style = Stroke(width = (obsWidth * 0.09f).coerceAtLeast(1.5f), cap = StrokeCap.Round))

                // 3D Layered Pine Foliage Cloud Pads (Underside shadow, mid-body, sunlit top crown)
                val pads = arrayOf(
                    Triple(-0.28f, -0.30f, 0.56f),
                    Triple(0.26f, -0.42f, 0.60f),
                    Triple(-0.02f, -0.62f, 0.68f)
                )
                for ((px, py, pw) in pads) {
                    val cx = trunkBaseX + obsWidth * px
                    val cy = trunkBaseY + obsHeight * py
                    val w = obsWidth * pw
                    val h = obsHeight * 0.24f
                    drawOval(Color(0xFF0D3B10).copy(alpha = alpha), Offset(cx - w * 0.5f, cy - h * 0.3f), Size(w, h))
                    drawOval(Color(0xFF2E7D32).copy(alpha = alpha), Offset(cx - w * 0.48f, cy - h * 0.5f), Size(w * 0.96f, h * 0.85f))
                    drawOval(Color(0xFF66BB6A).copy(alpha = alpha), Offset(cx - w * 0.38f, cy - h * 0.62f), Size(w * 0.72f, h * 0.55f))
                }
            }

            "Urn" -> {
                // 1. 3D Limestone Pedestal Plinth under the Amphora
                val plinthTopY = screenY - obsHeight * 0.22f
                draw3DBox(
                    leftX = screenX - obsWidth * 0.44f,
                    rightX = screenX + obsWidth * 0.44f,
                    topY = plinthTopY,
                    bottomY = screenY,
                    dx = dx3D * 0.60f,
                    dyTop = dyBottomBase * 0.65f,
                    dyBottom = dyBottomBase * 0.65f,
                    frontColor = Color(0xFFD7CCC8).copy(alpha = alpha),
                    sideColor = Color(0xFF8D6E63).copy(alpha = alpha),
                    topColor = Color(0xFFEFEBE9).copy(alpha = alpha)
                )

                val urnCenterX = screenX + dx3D * 0.28f
                val urnBaseY = plinthTopY + dyBottomBase * 0.30f

                // 2. Curved 3D Terracotta Handles
                drawOval(
                    color = Color(0xFF6D361B).copy(alpha = alpha),
                    topLeft = Offset(urnCenterX - obsWidth * 0.56f, urnBaseY - obsHeight * 0.68f),
                    size = Size(obsWidth * 1.12f, obsHeight * 0.38f),
                    style = Stroke(width = (obsWidth * 0.075f).coerceAtLeast(1.5f))
                )

                // 3. 3D Lathed Amphora Body with Specular-to-Core-Shadow Gradient
                val bodyW = obsWidth * 0.86f
                val bodyH = obsHeight * 0.62f
                val bodyTopY = urnBaseY - bodyH
                drawOval(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF8D4524).copy(alpha = alpha),
                            Color(0xFFE68A5C).copy(alpha = alpha),
                            Color(0xFFC86D44).copy(alpha = alpha),
                            Color(0xFF5D2912).copy(alpha = alpha)
                        ),
                        startX = urnCenterX - bodyW * 0.5f,
                        endX = urnCenterX + bodyW * 0.5f
                    ),
                    topLeft = Offset(urnCenterX - bodyW * 0.5f, bodyTopY),
                    size = Size(bodyW, bodyH)
                )

                // 4. Curved 3D Gold Hieroglyphic Band wrapping around belly
                drawArc(
                    color = Color(0xFFFFD54F).copy(alpha = alpha),
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(urnCenterX - bodyW * 0.48f, bodyTopY + bodyH * 0.32f),
                    size = Size(bodyW * 0.96f, bodyH * 0.28f),
                    style = Stroke(width = (obsHeight * 0.08f).coerceAtLeast(2f))
                )

                // 5. 3D Cylindrical Neck & Hollow Elliptical Mouth Rim
                val neckTopY = bodyTopY - obsHeight * 0.16f
                draw3DCylinder(
                    centerX = urnCenterX,
                    topY = neckTopY,
                    bottomY = bodyTopY + obsHeight * 0.08f,
                    radiusX = obsWidth * 0.20f,
                    ellipseRy = obsHeight * 0.055f,
                    baseColor = Color(0xFFC86D44).copy(alpha = alpha),
                    highlightColor = Color(0xFFFFD54F).copy(alpha = alpha),
                    shadowColor = Color(0xFF6D361B).copy(alpha = alpha),
                    topCapColor = Color(0xFFFFE082).copy(alpha = alpha)
                )
                // Dark hollow interior of amphora mouth
                drawOval(
                    color = Color(0xFF2B1206).copy(alpha = alpha),
                    topLeft = Offset(urnCenterX - obsWidth * 0.14f, neckTopY - obsHeight * 0.035f),
                    size = Size(obsWidth * 0.28f, obsHeight * 0.07f)
                )
            }

            "Surfboard" -> {
                // 1. 3D Timber Beach Display Rack Base
                val rackTopY = screenY - obsHeight * 0.24f
                draw3DBox(
                    leftX = screenX - obsWidth * 0.58f,
                    rightX = screenX + obsWidth * 0.58f,
                    topY = rackTopY,
                    bottomY = screenY,
                    dx = dx3D * 0.70f,
                    dyTop = dyBottomBase * 0.70f,
                    dyBottom = dyBottomBase * 0.70f,
                    frontColor = Color(0xFF6D4C41).copy(alpha = alpha),
                    sideColor = Color(0xFF3E2723).copy(alpha = alpha),
                    topColor = Color(0xFF8D6E63).copy(alpha = alpha),
                    cornerRadius = obsWidth * 0.03f
                )

                val railThickness = obsHeight * 0.075f
                // 2. 3D Surfboard Lower Rail Thickness Volume (Shaded Fiberglass Side Edge)
                sharedPath.reset()
                sharedPath.moveTo(screenX - obsWidth * 0.78f, screenY - obsHeight * 0.16f + railThickness)
                sharedPath.quadraticBezierTo(
                    screenX,
                    screenY - obsHeight * 0.90f + railThickness,
                    screenX + obsWidth * 0.80f,
                    screenY - obsHeight * 0.74f + railThickness
                )
                sharedPath.quadraticBezierTo(
                    screenX + obsWidth * 0.15f,
                    screenY - obsHeight * 0.05f + railThickness,
                    screenX - obsWidth * 0.78f,
                    screenY - obsHeight * 0.16f + railThickness
                )
                sharedPath.close()
                drawPath(sharedPath, Color(0xFF0077C2).copy(alpha = alpha))

                // 3. 3D Surfboard Top Deck Surface
                sharedPath.reset()
                sharedPath.moveTo(screenX - obsWidth * 0.78f, screenY - obsHeight * 0.16f)
                sharedPath.quadraticBezierTo(
                    screenX,
                    screenY - obsHeight * 0.90f,
                    screenX + obsWidth * 0.80f,
                    screenY - obsHeight * 0.74f
                )
                sharedPath.quadraticBezierTo(
                    screenX + obsWidth * 0.15f,
                    screenY - obsHeight * 0.05f,
                    screenX - obsWidth * 0.78f,
                    screenY - obsHeight * 0.16f
                )
                sharedPath.close()
                drawPath(sharedPath, Color(0xFFFFFDE7).copy(alpha = alpha))

                // Center Stringer & Racing Stripe on Top Deck
                drawLine(
                    color = Color(0xFF00B0FF).copy(alpha = alpha),
                    start = Offset(screenX - obsWidth * 0.58f, screenY - obsHeight * 0.22f),
                    end = Offset(screenX + obsWidth * 0.64f, screenY - obsHeight * 0.68f),
                    strokeWidth = (obsHeight * 0.09f).coerceAtLeast(2f),
                    cap = StrokeCap.Round
                )
                // 3D Tail Thruster Fin protruding upward
                sharedPath.reset()
                sharedPath.moveTo(screenX - obsWidth * 0.52f, screenY - obsHeight * 0.28f)
                sharedPath.lineTo(screenX - obsWidth * 0.62f, screenY - obsHeight * 0.50f)
                sharedPath.lineTo(screenX - obsWidth * 0.36f, screenY - obsHeight * 0.34f)
                sharedPath.close()
                drawPath(sharedPath, Color(0xFFFF6D00).copy(alpha = alpha))
            }

            else -> {
                // True 3D Construction Sawhorse Barricade with Front & Back A-Frame Legs
                val legFrontColor = Color(0xFF455A64).copy(alpha = alpha)
                val legBackColor = Color(0xFF263238).copy(alpha = alpha)
                val topY = screenY - obsHeight * 0.62f
                val leftX = screenX - obsWidth * 0.74f
                val rightX = screenX + obsWidth * 0.74f
                val legStroke = (obsWidth * 0.075f).coerceAtLeast(2f)

                // Back A-frame legs (spread in 3D depth)
                drawLine(legBackColor, Offset(leftX + obsWidth * 0.12f + dx3D * 0.4f, topY + dyTopBase * 0.5f), Offset(leftX + dx3D * 0.75f, screenY + dyBottomBase * 0.8f), strokeWidth = legStroke, cap = StrokeCap.Round)
                drawLine(legBackColor, Offset(rightX - obsWidth * 0.12f + dx3D * 0.4f, topY + dyTopBase * 0.5f), Offset(rightX + dx3D * 0.75f, screenY + dyBottomBase * 0.8f), strokeWidth = legStroke, cap = StrokeCap.Round)

                // Front A-frame legs
                drawLine(legFrontColor, Offset(leftX + obsWidth * 0.12f, topY), Offset(leftX, screenY), strokeWidth = legStroke, cap = StrokeCap.Round)
                drawLine(legFrontColor, Offset(rightX - obsWidth * 0.12f, topY), Offset(rightX, screenY), strokeWidth = legStroke, cap = StrokeCap.Round)

                // Top 3D Reflective Chevron Rail Box
                val barH = obsHeight * 0.24f
                draw3DBox(
                    leftX = leftX,
                    rightX = rightX,
                    topY = topY,
                    bottomY = topY + barH,
                    dx = dx3D * 0.65f,
                    dyTop = dyTopBase * 0.65f,
                    dyBottom = dyTopBase * 0.65f,
                    frontColor = Color.White.copy(alpha = alpha),
                    sideColor = Color(0xFFB0BEC5).copy(alpha = alpha),
                    topColor = Color(0xFFECEFF1).copy(alpha = alpha)
                )
                val stripes = 5
                val sw = (rightX - leftX) / (stripes * 2f)
                for (i in 0 until stripes) {
                    drawRect(
                        color = Color(0xFFFF6D00).copy(alpha = alpha),
                        topLeft = Offset(leftX + i * 2 * sw, topY),
                        size = Size(sw, barH)
                    )
                }
            }
        }
    }

    private fun DrawScope.drawSolidObstacle(
        obs: Obstacle,
        screenX: Float,
        screenY: Float,
        obsWidth: Float,
        obsHeight: Float,
        dx3D: Float,
        vanishingY: Float,
        scale: Float,
        alpha: Float
    ) {
        val dyBottom = ((vanishingY - screenY) * 0.22f).coerceAtMost(-obsHeight * 0.18f)

        when (obs.type) {
            "Taxi" -> {
                // True 3D NYC Yellow Cab with Front, 3D Side Door/Window Wall, 3D Hood, 3D Roof & 3D Wheels
                val cabYellow = Color(0xFFFFC107).copy(alpha = alpha)
                val cabSide = Color(0xFFFF8F00).copy(alpha = alpha)
                val cabTop = Color(0xFFFFE082).copy(alpha = alpha)

                val leftX = screenX - obsWidth * 0.68f
                val rightX = screenX + obsWidth * 0.68f
                val hoodTopY = screenY - obsHeight * 0.48f
                val roofTopY = screenY - obsHeight * 0.90f
                val dyHood = ((vanishingY - hoodTopY) * 0.22f).coerceAtMost(-obsHeight * 0.20f)
                val dyRoof = ((vanishingY - roofTopY) * 0.20f).coerceAtMost(-obsHeight * 0.22f)

                // Ground shadow covering front and 3D receding chassis
                drawOval(
                    color = Color.Black.copy(alpha = 0.44f * alpha),
                    topLeft = Offset(leftX - obsWidth * 0.12f + dx3D * 0.2f, screenY - obsHeight * 0.16f),
                    size = Size(obsWidth * 1.68f + abs(dx3D) * 0.5f, obsHeight * 0.32f)
                )

                // Rear 3D Wheel on the visible receding side wall
                val wheelR = obsWidth * 0.16f
                val sideEdgeX = if (dx3D >= 0f) rightX else leftX
                draw3DWheel(
                    centerX = sideEdgeX + dx3D * 0.72f,
                    centerY = screenY - wheelR * 0.75f + dyBottom * 0.72f,
                    radiusX = wheelR * 0.75f,
                    radiusY = wheelR * 0.85f,
                    treadDx = dx3D * 0.18f,
                    alpha = alpha
                )

                // Front-Left & Front-Right 3D Wheels
                draw3DWheel(leftX + obsWidth * 0.16f, screenY - wheelR * 0.75f, wheelR * 0.82f, wheelR * 0.92f, dx3D * 0.20f, alpha)
                draw3DWheel(rightX - obsWidth * 0.16f, screenY - wheelR * 0.75f, wheelR * 0.82f, wheelR * 0.92f, dx3D * 0.20f, alpha)

                // 1. Lower 3D Car Body / Engine Hood / Side Doors
                draw3DBox(
                    leftX = leftX,
                    rightX = rightX,
                    topY = hoodTopY,
                    bottomY = screenY - obsHeight * 0.08f,
                    dx = dx3D,
                    dyTop = dyHood,
                    dyBottom = dyBottom,
                    frontColor = cabYellow,
                    sideColor = cabSide,
                    topColor = cabTop,
                    cornerRadius = obsWidth * 0.06f
                )

                // NYC Checkered / Black Side Stripe along the receding 3D Side Door Panel
                val stripeYFront = (hoodTopY + screenY) * 0.50f
                val stripeYBack = stripeYFront + dyBottom * 0.85f
                drawLine(
                    color = Color(0xFF212121).copy(alpha = 0.85f * alpha),
                    start = Offset(sideEdgeX, stripeYFront),
                    end = Offset(sideEdgeX + dx3D * 0.92f, stripeYBack),
                    strokeWidth = (obsHeight * 0.07f).coerceAtLeast(2f)
                )

                // 2. Upper 3D Passenger Cabin (Set slightly back on the hood)
                val cabLeftX = screenX - obsWidth * 0.54f
                val cabRightX = screenX + obsWidth * 0.54f
                val cabDx = dx3D * 0.88f
                draw3DBox(
                    leftX = cabLeftX,
                    rightX = cabRightX,
                    topY = roofTopY,
                    bottomY = hoodTopY + obsHeight * 0.04f,
                    dx = cabDx,
                    dyTop = dyRoof,
                    dyBottom = dyHood * 0.9f,
                    frontColor = cabYellow,
                    sideColor = cabSide,
                    topColor = cabTop,
                    cornerRadius = obsWidth * 0.07f
                )

                // 3D Side Passenger Windows on the receding side of the cabin
                val cabSideX = if (cabDx >= 0f) cabRightX else cabLeftX
                sharedPath.reset()
                sharedPath.moveTo(cabSideX + cabDx * 0.12f, roofTopY + obsHeight * 0.07f + dyRoof * 0.12f)
                sharedPath.lineTo(cabSideX + cabDx * 0.86f, roofTopY + obsHeight * 0.07f + dyRoof * 0.86f)
                sharedPath.lineTo(cabSideX + cabDx * 0.86f, hoodTopY - obsHeight * 0.02f + dyHood * 0.86f)
                sharedPath.lineTo(cabSideX + cabDx * 0.12f, hoodTopY - obsHeight * 0.02f + dyHood * 0.12f)
                sharedPath.close()
                drawPath(sharedPath, Color(0xFF1C262B).copy(alpha = alpha))

                // Front Tinted Windshield with Specular Reflection
                val wsLeft = cabLeftX + obsWidth * 0.08f
                val wsW = (cabRightX - cabLeftX) - obsWidth * 0.16f
                val wsH = (hoodTopY - roofTopY) * 0.78f
                drawRoundRect(
                    color = Color(0xFF263238).copy(alpha = alpha),
                    topLeft = Offset(wsLeft, roofTopY + obsHeight * 0.06f),
                    size = Size(wsW, wsH),
                    cornerRadius = CornerRadius(obsWidth * 0.04f)
                )
                sharedPath.reset()
                sharedPath.moveTo(wsLeft + wsW * 0.12f, roofTopY + obsHeight * 0.08f)
                sharedPath.lineTo(wsLeft + wsW * 0.40f, roofTopY + obsHeight * 0.08f)
                sharedPath.lineTo(wsLeft + wsW * 0.20f, roofTopY + obsHeight * 0.08f + wsH * 0.85f)
                sharedPath.lineTo(wsLeft + wsW * 0.04f, roofTopY + obsHeight * 0.08f + wsH * 0.85f)
                sharedPath.close()
                drawPath(sharedPath, Color.White.copy(alpha = 0.35f * alpha))

                // 3. 3D NYC Taxi Roof Medallion Sign Box sitting on the 3D roof
                val signCenterX = screenX + cabDx * 0.35f
                val signBaseY = roofTopY + dyRoof * 0.35f
                val signW = obsWidth * 0.38f
                val signH = obsHeight * 0.13f
                draw3DBox(
                    leftX = signCenterX - signW * 0.5f,
                    rightX = signCenterX + signW * 0.5f,
                    topY = signBaseY - signH,
                    bottomY = signBaseY,
                    dx = cabDx * 0.32f,
                    dyTop = dyRoof * 0.32f,
                    dyBottom = dyRoof * 0.32f,
                    frontColor = Color(0xFFFFF9C4).copy(alpha = alpha),
                    sideColor = Color(0xFFFBC02D).copy(alpha = alpha),
                    topColor = Color.White.copy(alpha = alpha),
                    cornerRadius = obsWidth * 0.02f
                )

                // 4. Protruding 3D Front Bumper, Radiator Grille & Glowing Headlights
                val grilleY = hoodTopY + obsHeight * 0.11f
                drawRoundRect(
                    color = Color(0xFF1A1A1A).copy(alpha = alpha),
                    topLeft = Offset(screenX - obsWidth * 0.28f, grilleY),
                    size = Size(obsWidth * 0.56f, obsHeight * 0.16f),
                    cornerRadius = CornerRadius(obsWidth * 0.03f)
                )
                drawRoundRect(Color(0xFFFFFDE7).copy(alpha = alpha), Offset(leftX + obsWidth * 0.07f, grilleY), Size(obsWidth * 0.24f, obsHeight * 0.12f), CornerRadius(obsWidth * 0.03f))
                drawRoundRect(Color(0xFFFFFDE7).copy(alpha = alpha), Offset(rightX - obsWidth * 0.31f, grilleY), Size(obsWidth * 0.24f, obsHeight * 0.12f), CornerRadius(obsWidth * 0.03f))

                // 3D Extruded Front Bumper Bar
                draw3DBox(
                    leftX = leftX - obsWidth * 0.03f,
                    rightX = rightX + obsWidth * 0.03f,
                    topY = screenY - obsHeight * 0.19f,
                    bottomY = screenY - obsHeight * 0.08f,
                    dx = dx3D * 0.25f,
                    dyTop = dyBottom * 0.25f,
                    dyBottom = dyBottom * 0.25f,
                    frontColor = Color(0xFF2B2B2B).copy(alpha = alpha),
                    sideColor = Color(0xFF141414).copy(alpha = alpha),
                    topColor = Color(0xFF484848).copy(alpha = alpha),
                    cornerRadius = obsWidth * 0.02f
                )
                drawRect(Color(0xFFFFD54F).copy(alpha = alpha), Offset(screenX - obsWidth * 0.14f, screenY - obsHeight * 0.17f), Size(obsWidth * 0.28f, obsHeight * 0.08f))
            }

            "Bus" -> {
                // True 3D London Double-Decker Bus with Side Deck Windows, 3D Roof & Protruding Engine Bonnet
                val busRed = Color(0xFFC62828).copy(alpha = alpha)
                val busSide = Color(0xFF7F0000).copy(alpha = alpha)
                val busTop = Color(0xFFE53935).copy(alpha = alpha)
                val glass = Color(0xFF1C262B).copy(alpha = alpha)

                val leftX = screenX - obsWidth * 0.72f
                val rightX = screenX + obsWidth * 0.72f
                val topY = screenY - obsHeight * 1.48f
                val dyTop = ((vanishingY - topY) * 0.22f).coerceAtMost(-obsHeight * 0.22f)

                drawOval(
                    color = Color.Black.copy(alpha = 0.45f * alpha),
                    topLeft = Offset(leftX - obsWidth * 0.12f + dx3D * 0.2f, screenY - obsHeight * 0.16f),
                    size = Size(obsWidth * 1.72f + abs(dx3D) * 0.55f, obsHeight * 0.32f)
                )

                // Rear 3D Wheel on visible receding side + Front 3D Wheels
                val wheelR = obsWidth * 0.18f
                val sideEdgeX = if (dx3D >= 0f) rightX else leftX
                draw3DWheel(sideEdgeX + dx3D * 0.75f, screenY - wheelR * 0.7f + dyBottom * 0.75f, wheelR * 0.78f, wheelR * 0.88f, dx3D * 0.18f, alpha)
                draw3DWheel(leftX + obsWidth * 0.16f, screenY - wheelR * 0.7f, wheelR * 0.85f, wheelR * 0.95f, dx3D * 0.20f, alpha)
                draw3DWheel(rightX - obsWidth * 0.16f, screenY - wheelR * 0.7f, wheelR * 0.85f, wheelR * 0.95f, dx3D * 0.20f, alpha)

                // Main Double-Decker 3D Body
                draw3DBox(
                    leftX = leftX,
                    rightX = rightX,
                    topY = topY,
                    bottomY = screenY - obsHeight * 0.08f,
                    dx = dx3D * 1.15f,
                    dyTop = dyTop,
                    dyBottom = dyBottom,
                    frontColor = busRed,
                    sideColor = busSide,
                    topColor = busTop,
                    cornerRadius = obsWidth * 0.08f
                )

                // Upper & Lower Deck 3D Side Passenger Windows along the receding side wall
                val busDx = dx3D * 1.15f
                for (deck in 0..1) {
                    val winTopFront = topY + obsHeight * (0.10f + deck * 0.56f)
                    val winBotFront = winTopFront + obsHeight * 0.34f
                    val dYTopDeck = dyTop + (dyBottom - dyTop) * (0.10f + deck * 0.56f)
                    val dYBotDeck = dyTop + (dyBottom - dyTop) * (0.34f + deck * 0.56f)

                    sharedPath.reset()
                    sharedPath.moveTo(sideEdgeX + busDx * 0.12f, winTopFront + dYTopDeck * 0.12f)
                    sharedPath.lineTo(sideEdgeX + busDx * 0.88f, winTopFront + dYTopDeck * 0.88f)
                    sharedPath.lineTo(sideEdgeX + busDx * 0.88f, winBotFront + dYBotDeck * 0.88f)
                    sharedPath.lineTo(sideEdgeX + busDx * 0.12f, winBotFront + dYBotDeck * 0.12f)
                    sharedPath.close()
                    drawPath(sharedPath, glass)
                }

                // Upper Deck Front Panoramic Window
                drawRoundRect(glass, Offset(leftX + obsWidth * 0.10f, topY + obsHeight * 0.08f), Size(obsWidth * 1.24f, obsHeight * 0.36f), CornerRadius(obsWidth * 0.05f))
                // Destination Blind ("15 PICCADILLY")
                drawRect(Color(0xFF111111).copy(alpha = alpha), Offset(screenX - obsWidth * 0.42f, topY + obsHeight * 0.48f), Size(obsWidth * 0.84f, obsHeight * 0.12f))
                drawLine(Color(0xFFFFD54F).copy(alpha = alpha), Offset(screenX - obsWidth * 0.32f, topY + obsHeight * 0.54f), Offset(screenX + obsWidth * 0.32f, topY + obsHeight * 0.54f), strokeWidth = (obsHeight * 0.04f).coerceAtLeast(1.5f))

                // Lower Deck Driver Cab & Windshield
                drawRoundRect(glass, Offset(leftX + obsWidth * 0.10f, topY + obsHeight * 0.64f), Size(obsWidth * 1.24f, obsHeight * 0.34f), CornerRadius(obsWidth * 0.04f))

                // Protruding 3D Half-Cab Engine Bonnet & Silver Radiator Grille
                draw3DBox(
                    leftX = screenX - obsWidth * 0.28f,
                    rightX = rightX - obsWidth * 0.06f,
                    topY = screenY - obsHeight * 0.42f,
                    bottomY = screenY - obsHeight * 0.08f,
                    dx = dx3D * 0.28f,
                    dyTop = dyBottom * 0.28f,
                    dyBottom = dyBottom * 0.28f,
                    frontColor = Color(0xFFD32F2F).copy(alpha = alpha),
                    sideColor = busSide,
                    topColor = busTop,
                    cornerRadius = obsWidth * 0.03f
                )
                drawRoundRect(Color(0xFFCFD8DC).copy(alpha = alpha), Offset(screenX - obsWidth * 0.22f, screenY - obsHeight * 0.38f), Size(obsWidth * 0.44f, obsHeight * 0.24f), CornerRadius(obsWidth * 0.03f))
                drawCircle(Color(0xFFFFFDE7).copy(alpha = alpha), radius = obsWidth * 0.10f, center = Offset(leftX + obsWidth * 0.22f, screenY - obsHeight * 0.24f))
                drawCircle(Color(0xFFFFFDE7).copy(alpha = alpha), radius = obsWidth * 0.10f, center = Offset(rightX - obsWidth * 0.22f, screenY - obsHeight * 0.24f))
            }

            "Camel", "Kangaroo" -> {
                val isCamel = obs.type == "Camel"
                val furFront = if (isCamel) Color(0xFFC69C6D).copy(alpha = alpha) else Color(0xFFB8653B).copy(alpha = alpha)
                val furSide = if (isCamel) Color(0xFF8D633A).copy(alpha = alpha) else Color(0xFF7D3C1D).copy(alpha = alpha)
                val furTop = if (isCamel) Color(0xFFE2C199).copy(alpha = alpha) else Color(0xFFE6B89C).copy(alpha = alpha)

                drawOval(
                    color = Color.Black.copy(alpha = 0.38f * alpha),
                    topLeft = Offset(screenX - obsWidth * 0.78f + dx3D * 0.15f, screenY - obsHeight * 0.14f),
                    size = Size(obsWidth * 1.56f, obsHeight * 0.28f)
                )

                if (isCamel) {
                    // Far-side 3D legs (offset in depth) + Near-side 3D legs
                    val legXs = floatArrayOf(-0.36f, -0.14f, 0.16f, 0.38f)
                    for (i in legXs.indices) {
                        val isFarLeg = (i % 2 == 0)
                        val lx = screenX + obsWidth * legXs[i] + (if (isFarLeg) dx3D * 0.35f else 0f)
                        val lyBot = screenY - obsHeight * 0.03f + (if (isFarLeg) dyBottom * 0.35f else 0f)
                        val lW = obsWidth * 0.11f
                        draw3DBox(
                            leftX = lx - lW * 0.5f,
                            rightX = lx + lW * 0.5f,
                            topY = screenY - obsHeight * 0.50f,
                            bottomY = lyBot,
                            dx = dx3D * 0.22f,
                            dyTop = dyBottom * 0.22f,
                            dyBottom = dyBottom * 0.22f,
                            frontColor = if (isFarLeg) furSide else furFront,
                            sideColor = furSide,
                            topColor = furTop,
                            cornerRadius = lW * 0.3f,
                            drawEdges = false
                        )
                    }

                    // 3D Sculpted Camel Torso Box + 3D Saddle & Hump Box
                    val torsoTopY = screenY - obsHeight * 0.80f
                    draw3DBox(
                        leftX = screenX - obsWidth * 0.54f,
                        rightX = screenX + obsWidth * 0.54f,
                        topY = torsoTopY,
                        bottomY = screenY - obsHeight * 0.42f,
                        dx = dx3D * 0.75f,
                        dyTop = dyBottom * 0.8f,
                        dyBottom = dyBottom * 0.75f,
                        frontColor = furFront,
                        sideColor = furSide,
                        topColor = furTop,
                        cornerRadius = obsWidth * 0.18f
                    )
                    // 3D Crimson & Gold Egyptian Saddle + Hump
                    draw3DBox(
                        leftX = screenX - obsWidth * 0.26f,
                        rightX = screenX + obsWidth * 0.26f,
                        topY = screenY - obsHeight * 1.00f,
                        bottomY = torsoTopY + obsHeight * 0.08f,
                        dx = dx3D * 0.65f,
                        dyTop = dyBottom * 0.85f,
                        dyBottom = dyBottom * 0.80f,
                        frontColor = Color(0xFFB71C1C).copy(alpha = alpha),
                        sideColor = Color(0xFF7F0000).copy(alpha = alpha),
                        topColor = Color(0xFFFFD54F).copy(alpha = alpha),
                        cornerRadius = obsWidth * 0.10f
                    )
                    // 3D Upright Neck Column & 3D Snout/Head Box
                    val neckLeft = screenX - obsWidth * 0.68f
                    val neckRight = screenX - obsWidth * 0.44f
                    val headTopY = screenY - obsHeight * 1.24f
                    draw3DBox(
                        leftX = neckLeft,
                        rightX = neckRight,
                        topY = headTopY + obsHeight * 0.14f,
                        bottomY = torsoTopY + obsHeight * 0.14f,
                        dx = dx3D * 0.45f,
                        dyTop = dyBottom * 0.5f,
                        dyBottom = dyBottom * 0.5f,
                        frontColor = furFront,
                        sideColor = furSide,
                        topColor = furTop,
                        cornerRadius = obsWidth * 0.06f
                    )
                    draw3DBox(
                        leftX = screenX - obsWidth * 0.86f,
                        rightX = neckRight + obsWidth * 0.04f,
                        topY = headTopY,
                        bottomY = headTopY + obsHeight * 0.20f,
                        dx = dx3D * 0.45f,
                        dyTop = dyBottom * 0.5f,
                        dyBottom = dyBottom * 0.5f,
                        frontColor = furFront,
                        sideColor = furSide,
                        topColor = furTop,
                        cornerRadius = obsWidth * 0.06f
                    )
                } else {
                    // 3D Sculpted Kangaroo: 3D Tail Prism, Hind Haunches, Torso Box, and 3D Head/Ears
                    // Far-side & Near-side 3D Hind Feet
                    draw3DBox(
                        leftX = screenX - obsWidth * 0.42f + dx3D * 0.35f,
                        rightX = screenX + obsWidth * 0.10f + dx3D * 0.35f,
                        topY = screenY - obsHeight * 0.10f + dyBottom * 0.35f,
                        bottomY = screenY + dyBottom * 0.35f,
                        dx = dx3D * 0.3f,
                        dyTop = dyBottom * 0.3f,
                        dyBottom = dyBottom * 0.3f,
                        frontColor = furSide,
                        sideColor = furSide,
                        topColor = furFront,
                        cornerRadius = obsWidth * 0.03f
                    )
                    draw3DBox(
                        leftX = screenX - obsWidth * 0.44f,
                        rightX = screenX + obsWidth * 0.12f,
                        topY = screenY - obsHeight * 0.10f,
                        bottomY = screenY,
                        dx = dx3D * 0.3f,
                        dyTop = dyBottom * 0.3f,
                        dyBottom = dyBottom * 0.3f,
                        frontColor = furFront,
                        sideColor = furSide,
                        topColor = furTop,
                        cornerRadius = obsWidth * 0.03f
                    )
                    // 3D Muscular Haunches & Upright Torso
                    draw3DBox(
                        leftX = screenX - obsWidth * 0.30f,
                        rightX = screenX + obsWidth * 0.28f,
                        topY = screenY - obsHeight * 0.84f,
                        bottomY = screenY - obsHeight * 0.08f,
                        dx = dx3D * 0.65f,
                        dyTop = dyBottom * 0.75f,
                        dyBottom = dyBottom * 0.65f,
                        frontColor = furFront,
                        sideColor = furSide,
                        topColor = furTop,
                        cornerRadius = obsWidth * 0.14f
                    )
                    // Cream chest patch
                    drawRoundRect(
                        color = furTop,
                        topLeft = Offset(screenX - obsWidth * 0.24f, screenY - obsHeight * 0.76f),
                        size = Size(obsWidth * 0.34f, obsHeight * 0.44f),
                        cornerRadius = CornerRadius(obsWidth * 0.08f)
                    )
                    // 3D Head Box & Tall Ears
                    val headTopY = screenY - obsHeight * 1.06f
                    draw3DBox(
                        leftX = screenX - obsWidth * 0.36f,
                        rightX = screenX + obsWidth * 0.08f,
                        topY = headTopY,
                        bottomY = screenY - obsHeight * 0.82f,
                        dx = dx3D * 0.50f,
                        dyTop = dyBottom * 0.55f,
                        dyBottom = dyBottom * 0.55f,
                        frontColor = furFront,
                        sideColor = furSide,
                        topColor = furTop,
                        cornerRadius = obsWidth * 0.06f
                    )
                    drawOval(furSide, Offset(screenX - obsWidth * 0.18f, headTopY - obsHeight * 0.22f), Size(obsWidth * 0.11f, obsHeight * 0.24f))
                    drawOval(furFront, Offset(screenX - obsWidth * 0.04f, headTopY - obsHeight * 0.20f), Size(obsWidth * 0.11f, obsHeight * 0.22f))
                }
            }

            "Roadblock" -> {
                // True 3D Concrete Jersey Barrier with Flared Base + Upper Wall + 3D Hazard Beacons
                val concreteFront = Color(0xFF9E9B95).copy(alpha = alpha)
                val concreteSide = Color(0xFF6E6A63).copy(alpha = alpha)
                val concreteTop = Color(0xFFC4C0B8).copy(alpha = alpha)
                val leftX = screenX - obsWidth * 0.88f
                val rightX = screenX + obsWidth * 0.88f
                val midY = screenY - obsHeight * 0.32f
                val topY = screenY - obsHeight * 0.80f
                val dyTop = ((vanishingY - topY) * 0.18f).coerceAtMost(-obsHeight * 0.18f)

                drawOval(
                    color = Color.Black.copy(alpha = 0.40f * alpha),
                    topLeft = Offset(leftX - obsWidth * 0.10f + dx3D * 0.15f, screenY - obsHeight * 0.14f),
                    size = Size(obsWidth * 1.96f + abs(dx3D) * 0.4f, obsHeight * 0.28f)
                )

                // 1. Lower Wider 3D Concrete Jersey Footing
                draw3DBox(
                    leftX = leftX - obsWidth * 0.05f,
                    rightX = rightX + obsWidth * 0.05f,
                    topY = midY,
                    bottomY = screenY,
                    dx = dx3D * 0.90f,
                    dyTop = dyBottom * 0.90f,
                    dyBottom = dyBottom * 0.90f,
                    frontColor = concreteFront,
                    sideColor = concreteSide,
                    topColor = concreteTop,
                    cornerRadius = obsWidth * 0.03f
                )

                // 2. Upper Tapered 3D Concrete Barrier Wall
                draw3DBox(
                    leftX = leftX,
                    rightX = rightX,
                    topY = topY,
                    bottomY = midY + obsHeight * 0.04f,
                    dx = dx3D * 0.72f,
                    dyTop = dyTop,
                    dyBottom = dyBottom * 0.75f,
                    frontColor = concreteFront,
                    sideColor = concreteSide,
                    topColor = concreteTop,
                    cornerRadius = obsWidth * 0.03f
                )

                // 3. Reflective Red/White Hazard Chevron Board across front face
                val boardY = topY + obsHeight * 0.08f
                val boardH = obsHeight * 0.26f
                drawRect(Color.White.copy(alpha = alpha), Offset(leftX + obsWidth * 0.06f, boardY), Size(obsWidth * 1.64f, boardH))
                val stripeCount = 5
                val sw = (obsWidth * 1.64f) / (stripeCount * 2f)
                for (i in 0 until stripeCount) {
                    drawRect(
                        color = Color(0xFFD32F2F).copy(alpha = alpha),
                        topLeft = Offset(leftX + obsWidth * 0.06f + i * 2 * sw, boardY),
                        size = Size(sw, boardH)
                    )
                }

                // 4. Dual 3D Cylindrical Amber Hazard Beacons mounted on the 3D top ridge
                val beaconTopY = topY + dyTop * 0.4f - obsHeight * 0.14f
                val beaconBotY = topY + dyTop * 0.4f
                for (bx in listOf(leftX + obsWidth * 0.22f + dx3D * 0.3f, rightX - obsWidth * 0.22f + dx3D * 0.3f)) {
                    draw3DCylinder(
                        centerX = bx,
                        topY = beaconTopY,
                        bottomY = beaconBotY,
                        radiusX = obsWidth * 0.085f,
                        ellipseRy = obsHeight * 0.03f,
                        baseColor = Color(0xFFFFAB00).copy(alpha = alpha),
                        highlightColor = Color(0xFFFFE57F).copy(alpha = alpha),
                        shadowColor = Color(0xFFE65100).copy(alpha = alpha),
                        topCapColor = Color(0xFFFFF59D).copy(alpha = alpha)
                    )
                }
            }

            "Trash Can" -> {
                // True 3D Cylindrical Municipal Litter Bin with Domed 3D Hood & Recessed Chute
                val radiusX = obsWidth * 0.40f
                val ellipseRy = obsHeight * 0.10f
                val topY = screenY - obsHeight * 0.88f

                drawOval(
                    color = Color.Black.copy(alpha = 0.38f * alpha),
                    topLeft = Offset(screenX - radiusX * 1.25f, screenY - ellipseRy * 1.2f),
                    size = Size(radiusX * 2.5f, ellipseRy * 2.4f)
                )

                // 1. Main 3D Fluted Cylinder Body
                draw3DCylinder(
                    centerX = screenX,
                    topY = topY + obsHeight * 0.16f,
                    bottomY = screenY,
                    radiusX = radiusX,
                    ellipseRy = ellipseRy,
                    baseColor = Color(0xFF37474F).copy(alpha = alpha),
                    highlightColor = Color(0xFF78909C).copy(alpha = alpha),
                    shadowColor = Color(0xFF1C262B).copy(alpha = alpha),
                    topCapColor = Color(0xFF546E7A).copy(alpha = alpha)
                )

                // 2. Upper 3D Domed Hood Cylinder
                draw3DCylinder(
                    centerX = screenX,
                    topY = topY,
                    bottomY = topY + obsHeight * 0.18f,
                    radiusX = radiusX * 1.04f,
                    ellipseRy = ellipseRy * 1.05f,
                    baseColor = Color(0xFF263238).copy(alpha = alpha),
                    highlightColor = Color(0xFF546E7A).copy(alpha = alpha),
                    shadowColor = Color(0xFF11171A).copy(alpha = alpha),
                    topCapColor = Color(0xFF607D8B).copy(alpha = alpha)
                )

                // 3. Recessed 3D Dark Waste Chute Cavity + Gold Trim Band
                val chuteShift = if (dx3D >= 0f) radiusX * 0.12f else -radiusX * 0.12f
                drawRoundRect(
                    color = Color(0xFF0D1114).copy(alpha = alpha),
                    topLeft = Offset(screenX + chuteShift - radiusX * 0.55f, topY + obsHeight * 0.06f),
                    size = Size(radiusX * 1.10f, obsHeight * 0.18f),
                    cornerRadius = CornerRadius(obsWidth * 0.04f)
                )
                // Vertical 3D ribs around cylinder
                for (r in -1..1) {
                    val rx = screenX + r * radiusX * 0.45f
                    drawLine(
                        color = Color(0xFF90A4AE).copy(alpha = 0.55f * alpha),
                        start = Offset(rx, topY + obsHeight * 0.34f),
                        end = Offset(rx, screenY - obsHeight * 0.08f),
                        strokeWidth = (2.2.dp.toPx() * scale).coerceAtLeast(1f)
                    )
                }
            }

            "Bench" -> {
                // True 3D Boulevard Park Bench with 3D Seat Box, 3D Backrest Box, and Front/Back Cast-Iron Legs
                val woodFront = Color(0xFF8D6E63).copy(alpha = alpha)
                val woodSide = Color(0xFF4E342E).copy(alpha = alpha)
                val woodTop = Color(0xFFA1887F).copy(alpha = alpha)
                val castIron = Color(0xFF212121).copy(alpha = alpha)

                val bw = obsWidth * 1.62f
                val leftX = screenX - bw * 0.5f
                val rightX = screenX + bw * 0.5f
                val seatY = screenY - obsHeight * 0.38f
                val backTopY = screenY - obsHeight * 0.80f
                val benchDx = dx3D * 0.55f
                val benchDy = dyBottom * 0.65f

                drawOval(
                    color = Color.Black.copy(alpha = 0.36f * alpha),
                    topLeft = Offset(leftX - obsWidth * 0.08f + benchDx * 0.2f, screenY - obsHeight * 0.13f),
                    size = Size(bw + obsWidth * 0.25f, obsHeight * 0.26f)
                )

                val legX1 = leftX + bw * 0.10f
                val legX2 = rightX - bw * 0.10f
                val strokeW = (obsWidth * 0.08f).coerceAtLeast(2f)

                // Back-plane cast-iron legs (offset in 3D depth)
                drawLine(castIron, Offset(legX1 + benchDx, backTopY + benchDy), Offset(legX1 + benchDx, screenY + benchDy), strokeWidth = strokeW)
                drawLine(castIron, Offset(legX2 + benchDx, backTopY + benchDy), Offset(legX2 + benchDx, screenY + benchDy), strokeWidth = strokeW)

                // 1. 3D Wooden Backrest Box (offset to the back of the seat)
                draw3DBox(
                    leftX = leftX + benchDx * 0.65f,
                    rightX = rightX + benchDx * 0.65f,
                    topY = backTopY + benchDy * 0.65f,
                    bottomY = seatY + benchDy * 0.65f,
                    dx = benchDx * 0.35f,
                    dyTop = benchDy * 0.35f,
                    dyBottom = benchDy * 0.35f,
                    frontColor = woodFront,
                    sideColor = woodSide,
                    topColor = woodTop,
                    cornerRadius = obsWidth * 0.02f
                )
                // Horizontal slat groove lines on backrest
                for (s in 1..2) {
                    val sy = backTopY + benchDy * 0.65f + s * (obsHeight * 0.13f)
                    drawLine(woodSide, Offset(leftX + benchDx * 0.65f, sy), Offset(rightX + benchDx * 0.65f, sy), strokeWidth = (obsWidth * 0.02f).coerceAtLeast(1f))
                }

                // 2. 3D Horizontal Wooden Seat Box (extending forward from backrest to front legs)
                draw3DBox(
                    leftX = leftX,
                    rightX = rightX,
                    topY = seatY,
                    bottomY = seatY + obsHeight * 0.11f,
                    dx = benchDx * 0.75f,
                    dyTop = benchDy * 0.75f,
                    dyBottom = benchDy * 0.75f,
                    frontColor = woodFront,
                    sideColor = woodSide,
                    topColor = woodTop,
                    cornerRadius = obsWidth * 0.02f
                )

                // 3. Front Cast-Iron Legs & 3D Armrest Loops
                drawLine(castIron, Offset(legX1, seatY), Offset(legX1, screenY), strokeWidth = strokeW)
                drawLine(castIron, Offset(legX2, seatY), Offset(legX2, screenY), strokeWidth = strokeW)
                drawLine(castIron, Offset(legX1, seatY - obsHeight * 0.14f), Offset(legX1 + benchDx * 0.75f, seatY - obsHeight * 0.14f + benchDy * 0.75f), strokeWidth = strokeW * 0.8f)
                drawLine(castIron, Offset(legX2, seatY - obsHeight * 0.14f), Offset(legX2 + benchDx * 0.75f, seatY - obsHeight * 0.14f + benchDy * 0.75f), strokeWidth = strokeW * 0.8f)
            }

            else -> {
                // True 3D Phone Box (London), 3D Vending Machine (Tokyo), or 3D Parisian Kiosk (Paris)
                val isPhoneBox = obs.type == "Phone Box"
                val isVending = obs.type == "Vending Machine"

                val frontColor = when {
                    isPhoneBox -> Color(0xFFC62828).copy(alpha = alpha)
                    isVending -> Color(0xFFECEFF1).copy(alpha = alpha)
                    else -> Color(0xFF2E5A44).copy(alpha = alpha)
                }
                val sideColor = when {
                    isPhoneBox -> Color(0xFF7F0000).copy(alpha = alpha)
                    isVending -> Color(0xFF1565C0).copy(alpha = alpha)
                    else -> Color(0xFF1B3B2B).copy(alpha = alpha)
                }
                val topColor = when {
                    isPhoneBox -> Color(0xFFE53935).copy(alpha = alpha)
                    isVending -> Color(0xFFFFFFFF).copy(alpha = alpha)
                    else -> Color(0xFF455A64).copy(alpha = alpha)
                }

                val leftX = screenX - obsWidth * 0.54f
                val rightX = screenX + obsWidth * 0.54f
                val topY = screenY - obsHeight * 1.28f
                val dyTop = ((vanishingY - topY) * 0.20f).coerceAtMost(-obsHeight * 0.22f)
                val boxDx = dx3D * 0.95f

                drawOval(
                    color = Color.Black.copy(alpha = 0.40f * alpha),
                    topLeft = Offset(leftX - obsWidth * 0.18f + boxDx * 0.2f, screenY - obsHeight * 0.14f),
                    size = Size(obsWidth * 1.48f + abs(boxDx) * 0.4f, obsHeight * 0.28f)
                )

                // Main 3D Cabinet / Booth Box
                draw3DBox(
                    leftX = leftX,
                    rightX = rightX,
                    topY = topY,
                    bottomY = screenY,
                    dx = boxDx,
                    dyTop = dyTop,
                    dyBottom = dyBottom,
                    frontColor = frontColor,
                    sideColor = sideColor,
                    topColor = topColor,
                    cornerRadius = obsWidth * 0.04f
                )

                val sideEdgeX = if (boxDx >= 0f) rightX else leftX

                if (isPhoneBox) {
                    // White "TELEPHONE" crown banner on Front + Side
                    drawRect(Color.White.copy(alpha = alpha), Offset(leftX + obsWidth * 0.10f, topY + obsHeight * 0.08f), Size(obsWidth * 0.88f, obsHeight * 0.10f))
                    val glassLeft = leftX + obsWidth * 0.14f
                    val glassTop = topY + obsHeight * 0.24f
                    val glassW = obsWidth * 0.80f
                    val glassH = obsHeight * 0.82f
                    drawRect(Color(0xFFB3E5FC).copy(alpha = 0.80f * alpha), Offset(glassLeft, glassTop), Size(glassW, glassH))
                    // 3D Side Window Panel on receding wall of the phone box
                    sharedPath.reset()
                    sharedPath.moveTo(sideEdgeX + boxDx * 0.18f, glassTop + dyTop * 0.18f)
                    sharedPath.lineTo(sideEdgeX + boxDx * 0.82f, glassTop + dyTop * 0.82f)
                    sharedPath.lineTo(sideEdgeX + boxDx * 0.82f, glassTop + glassH + dyBottom * 0.82f)
                    sharedPath.lineTo(sideEdgeX + boxDx * 0.18f, glassTop + glassH + dyBottom * 0.18f)
                    sharedPath.close()
                    drawPath(sharedPath, Color(0xFF4FC3F7).copy(alpha = 0.55f * alpha))

                    for (r in 1..3) {
                        val ry = glassTop + r * (glassH / 4f)
                        drawLine(frontColor, Offset(glassLeft, ry), Offset(glassLeft + glassW, ry), strokeWidth = (obsWidth * 0.03f).coerceAtLeast(1f))
                    }
                    drawLine(frontColor, Offset(screenX, glassTop), Offset(screenX, glassTop + glassH), strokeWidth = (obsWidth * 0.03f).coerceAtLeast(1f))
                } else if (isVending) {
                    // Illuminated beverage display case
                    val dispLeft = leftX + obsWidth * 0.08f
                    val dispTop = topY + obsHeight * 0.08f
                    val dispW = obsWidth * 0.68f
                    val dispH = obsHeight * 0.68f
                    drawRoundRect(Color(0xFF263238).copy(alpha = alpha), Offset(dispLeft, dispTop), Size(dispW, dispH), CornerRadius(obsWidth * 0.03f))
                    drawRoundRect(Color(0xFFE0F7FA).copy(alpha = 0.90f * alpha), Offset(dispLeft + obsWidth * 0.03f, dispTop + obsHeight * 0.03f), Size(dispW - obsWidth * 0.06f, dispH - obsHeight * 0.06f), CornerRadius(obsWidth * 0.02f))

                    val canColors = arrayOf(Color(0xFFE53935), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFFFFB300))
                    for (shelf in 0..2) {
                        val sy = dispTop + obsHeight * (0.14f + shelf * 0.21f)
                        for (c in 0..3) {
                            val cx = dispLeft + obsWidth * (0.08f + c * 0.14f)
                            drawRect(canColors[(shelf + c) % canColors.size].copy(alpha = alpha), Offset(cx, sy), Size(obsWidth * 0.09f, obsHeight * 0.13f))
                        }
                    }
                    drawRect(Color(0xFF37474F).copy(alpha = alpha), Offset(rightX - obsWidth * 0.26f, dispTop), Size(obsWidth * 0.18f, dispH))
                    drawRoundRect(Color(0xFF1C262B).copy(alpha = alpha), Offset(leftX + obsWidth * 0.12f, screenY - obsHeight * 0.34f), Size(obsWidth * 0.84f, obsHeight * 0.22f), CornerRadius(obsWidth * 0.03f))
                } else {
                    // Parisian Kiosk 3D Domed Cupola & Side Poster Display
                    val domeCenterX = screenX + boxDx * 0.45f
                    val domeBaseY = topY + dyTop * 0.45f
                    draw3DCylinder(
                        centerX = domeCenterX,
                        topY = domeBaseY - obsHeight * 0.16f,
                        bottomY = domeBaseY,
                        radiusX = obsWidth * 0.62f,
                        ellipseRy = obsHeight * 0.10f,
                        baseColor = Color(0xFF37474F).copy(alpha = alpha),
                        highlightColor = Color(0xFF78909C).copy(alpha = alpha),
                        shadowColor = Color(0xFF1C262B).copy(alpha = alpha),
                        topCapColor = Color(0xFF546E7A).copy(alpha = alpha)
                    )
                    // Front & 3D Side Theater Posters
                    drawRect(Color(0xFFFFF8E1).copy(alpha = alpha), Offset(leftX + obsWidth * 0.16f, topY + obsHeight * 0.22f), Size(obsWidth * 0.76f, obsHeight * 0.76f))
                    drawRect(Color(0xFFD32F2F).copy(alpha = alpha), Offset(leftX + obsWidth * 0.22f, topY + obsHeight * 0.30f), Size(obsWidth * 0.64f, obsHeight * 0.24f))

                    sharedPath.reset()
                    sharedPath.moveTo(sideEdgeX + boxDx * 0.20f, topY + obsHeight * 0.24f + dyTop * 0.20f)
                    sharedPath.lineTo(sideEdgeX + boxDx * 0.80f, topY + obsHeight * 0.24f + dyTop * 0.80f)
                    sharedPath.lineTo(sideEdgeX + boxDx * 0.80f, topY + obsHeight * 0.96f + dyBottom * 0.80f)
                    sharedPath.lineTo(sideEdgeX + boxDx * 0.20f, topY + obsHeight * 0.96f + dyBottom * 0.20f)
                    sharedPath.close()
                    drawPath(sharedPath, Color(0xFFFFE082).copy(alpha = 0.85f * alpha))
                }
            }
        }
    }
}
