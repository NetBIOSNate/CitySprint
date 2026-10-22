package com.example

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

object StreetAndBuildingRenderer {

    private val sharedPath = Path()

    fun DrawScope.drawStreetAndBuildings(
        city: City,
        distance: Float,
        width: Float,
        height: Float,
        vanishingX: Float,
        vanishingY: Float,
        roadStartXAtBottom: Float,
        laneWidthAtBottom: Float,
        turnDirection: Float = 0f
    ) {
        val roadWidthAtBottom = laneWidthAtBottom * 3f
        val roadEndXAtBottom = roadStartXAtBottom + roadWidthAtBottom
        // 1. Base Urban Ground Plane (Neutral/warm stone & urban earth, zero blue tinge)
        val groundFill = when (city) {
            City.NEW_YORK -> Color(0xFF7A756E)
            City.PARIS -> Color(0xFF9C9183)
            City.TOKYO -> Color(0xFF5C5854)
            City.LONDON -> Color(0xFF706B65)
            City.CAIRO -> Color(0xFFC9B496)
            City.SYDNEY -> Color(0xFF8F867A)
        }
        drawRect(
            color = groundFill,
            topLeft = Offset(0f, vanishingY),
            size = Size(width, height - vanishingY)
        )

        // 2. Realistic Asphalt / Cobblestone Roadway Surface (True neutral/warm asphalt, no blue tint)
        val roadColor = when (city) {
            City.NEW_YORK -> Color(0xFF2B2A29) // Neutral dark asphalt
            City.PARIS -> Color(0xFF3B3632)    // Warm cobblestone charcoal
            City.TOKYO -> Color(0xFF242322)    // Deep neutral obsidian asphalt
            City.LONDON -> Color(0xFF2E2D2B)   // Classic neutral tarmac
            City.CAIRO -> Color(0xFF4F4339)    // Sun-baked desert road
            City.SYDNEY -> Color(0xFF33312F)   // Coastal neutral gray asphalt
        }

        val sidewalkWidthAtBottom = roadWidthAtBottom * 0.34f
        val curbWidthAtBottom = roadWidthAtBottom * 0.045f

        val sidewalkBase = when (city) {
            City.NEW_YORK -> Color(0xFF948E86) // Warm concrete slabs
            City.PARIS -> Color(0xFFB5A999)    // Parisian limestone flags
            City.TOKYO -> Color(0xFF78736E)    // Neutral granite tiles
            City.LONDON -> Color(0xFF857F78)   // Warm Yorkstone paving
            City.CAIRO -> Color(0xFFC4B093)    // Sandstone blocks
            City.SYDNEY -> Color(0xFFA3998B)   // Warm coastal sandstone pavers
        }
        val curbTopColor = when (city) {
            City.NEW_YORK -> Color(0xFFB0AAA2)
            City.PARIS -> Color(0xFFC9BEB0)
            City.TOKYO -> Color(0xFF96908A)
            City.LONDON -> Color(0xFF9E9890)
            City.CAIRO -> Color(0xFFD6C4A8)
            City.SYDNEY -> Color(0xFFB8AEA0)
        }
        val curbFaceColor = Color(0xFF3A3633)

        // Left & Right Elevated Sidewalk Walkways
        sharedPath.reset()
        sharedPath.moveTo(vanishingX, vanishingY)
        sharedPath.lineTo(roadStartXAtBottom, height)
        sharedPath.lineTo(roadStartXAtBottom - sidewalkWidthAtBottom, height)
        sharedPath.close()
        drawPath(sharedPath, sidewalkBase)

        sharedPath.reset()
        sharedPath.moveTo(vanishingX, vanishingY)
        sharedPath.lineTo(roadEndXAtBottom, height)
        sharedPath.lineTo(roadEndXAtBottom + sidewalkWidthAtBottom, height)
        sharedPath.close()
        drawPath(sharedPath, sidewalkBase)

        // Main Roadway Trapezoid
        sharedPath.reset()
        sharedPath.moveTo(vanishingX, vanishingY)
        sharedPath.lineTo(roadEndXAtBottom, height)
        sharedPath.lineTo(roadStartXAtBottom, height)
        sharedPath.close()
        drawPath(
            path = sharedPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    roadColor.copy(alpha = 0.95f),
                    roadColor,
                    Color(0xFF1E1D1C)
                ),
                startY = vanishingY,
                endY = height
            )
        )

        // Roadway Wear Tracks (Subtle tire polish bands down the 3 lanes)
        for (lane in 0..2) {
            val laneCenterBottom = roadStartXAtBottom + roadWidthAtBottom * ((lane + 0.5f) / 3f)
            val trackHalfW = roadWidthAtBottom * 0.09f
            sharedPath.reset()
            sharedPath.moveTo(vanishingX, vanishingY)
            sharedPath.lineTo(laneCenterBottom + trackHalfW, height)
            sharedPath.lineTo(laneCenterBottom - trackHalfW, height)
            sharedPath.close()
            drawPath(sharedPath, Color.White.copy(alpha = 0.03f))
        }

        // 3D Raised Sidewalk Curbs (Vertical inner face + top bevel)
        sharedPath.reset()
        sharedPath.moveTo(vanishingX, vanishingY)
        sharedPath.lineTo(roadStartXAtBottom + curbWidthAtBottom * 0.45f, height)
        sharedPath.lineTo(roadStartXAtBottom, height)
        sharedPath.close()
        drawPath(sharedPath, curbFaceColor)

        sharedPath.reset()
        sharedPath.moveTo(vanishingX, vanishingY)
        sharedPath.lineTo(roadStartXAtBottom, height)
        sharedPath.lineTo(roadStartXAtBottom - curbWidthAtBottom, height)
        sharedPath.close()
        drawPath(sharedPath, curbTopColor)

        sharedPath.reset()
        sharedPath.moveTo(vanishingX, vanishingY)
        sharedPath.lineTo(roadEndXAtBottom - curbWidthAtBottom * 0.45f, height)
        sharedPath.lineTo(roadEndXAtBottom, height)
        sharedPath.close()
        drawPath(sharedPath, curbFaceColor)

        sharedPath.reset()
        sharedPath.moveTo(vanishingX, vanishingY)
        sharedPath.lineTo(roadEndXAtBottom, height)
        sharedPath.lineTo(roadEndXAtBottom + curbWidthAtBottom, height)
        sharedPath.close()
        drawPath(sharedPath, curbTopColor)

        // Edge Drainage Gutter Shadow
        drawLine(
            color = Color.Black.copy(alpha = 0.42f),
            start = Offset(vanishingX, vanishingY),
            end = Offset(roadStartXAtBottom + curbWidthAtBottom * 0.5f, height),
            strokeWidth = 3f
        )
        drawLine(
            color = Color.Black.copy(alpha = 0.42f),
            start = Offset(vanishingX, vanishingY),
            end = Offset(roadEndXAtBottom - curbWidthAtBottom * 0.5f, height),
            strokeWidth = 3f
        )

        // 3. Perspective Sidewalk Paving Joints, Manhole Covers & Street Details
        val maxZDist = 15f
        val minZ = floor(distance - 2f).toInt()
        val maxZ = ceil(distance + maxZDist).toInt()

        for (zIndex in maxZ downTo minZ) {
            val relZ = zIndex - distance
            if (relZ <= 0.05f || relZ > maxZDist) continue
            val yRatio = 1f - (relZ / maxZDist)
            val p = yRatio * yRatio * yRatio
            val sy = vanishingY + p * (height - vanishingY)
            val curveX = turnDirection * (1f - yRatio) * (1f - yRatio) * width * 0.45f
            val vx = vanishingX + curveX

            val leftRoadEdge = vx + (roadStartXAtBottom - vx) * p
            val rightRoadEdge = vx + (roadEndXAtBottom - vx) * p
            val leftSidewalkOuter = vx + (roadStartXAtBottom - sidewalkWidthAtBottom - vx) * p
            val rightSidewalkOuter = vx + (roadEndXAtBottom + sidewalkWidthAtBottom - vx) * p

            // Sidewalk expansion joints
            val jointAlpha = (yRatio * 0.35f).coerceIn(0f, 0.35f)
            drawLine(
                color = Color.Black.copy(alpha = jointAlpha),
                start = Offset(leftSidewalkOuter, sy),
                end = Offset(leftRoadEdge, sy),
                strokeWidth = (1.5f * p).coerceAtLeast(1f)
            )
            drawLine(
                color = Color.Black.copy(alpha = jointAlpha),
                start = Offset(rightRoadEdge, sy),
                end = Offset(rightSidewalkOuter, sy),
                strokeWidth = (1.5f * p).coerceAtLeast(1f)
            )

            // Paris Cobblestone / Cairo Stone Paver courses across the road
            if (city == City.PARIS || city == City.CAIRO) {
                drawLine(
                    color = Color.Black.copy(alpha = (yRatio * 0.16f).coerceIn(0f, 0.16f)),
                    start = Offset(leftRoadEdge, sy),
                    end = Offset(rightRoadEdge, sy),
                    strokeWidth = (1.2f * p).coerceAtLeast(0.8f)
                )
            }

            // Cast-iron manhole covers & storm grates every 5 blocks
            if (zIndex % 5 == 0 && p > 0.06f) {
                val roadCenter = (leftRoadEdge + rightRoadEdge) * 0.5f
                val mhW = roadWidthAtBottom * 0.085f * p
                val mhH = mhW * 0.34f
                drawOval(
                    color = Color(0xFF1C1A18),
                    topLeft = Offset(roadCenter - mhW * 0.5f + mhW * 0.6f, sy - mhH * 0.5f),
                    size = Size(mhW, mhH)
                )
                drawOval(
                    color = Color(0xFF4A443F),
                    topLeft = Offset(roadCenter - mhW * 0.44f + mhW * 0.6f, sy - mhH * 0.42f),
                    size = Size(mhW * 0.88f, mhH * 0.84f)
                )
                // NYC Steam Vent wisps
                if (city == City.NEW_YORK && zIndex % 10 == 0) {
                    val steamDrift = sin(distance * 3f + zIndex) * mhW * 0.4f
                    drawOval(
                        color = Color(0xFFF5F2EB).copy(alpha = (0.18f * yRatio).coerceIn(0f, 0.22f)),
                        topLeft = Offset(roadCenter + steamDrift, sy - mhW * 1.1f),
                        size = Size(mhW * 1.3f, mhW * 0.9f)
                    )
                }
            }
        }

        // 4. Dashed Lane Divider Markings (2 dividers for 3 lanes)
        val dashPeriod = 1.0f
        val dashFraction = 0.52f
        val firstDashIndex = floor(distance / dashPeriod).toInt() - 2
        val lastDashIndex = ceil((distance + maxZDist) / dashPeriod).toInt()

        val laneLineColor = when (city) {
            City.NEW_YORK -> Color(0xFFFFC107) // NYC yellow traffic lines
            City.TOKYO -> Color(0xFFF5F3EE)
            else -> Color(0xFFF2EFE9)
        }

        for (i in 1..2) {
            val laneBottomX = roadStartXAtBottom + (roadWidthAtBottom / 3f) * i
            for (dIdx in lastDashIndex downTo firstDashIndex) {
                // Skip lane dashes inside cross-street intersections
                if ( kotlin.math.abs(dIdx % 6) == 0 ) continue

                val zStart = dIdx * dashPeriod - distance
                val zEnd = zStart + dashPeriod * dashFraction
                val clampedStart = zStart.coerceAtLeast(0.02f)
                val clampedEnd = zEnd.coerceAtMost(maxZDist)
                if (clampedEnd <= clampedStart) continue

                val yRatio1 = 1f - (clampedStart / maxZDist)
                val yRatio2 = 1f - (clampedEnd / maxZDist)
                val p1 = yRatio1 * yRatio1 * yRatio1
                val p2 = yRatio2 * yRatio2 * yRatio2

                val y1 = vanishingY + p1 * (height - vanishingY)
                val y2 = vanishingY + p2 * (height - vanishingY)

                val c1 = turnDirection * (1f - yRatio1) * (1f - yRatio1) * width * 0.45f
                val c2 = turnDirection * (1f - yRatio2) * (1f - yRatio2) * width * 0.45f

                val x1 = (vanishingX + c1) + (laneBottomX - (vanishingX + c1)) * p1
                val x2 = (vanishingX + c2) + (laneBottomX - (vanishingX + c2)) * p2

                val strokeW = (5.5f * ((p1 + p2) * 0.5f)).coerceAtLeast(1.2f)
                drawLine(
                    color = laneLineColor.copy(alpha = (yRatio1 * 0.92f).coerceIn(0.15f, 0.92f)),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Butt
                )
            }
        }

        // 5. Realistic 3D City Blocks, Cross-Streets, Crosswalks & Sidewalk Furniture
        // Painter's algorithm: far to near (zIndex descending)
        val buildingStep = 2
        val maxAlignedZ = maxZ + (buildingStep - (maxZ % buildingStep)) % buildingStep
        for (zIndex in maxAlignedZ downTo minZ step buildingStep) {
            val isIntersection = (zIndex % 6 == 0)
            if (isIntersection) {
                drawCrossStreetAndCrosswalk(
                    zIndex = zIndex,
                    distance = distance,
                    maxZDist = maxZDist,
                    turnDirection = turnDirection,
                    width = width,
                    height = height,
                    vanishingX = vanishingX,
                    vanishingY = vanishingY,
                    roadStartXAtBottom = roadStartXAtBottom,
                    roadEndXAtBottom = roadEndXAtBottom,
                    roadColor = roadColor
                )
            } else {
                drawRealisticBuildingPair(
                    city = city,
                    zIndex = zIndex,
                    buildingStep = buildingStep,
                    distance = distance,
                    maxZDist = maxZDist,
                    turnDirection = turnDirection,
                    width = width,
                    height = height,
                    vanishingX = vanishingX,
                    vanishingY = vanishingY,
                    roadStartXAtBottom = roadStartXAtBottom,
                    roadEndXAtBottom = roadEndXAtBottom,
                    sidewalkWidthAtBottom = sidewalkWidthAtBottom
                )
            }
        }
    }

    private fun DrawScope.drawCrossStreetAndCrosswalk(
        zIndex: Int,
        distance: Float,
        maxZDist: Float,
        turnDirection: Float,
        width: Float,
        height: Float,
        vanishingX: Float,
        vanishingY: Float,
        roadStartXAtBottom: Float,
        roadEndXAtBottom: Float,
        roadColor: Color
    ) {
        val relZFront = zIndex - distance
        val relZBack = relZFront + 1.5f
        if (relZBack <= 0.05f || relZFront >= maxZDist) return

        val cFront = relZFront.coerceIn(0.02f, maxZDist)
        val cBack = relZBack.coerceIn(0.02f, maxZDist)
        if (cBack <= cFront) return

        val yRatioF = 1f - (cFront / maxZDist)
        val yRatioB = 1f - (cBack / maxZDist)
        val pF = yRatioF * yRatioF * yRatioF
        val pB = yRatioB * yRatioB * yRatioB

        val yF = vanishingY + pF * (height - vanishingY)
        val yB = vanishingY + pB * (height - vanishingY)

        // Smooth fade only near the far horizon; completely opaque for near/mid street
        val horizonVisibility = ((1f - (relZFront / maxZDist)) * 2.5f).coerceIn(0f, 1f)
        // True neutral dark asphalt for cross-street (NO skyColor blending -> zero blue tinge!)
        val crossAsphalt = shadeByDistance(roadColor, horizonVisibility)

        // Cross-street asphalt band extending left and right across the screen
        drawRect(
            color = crossAsphalt,
            topLeft = Offset(0f, yB),
            size = Size(width, (yF - yB).coerceAtLeast(1f))
        )

        // Cross-street curb edges
        drawLine(
            color = Color(0xFF8C867E).copy(alpha = horizonVisibility),
            start = Offset(0f, yB),
            end = Offset(width, yB),
            strokeWidth = (2.5f * pB).coerceAtLeast(1f)
        )
        drawLine(
            color = Color(0xFF8C867E).copy(alpha = horizonVisibility),
            start = Offset(0f, yF),
            end = Offset(width, yF),
            strokeWidth = (2.5f * pF).coerceAtLeast(1f)
        )

        // Crisp white pedestrian zebra crosswalk stripes across the main avenue (Opaque warm white, no blue!)
        val curveF = turnDirection * (1f - yRatioF) * (1f - yRatioF) * width * 0.45f
        val curveB = turnDirection * (1f - yRatioB) * (1f - yRatioB) * width * 0.45f
        val vxF = vanishingX + curveF
        val vxB = vanishingX + curveB

        val roadLeftF = vxF + (roadStartXAtBottom - vxF) * pF
        val roadRightF = vxF + (roadEndXAtBottom - vxF) * pF
        val roadLeftB = vxB + (roadStartXAtBottom - vxB) * pB
        val roadRightB = vxB + (roadEndXAtBottom - vxB) * pB

        val stripeCount = 7
        val stripeTopColor = Color(0xFFF7F5F0).copy(alpha = (0.94f * horizonVisibility).coerceIn(0f, 1f))
        val stripeEdgeColor = Color(0xFFC7C2B8).copy(alpha = (0.94f * horizonVisibility).coerceIn(0f, 1f))
        val bevelThickness = ((yF - yB) * 0.08f).coerceAtLeast(1f)

        for (s in 0 until stripeCount) {
            val t0 = (s + 0.18f) / stripeCount
            val t1 = (s + 0.72f) / stripeCount

            val sx0F = roadLeftF + (roadRightF - roadLeftF) * t0
            val sx1F = roadLeftF + (roadRightF - roadLeftF) * t1
            val sx0B = roadLeftB + (roadRightB - roadLeftB) * t0
            val sx1B = roadLeftB + (roadRightB - roadLeftB) * t1

            // Subtle 3D front paint thickness edge
            sharedPath.reset()
            sharedPath.moveTo(sx0F, yF)
            sharedPath.lineTo(sx1F, yF)
            sharedPath.lineTo(sx1F, yF + bevelThickness)
            sharedPath.lineTo(sx0F, yF + bevelThickness)
            sharedPath.close()
            drawPath(sharedPath, stripeEdgeColor)

            // Main crisp white zebra stripe
            sharedPath.reset()
            sharedPath.moveTo(sx0B, yB)
            sharedPath.lineTo(sx1B, yB)
            sharedPath.lineTo(sx1F, yF)
            sharedPath.lineTo(sx0F, yF)
            sharedPath.close()
            drawPath(sharedPath, stripeTopColor)
        }
    }

    private fun DrawScope.drawRealisticBuildingPair(
        city: City,
        zIndex: Int,
        buildingStep: Int,
        distance: Float,
        maxZDist: Float,
        turnDirection: Float,
        width: Float,
        height: Float,
        vanishingX: Float,
        vanishingY: Float,
        roadStartXAtBottom: Float,
        roadEndXAtBottom: Float,
        sidewalkWidthAtBottom: Float
    ) {
        val relZFront = zIndex - distance
        val relZBack = relZFront + buildingStep * 0.92f
        if (relZBack <= 0.04f || relZFront >= maxZDist) return

        val cFront = relZFront.coerceIn(0.02f, maxZDist)
        val cBack = relZBack.coerceIn(0.02f, maxZDist)
        if (cBack <= cFront) return

        val yRatioF = 1f - (cFront / maxZDist)
        val yRatioB = 1f - (cBack / maxZDist)

        val pF = yRatioF * yRatioF * yRatioF
        val pB = yRatioB * yRatioB * yRatioB

        val yF = vanishingY + pF * (height - vanishingY)
        val yB = vanishingY + pB * (height - vanishingY)

        val curveF = turnDirection * (1f - yRatioF) * (1f - yRatioF) * width * 0.45f
        val curveB = turnDirection * (1f - yRatioB) * (1f - yRatioB) * width * 0.45f
        val vxF = vanishingX + curveF
        val vxB = vanishingX + curveB

        // Buildings sit directly on the outer edge of the sidewalk
        val leftBaseOffsetAtBottom = roadStartXAtBottom - sidewalkWidthAtBottom * 0.85f
        val rightBaseOffsetAtBottom = roadEndXAtBottom + sidewalkWidthAtBottom * 0.85f

        val leftXFront = vxF + (leftBaseOffsetAtBottom - vxF) * pF
        val leftXBack = vxB + (leftBaseOffsetAtBottom - vxB) * pB

        val rightXFront = vxF + (rightBaseOffsetAtBottom - vxF) * pF
        val rightXBack = vxB + (rightBaseOffsetAtBottom - vxB) * pB

        // Deterministic architectural seed per building slot
        val seedL = kotlin.math.abs(zIndex * 31 + 7)
        val seedR = kotlin.math.abs(zIndex * 37 + 19)

        val baseWidthAtBottom = width * 0.56f
        val bWidthLF = baseWidthAtBottom * pF
        val bWidthRF = baseWidthAtBottom * pF

        val heightFactorL = when (city) {
            City.NEW_YORK -> 1.15f + (seedL % 5) * 0.22f
            City.TOKYO -> 1.05f + (seedL % 5) * 0.20f
            City.PARIS -> 0.82f + (seedL % 3) * 0.10f // Uniform Haussmann height cornice line
            City.LONDON -> 0.85f + (seedL % 4) * 0.14f
            City.CAIRO -> 0.72f + (seedL % 4) * 0.14f
            City.SYDNEY -> 0.90f + (seedL % 5) * 0.18f
        }
        val heightFactorR = when (city) {
            City.NEW_YORK -> 1.15f + (seedR % 5) * 0.22f
            City.TOKYO -> 1.05f + (seedR % 5) * 0.20f
            City.PARIS -> 0.82f + (seedR % 3) * 0.10f
            City.LONDON -> 0.85f + (seedR % 4) * 0.14f
            City.CAIRO -> 0.72f + (seedR % 4) * 0.14f
            City.SYDNEY -> 0.90f + (seedR % 5) * 0.18f
        }

        val baseHeightAtBottom = height * 0.95f
        val bHeightLF = baseHeightAtBottom * heightFactorL * pF
        val bHeightLB = baseHeightAtBottom * heightFactorL * pB
        val bHeightRF = baseHeightAtBottom * heightFactorR * pF
        val bHeightRB = baseHeightAtBottom * heightFactorR * pB

        val distanceVisibility = ((1f - (relZFront / maxZDist)) * 2.2f).coerceIn(0f, 1f)

        // Draw Left Building
        drawSingleBuilding3D(
            city = city,
            isLeft = true,
            seed = seedL,
            xFront = leftXFront,
            xBack = leftXBack,
            yFront = yF,
            yBack = yB,
            hFront = bHeightLF,
            hBack = bHeightLB,
            wFront = bWidthLF,
            pFront = pF,
            pBack = pB,
            visibility = distanceVisibility
        )

        // Draw Right Building
        drawSingleBuilding3D(
            city = city,
            isLeft = false,
            seed = seedR,
            xFront = rightXFront,
            xBack = rightXBack,
            yFront = yF,
            yBack = yB,
            hFront = bHeightRF,
            hBack = bHeightRB,
            wFront = bWidthRF,
            pFront = pF,
            pBack = pB,
            visibility = distanceVisibility
        )

        // Draw Street-Level Roadside Props on the Sidewalk (Trees, Hydrants, Lamps, Bollards)
        val leftCurbX = vxF + (roadStartXAtBottom - sidewalkWidthAtBottom * 0.22f - vxF) * pF
        val rightCurbX = vxF + (roadEndXAtBottom + sidewalkWidthAtBottom * 0.22f - vxF) * pF
        drawSidewalkProps(
            city = city,
            zIndex = zIndex,
            leftX = leftCurbX,
            rightX = rightCurbX,
            y = yF,
            scale = pF,
            visibility = distanceVisibility
        )
    }

    private fun DrawScope.drawSingleBuilding3D(
        city: City,
        isLeft: Boolean,
        seed: Int,
        xFront: Float,
        xBack: Float,
        yFront: Float,
        yBack: Float,
        hFront: Float,
        hBack: Float,
        wFront: Float,
        pFront: Float,
        pBack: Float,
        visibility: Float
    ) {
        val topYFront = yFront - hFront
        val topYBack = yBack - hBack
        val outerXFront = if (isLeft) xFront - wFront else xFront + wFront

        // Authentic architectural material palettes per city (Warm brick, limestone, sandstone, terracotta - NO blue tinge!)
        val palette = when (city) {
            City.NEW_YORK -> listOf(
                Color(0xFF8D402F), // Classic NYC Terracotta Red Brick
                Color(0xFF6E4B3A), // Historic Manhattan Brownstone
                Color(0xFFC8B8A2), // Pre-war Buff Limestone
                Color(0xFF5C554E), // Warm Bronze & Granite Tower
                Color(0xFFA69683)  // SoHo Cast-Iron Sandstone
            )
            City.PARIS -> listOf(
                Color(0xFFE6DAC6), // Haussmann Lutetian Cream Limestone
                Color(0xFFDCCBB2), // Warm Parisian Stone
                Color(0xFFD1BEA3)  // Classic Sandstone
            )
            City.TOKYO -> listOf(
                Color(0xFF524E4A), // Warm Architectural Concrete
                Color(0xFF383431), // Shibuya Charcoal Tile
                Color(0xFFD8D0C5), // Warm Ivory Ceramic Facade
                Color(0xFF6B635B)  // Warm Slate Cladding
            )
            City.LONDON -> listOf(
                Color(0xFF8F3B2B), // Georgian Red Brick
                Color(0xFFE8DFD1), // Belgravia Cream Stucco
                Color(0xFFC9C0B5), // Whitechapel Portland Stone
                Color(0xFFA85438)  // Victorian Terracotta
            )
            City.CAIRO -> listOf(
                Color(0xFFD6B485), // Warm Sandstone
                Color(0xFFC49D68), // Sun-baked Ochre Mudbrick
                Color(0xFFE0C59B), // Desert Limestone
                Color(0xFFB88B58)  // Terracotta Clay
            )
            City.SYDNEY -> listOf(
                Color(0xFFD4B483), // The Rocks Yellow-Block Sandstone
                Color(0xFFC9A982), // Warm Harbour Limestone
                Color(0xFFDDD3C5), // Federation Cream Facade
                Color(0xFF8C6D53)  // Warm Timber & Brick Cladding
            )
        }

        val rawColor = palette[seed % palette.size]
        // Street-facing wall is slightly sun-shaded; front wall catches direct light
        val streetWallBase = darkenColor(rawColor, 0.78f)
        val frontWallBase = darkenColor(rawColor, 0.94f)
        val trimBase = lightenColor(rawColor, 1.16f)

        val streetWallColor = shadeByDistance(streetWallBase, visibility)
        val frontWallColor = shadeByDistance(frontWallBase, visibility)
        val trimColor = shadeByDistance(trimBase, visibility)

        // 1. Street-Facing Perspective Side Wall (Quad connecting Back Z to Front Z)
        sharedPath.reset()
        sharedPath.moveTo(xBack, yBack)
        sharedPath.lineTo(xFront, yFront)
        sharedPath.lineTo(xFront, topYFront)
        sharedPath.lineTo(xBack, topYBack)
        sharedPath.close()
        drawPath(sharedPath, streetWallColor)

        // 2. Ground-Floor Storefront Base on Street-Facing Wall
        val groundFloorRatio = 0.20f
        val gfYFront = yFront - hFront * groundFloorRatio
        val gfYBack = yBack - hBack * groundFloorRatio
        val groundBaseColor = when (city) {
            City.PARIS -> Color(0xFF3E352F)
            City.LONDON -> Color(0xFF2E2723)
            City.NEW_YORK -> Color(0xFF38322E)
            else -> darkenColor(streetWallBase, 0.65f)
        }
        val groundColor = shadeByDistance(groundBaseColor, visibility)

        sharedPath.reset()
        sharedPath.moveTo(xBack, yBack)
        sharedPath.lineTo(xFront, yFront)
        sharedPath.lineTo(xFront, gfYFront)
        sharedPath.lineTo(xBack, gfYBack)
        sharedPath.close()
        drawPath(sharedPath, groundColor)

        // 3. Perspective Floor Cornices / Stringcourses & Recessed Windows on Street-Facing Wall
        val floors = (4 + (seed % 4)).coerceAtMost(7)
        val litWindowBase = when (city) {
            City.TOKYO -> if (seed % 2 == 0) Color(0xFFFFF3E0) else Color(0xFFFFCC80)
            else -> Color(0xFFFFF3C4)
        }
        // Warm dark interior glass shadow (NO blue tint!)
        val unlitWinBase = Color(0xFF26211E)
        val litWinColor = shadeByDistance(litWindowBase, visibility)
        val unlitWinColor = shadeByDistance(unlitWinBase, visibility)

        if (pFront > 0.04f) {
            // Horizontal stone stringcourses every 2 floors
            for (f in 1 until floors) {
                val frac = groundFloorRatio + (1f - groundFloorRatio) * (f.toFloat() / floors)
                if (f % 2 == 0) {
                    val lyF = yFront - hFront * frac
                    val lyB = yBack - hBack * frac
                    drawLine(
                        color = trimColor,
                        start = Offset(xBack, lyB),
                        end = Offset(xFront, lyF),
                        strokeWidth = (2.2f * pFront).coerceAtLeast(1f)
                    )
                }
            }

            // Perspective window bays along the Z depth of the building
            val bays = 3
            for (f in 1 until floors) {
                val fTopFrac = groundFloorRatio + (1f - groundFloorRatio) * ((f + 0.68f) / floors)
                val fBotFrac = groundFloorRatio + (1f - groundFloorRatio) * ((f + 0.18f) / floors)

                for (b in 0 until bays) {
                    val t0 = (b + 0.18f) / bays
                    val t1 = (b + 0.78f) / bays

                    val wx0 = xBack + (xFront - xBack) * t0
                    val wx1 = xBack + (xFront - xBack) * t1

                    val wyBot0 = (yBack - hBack * fBotFrac) + ((yFront - hFront * fBotFrac) - (yBack - hBack * fBotFrac)) * t0
                    val wyBot1 = (yBack - hBack * fBotFrac) + ((yFront - hFront * fBotFrac) - (yBack - hBack * fBotFrac)) * t1
                    val wyTop0 = (yBack - hBack * fTopFrac) + ((yFront - hFront * fTopFrac) - (yBack - hBack * fTopFrac)) * t0
                    val wyTop1 = (yBack - hBack * fTopFrac) + ((yFront - hFront * fTopFrac) - (yBack - hBack * fTopFrac)) * t1

                    val isLit = ((seed + f * 7 + b * 13) % 5 == 0)
                    val wColor = if (isLit) litWinColor else unlitWinColor

                    sharedPath.reset()
                    sharedPath.moveTo(wx0, wyBot0)
                    sharedPath.lineTo(wx1, wyBot1)
                    sharedPath.lineTo(wx1, wyTop1)
                    sharedPath.lineTo(wx0, wyTop0)
                    sharedPath.close()
                    drawPath(sharedPath, wColor)

                    // Parisian wrought-iron Juliet balcony railing
                    if (city == City.PARIS && pFront > 0.12f) {
                        drawLine(
                            color = Color(0xFF2B2623),
                            start = Offset(wx0, wyBot0),
                            end = Offset(wx1, wyBot1),
                            strokeWidth = (2.5f * pFront).coerceAtLeast(1f)
                        )
                    }
                }
            }

            // Storefront Awning (Paris, New York, London, Sydney)
            if (city != City.CAIRO || seed % 2 == 0) {
                val awningColorBase = when (city) {
                    City.PARIS -> if (seed % 2 == 0) Color(0xFFB71C1C) else Color(0xFF1B5E20)
                    City.NEW_YORK -> Color(0xFF2E5A44)
                    City.TOKYO -> Color(0xFFD84315)
                    City.LONDON -> Color(0xFF7F1D1D)
                    City.CAIRO -> Color(0xFFC6842F)
                    City.SYDNEY -> Color(0xFFBF360C)
                }
                val awningColor = shadeByDistance(awningColorBase, visibility)
                val awningOut = (if (isLeft) 1f else -1f) * (wFront * 0.14f)
                val awTopF = gfYFront
                val awTopB = gfYBack
                val awBotF = gfYFront + hFront * 0.045f
                val awBotB = gfYBack + hBack * 0.045f

                sharedPath.reset()
                sharedPath.moveTo(xBack, awTopB)
                sharedPath.lineTo(xFront, awTopF)
                sharedPath.lineTo(xFront + awningOut, awBotF)
                sharedPath.lineTo(xBack + awningOut * (pBack / pFront.coerceAtLeast(0.001f)), awBotB)
                sharedPath.close()
                drawPath(sharedPath, awningColor)
            }

            // Tokyo Vertical Illuminated Street Signboards protruding from facade
            if (city == City.TOKYO && pFront > 0.06f) {
                val neonColors = listOf(Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00), Color(0xFF00E676))
                val signColor = shadeByDistance(neonColors[seed % neonColors.size], visibility)
                val signOut = (if (isLeft) 1f else -1f) * (wFront * 0.16f)
                val signTop = topYFront + hFront * 0.18f
                val signBot = topYFront + hFront * 0.62f
                val leftSignX = if (isLeft) xFront else xFront + signOut
                drawRect(
                    color = signColor,
                    topLeft = Offset(leftSignX, signTop),
                    size = Size(kotlin.math.abs(signOut), (signBot - signTop).coerceAtLeast(2f))
                )
                drawRect(
                    color = Color.White.copy(alpha = 0.7f * visibility),
                    topLeft = Offset(leftSignX + kotlin.math.abs(signOut) * 0.25f, signTop + (signBot - signTop) * 0.15f),
                    size = Size(kotlin.math.abs(signOut) * 0.5f, (signBot - signTop) * 0.7f)
                )
            }

            // NYC Black Iron Exterior Fire Escape Zig-Zag Stairs
            if (city == City.NEW_YORK && seed % 2 == 0 && pFront > 0.10f) {
                val ironColor = shadeByDistance(Color(0xFF24201D), visibility)
                val midX = (xFront + xBack) * 0.5f
                for (f in 1 until floors - 1) {
                    val yBalc = yFront - hFront * (groundFloorRatio + (1f - groundFloorRatio) * (f.toFloat() / floors))
                    val yNext = yFront - hFront * (groundFloorRatio + (1f - groundFloorRatio) * ((f + 1f) / floors))
                    drawLine(
                        color = ironColor,
                        start = Offset(xBack + (xFront - xBack) * 0.2f, yBalc),
                        end = Offset(xBack + (xFront - xBack) * 0.85f, yBalc),
                        strokeWidth = (2.5f * pFront).coerceAtLeast(1f)
                    )
                    drawLine(
                        color = ironColor,
                        start = Offset(midX, yBalc),
                        end = Offset(xFront, yNext),
                        strokeWidth = (1.6f * pFront).coerceAtLeast(0.8f)
                    )
                }
            }
        }

        // 4. Front-Facing Building Wall (Facing the player directly)
        val rectLeft = if (isLeft) outerXFront else xFront
        val rectWidth = kotlin.math.abs(xFront - outerXFront)

        if (rectWidth > 0.5f) {
            drawRect(
                color = frontWallColor,
                topLeft = Offset(rectLeft, topYFront),
                size = Size(rectWidth, hFront)
            )

            // Ground floor storefront band on front face
            drawRect(
                color = groundColor,
                topLeft = Offset(rectLeft, gfYFront),
                size = Size(rectWidth, hFront * groundFloorRatio)
            )

            // Roof Cornice Ledge & City-Specific Roofline Detail
            val corniceH = (hFront * 0.032f).coerceAtLeast(2f)
            drawRect(
                color = trimColor,
                topLeft = Offset(rectLeft - 2f * pFront, topYFront),
                size = Size(rectWidth + 4f * pFront, corniceH)
            )

            // Paris Charcoal-Slate Mansard Roof & Dormer Windows (Warm charcoal, no blue!)
            if (city == City.PARIS) {
                val mansardH = hFront * 0.12f
                val mansardColor = shadeByDistance(Color(0xFF45413E), visibility)
                sharedPath.reset()
                sharedPath.moveTo(xBack, topYBack)
                sharedPath.lineTo(xFront, topYFront)
                sharedPath.lineTo(xFront, topYFront - mansardH)
                sharedPath.lineTo(xBack, topYBack - mansardH * (pBack / pFront.coerceAtLeast(0.001f)))
                sharedPath.close()
                drawPath(sharedPath, mansardColor)

                drawRect(
                    color = mansardColor,
                    topLeft = Offset(rectLeft, topYFront - mansardH),
                    size = Size(rectWidth, mansardH)
                )
            }

            // NYC Rooftop Cedar Water Tank on left/right pre-war buildings
            if (city == City.NEW_YORK && seed % 3 == 0 && pFront > 0.08f) {
                val tankW = wFront * 0.22f
                val tankH = hFront * 0.09f
                val tankX = if (isLeft) xFront - tankW * 1.6f else xFront + tankW * 0.6f
                val tankY = topYFront - tankH * 1.35f
                // Support legs
                drawLine(Color(0xFF2B2623), Offset(tankX + tankW * 0.2f, topYFront), Offset(tankX + tankW * 0.3f, tankY + tankH), strokeWidth = 2f * pFront)
                drawLine(Color(0xFF2B2623), Offset(tankX + tankW * 0.8f, topYFront), Offset(tankX + tankW * 0.7f, tankY + tankH), strokeWidth = 2f * pFront)
                // Cedar barrel + conical roof
                drawRect(
                    color = shadeByDistance(Color(0xFF7D5438), visibility),
                    topLeft = Offset(tankX, tankY),
                    size = Size(tankW, tankH)
                )
                sharedPath.reset()
                sharedPath.moveTo(tankX - tankW * 0.1f, tankY)
                sharedPath.lineTo(tankX + tankW * 0.5f, tankY - tankH * 0.45f)
                sharedPath.lineTo(tankX + tankW * 1.1f, tankY)
                sharedPath.close()
                drawPath(sharedPath, shadeByDistance(Color(0xFF4A3322), visibility))
            }

            // Front Wall Window Grid
            if (pFront > 0.05f && rectWidth > 8f) {
                val cols = 3
                val padX = rectWidth * 0.12f
                val winW = (rectWidth - padX * (cols + 1)) / cols
                val winH = (hFront * (1f - groundFloorRatio) / floors) * 0.52f

                if (winW > 1.5f && winH > 1.5f) {
                    for (f in 1 until floors) {
                        val wy = yFront - hFront * (groundFloorRatio + (1f - groundFloorRatio) * ((f + 0.70f) / floors))
                        for (c in 0 until cols) {
                            val wx = rectLeft + padX + c * (winW + padX)
                            val isLit = ((seed + f * 11 + c * 5) % 6 == 0)
                            val winColor = if (isLit) litWinColor else unlitWinColor
                            // Recessed window frame
                            drawRect(
                                color = trimColor,
                                topLeft = Offset(wx - 1f, wy - 1f),
                                size = Size(winW + 2f, winH + 2f)
                            )
                            drawRect(
                                color = winColor,
                                topLeft = Offset(wx, wy),
                                size = Size(winW, winH)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun DrawScope.drawSidewalkProps(
        city: City,
        zIndex: Int,
        leftX: Float,
        rightX: Float,
        y: Float,
        scale: Float,
        visibility: Float
    ) {
        if (scale < 0.045f) return

        // Alternate street trees and city-specific sidewalk furniture
        if (zIndex % 4 == 0) {
            // Sidewalk Street Trees (Cherry Blossoms in Tokyo, Date Palms in Cairo, Plane Trees in Paris/London/NY/Sydney)
            drawSidewalkTree(city, leftX, y, scale, visibility)
            drawSidewalkTree(city, rightX, y, scale, visibility)
        } else if (zIndex % 2 == 0) {
            when (city) {
                City.NEW_YORK -> {
                    // Iconic NYC Cast-Iron Fire Hydrant
                    val hW = 14f * scale
                    val hH = 26f * scale
                    val hydColor = shadeByDistance(Color(0xFFC62828), visibility)
                    val capColor = shadeByDistance(Color(0xFFE0E0E0), visibility)
                    drawRect(hydColor, Offset(leftX - hW * 0.5f, y - hH), Size(hW, hH))
                    drawCircle(capColor, radius = hW * 0.55f, center = Offset(leftX, y - hH))
                    drawRect(hydColor, Offset(rightX - hW * 0.5f, y - hH), Size(hW, hH))
                    drawCircle(capColor, radius = hW * 0.55f, center = Offset(rightX, y - hH))
                }
                City.PARIS, City.LONDON -> {
                    // Ornate Cast-Iron Boulevard Lamp Post
                    val postH = 125f * scale
                    val postColor = shadeByDistance(Color(0xFF2B2825), visibility)
                    val glowColor = Color(0xFFFFF59D).copy(alpha = 0.85f * visibility)
                    for (px in listOf(leftX, rightX)) {
                        drawLine(postColor, Offset(px, y), Offset(px, y - postH), strokeWidth = (4.5f * scale).coerceAtLeast(1.2f))
                        drawCircle(glowColor, radius = (11f * scale).coerceAtLeast(2f), center = Offset(px, y - postH))
                    }
                }
                City.TOKYO -> {
                    // Illuminated Utility Pole & Vending Bollard
                    val postH = 135f * scale
                    val poleColor = shadeByDistance(Color(0xFF423E3B), visibility)
                    for (px in listOf(leftX, rightX)) {
                        drawLine(poleColor, Offset(px, y), Offset(px, y - postH), strokeWidth = (4f * scale).coerceAtLeast(1.2f))
                        drawRect(
                            color = Color(0xFFFFAB00).copy(alpha = 0.85f * visibility),
                            topLeft = Offset(px - 6f * scale, y - postH * 0.75f),
                            size = Size(12f * scale, 22f * scale)
                        )
                    }
                }
                else -> {
                    // Sandstone / Harbour Bollards
                    val bW = 12f * scale
                    val bH = 24f * scale
                    val bolColor = shadeByDistance(Color(0xFF5D534A), visibility)
                    drawRect(bolColor, Offset(leftX - bW * 0.5f, y - bH), Size(bW, bH))
                    drawRect(bolColor, Offset(rightX - bW * 0.5f, y - bH), Size(bW, bH))
                }
            }
        }
    }

    private fun DrawScope.drawSidewalkTree(
        city: City,
        x: Float,
        y: Float,
        scale: Float,
        visibility: Float
    ) {
        val trunkH = 95f * scale
        val trunkW = (9f * scale).coerceAtLeast(1.5f)
        val trunkColor = shadeByDistance(Color(0xFF4E342E), visibility)

        // Tree grate at base
        drawOval(
            color = Color.Black.copy(alpha = 0.35f * visibility),
            topLeft = Offset(x - 18f * scale, y - 5f * scale),
            size = Size(36f * scale, 10f * scale)
        )
        // Trunk
        drawLine(
            color = trunkColor,
            start = Offset(x, y),
            end = Offset(x, y - trunkH),
            strokeWidth = trunkW
        )

        if (city == City.CAIRO) {
            // Palm fronds
            val frondColor = shadeByDistance(Color(0xFF2E7D32), visibility)
            for (angle in listOf(-40f, -18f, 18f, 40f)) {
                val dx = angle * 1.1f * scale
                val dy = kotlin.math.abs(angle) * 0.45f * scale
                drawLine(
                    color = frondColor,
                    start = Offset(x, y - trunkH),
                    end = Offset(x + dx, y - trunkH + dy),
                    strokeWidth = (4.5f * scale).coerceAtLeast(1f),
                    cap = StrokeCap.Round
                )
            }
        } else {
            // Layered 3D foliage canopy (Sakura pink in Tokyo, lush green elsewhere)
            val canopyMain = if (city == City.TOKYO) Color(0xFFF48FB1) else Color(0xFF2E7D32)
            val canopyHighlight = if (city == City.TOKYO) Color(0xFFF8BBD0) else Color(0xFF43A047)
            val cMain = shadeByDistance(canopyMain, visibility)
            val cLight = shadeByDistance(canopyHighlight, visibility)
            val r = 34f * scale
            drawCircle(cMain, radius = r, center = Offset(x, y - trunkH - r * 0.4f))
            drawCircle(cMain, radius = r * 0.78f, center = Offset(x - r * 0.55f, y - trunkH - r * 0.15f))
            drawCircle(cMain, radius = r * 0.78f, center = Offset(x + r * 0.55f, y - trunkH - r * 0.15f))
            drawCircle(cLight, radius = r * 0.65f, center = Offset(x - r * 0.15f, y - trunkH - r * 0.65f))
        }
    }

    /**
     * Shades architectural colors purely by warm neutral atmospheric depth at the far horizon,
     * never tinting buildings or crosswalks blue.
     */
    private fun shadeByDistance(c: Color, visibility: Float): Color {
        val v = visibility.coerceIn(0f, 1f)
        // Keep 100% pure material color for everything in near/mid range;
        // only softly warm-neutralize at the very far horizon.
        val keep = 0.78f + 0.22f * v
        val neutralWarmHorizon = Color(0xFFD6CFC7)
        return Color(
            red = (c.red * keep + neutralWarmHorizon.red * (1f - keep)).coerceIn(0f, 1f),
            green = (c.green * keep + neutralWarmHorizon.green * (1f - keep)).coerceIn(0f, 1f),
            blue = (c.blue * keep + neutralWarmHorizon.blue * (1f - keep)).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    private fun darkenColor(c: Color, factor: Float): Color {
        return Color(
            red = (c.red * factor).coerceIn(0f, 1f),
            green = (c.green * factor).coerceIn(0f, 1f),
            blue = (c.blue * factor).coerceIn(0f, 1f),
            alpha = c.alpha
        )
    }

    private fun lightenColor(c: Color, factor: Float): Color {
        return Color(
            red = (c.red * factor).coerceIn(0f, 1f),
            green = (c.green * factor).coerceIn(0f, 1f),
            blue = (c.blue * factor).coerceIn(0f, 1f),
            alpha = c.alpha
        )
    }
}
