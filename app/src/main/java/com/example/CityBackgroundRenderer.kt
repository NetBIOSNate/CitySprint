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
import kotlin.math.cos
import kotlin.math.sin

object CityBackgroundRenderer {

    private val sharedPath = Path()

    private fun fastHash(seed: Int): Int {
        var x = seed
        x = ((x ushr 16) xor x) * 0x45d9f3b
        x = ((x ushr 16) xor x) * 0x45d9f3b
        x = (x ushr 16) xor x
        return x and 0x7FFFFFFF
    }

    private fun hashFloat(seed: Int): Float = (fastHash(seed) % 10000) / 10000f

    fun DrawScope.drawCityBackground(
        city: City,
        distance: Float,
        width: Float,
        vanishingX: Float,
        vanishingY: Float
    ) {
        // 1. Realistic Multi-Stop Atmospheric Sky
        val skyColors = when (city) {
            City.NEW_YORK -> listOf(
                Color(0xFF0D3B66),
                Color(0xFF1E6091),
                Color(0xFF5296A5),
                Color(0xFFF4A261)
            )
            City.PARIS -> listOf(
                Color(0xFF2B4C7E),
                Color(0xFF567EBB),
                Color(0xFFD99B82),
                Color(0xFFF6D5B0)
            )
            City.TOKYO -> listOf(
                Color(0xFF070714),
                Color(0xFF141836),
                Color(0xFF2B193E),
                Color(0xFF6B1D4F)
            )
            City.LONDON -> listOf(
                Color(0xFF2B3540),
                Color(0xFF4F5D6B),
                Color(0xFF83919E),
                Color(0xFFC2B9B0)
            )
            City.CAIRO -> listOf(
                Color(0xFF4A154B),
                Color(0xFF9E2A2B),
                Color(0xFFE07A5F),
                Color(0xFFF4D35E)
            )
            City.SYDNEY -> listOf(
                Color(0xFF005C97),
                Color(0xFF1E88E5),
                Color(0xFF64B5F6),
                Color(0xFFFFF3E0)
            )
        }

        drawRect(
            brush = Brush.verticalGradient(
                colors = skyColors,
                startY = 0f,
                endY = vanishingY
            ),
            size = Size(width, vanishingY)
        )

        // 2. Celestial Body (Sun / Moon / Atmospheric Glow)
        val sunX = width * 0.72f - ((distance * 0.005f) % 0.15f) * width
        val sunY = vanishingY * 0.34f
        when (city) {
            City.CAIRO -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFF59D).copy(alpha = 0.9f),
                            Color(0xFFFF9800).copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        center = Offset(sunX, sunY),
                        radius = vanishingY * 0.42f
                    ),
                    radius = vanishingY * 0.42f,
                    center = Offset(sunX, sunY)
                )
                drawCircle(
                    color = Color(0xFFFFFDE7),
                    radius = vanishingY * 0.11f,
                    center = Offset(sunX, sunY)
                )
            }
            City.TOKYO -> {
                // Crisp crescent/full moon & stars
                for (s in 0 until 24) {
                    val sx = hashFloat(s * 31 + 7) * width
                    val sy = hashFloat(s * 47 + 13) * (vanishingY * 0.65f)
                    val twinkle = 0.35f + 0.5f * kotlin.math.abs(sin(distance * 3f + s))
                    drawCircle(
                        color = Color.White.copy(alpha = twinkle),
                        radius = 1.5f + (s % 2),
                        center = Offset(sx, sy)
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFE0F7FA).copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(sunX, sunY * 0.8f),
                        radius = vanishingY * 0.22f
                    ),
                    radius = vanishingY * 0.22f,
                    center = Offset(sunX, sunY * 0.8f)
                )
                drawCircle(
                    color = Color(0xFFF5F9FF),
                    radius = vanishingY * 0.065f,
                    center = Offset(sunX, sunY * 0.8f)
                )
            }
            else -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFF9C4).copy(alpha = 0.65f),
                            Color(0xFFFFCC80).copy(alpha = 0.2f),
                            Color.Transparent
                        ),
                        center = Offset(sunX, sunY),
                        radius = vanishingY * 0.35f
                    ),
                    radius = vanishingY * 0.35f,
                    center = Offset(sunX, sunY)
                )
            }
        }

        // 3. Soft Layered Atmospheric Clouds
        val cloudScroll = (distance * 0.012f) % 1f
        val cloudColor = when (city) {
            City.TOKYO -> Color(0xFF3F2E56).copy(alpha = 0.35f)
            City.CAIRO -> Color(0xFFFFCC80).copy(alpha = 0.28f)
            City.LONDON -> Color(0xFFD6DCE2).copy(alpha = 0.45f)
            else -> Color.White.copy(alpha = 0.38f)
        }
        for (c in 0 until 6) {
            val baseCx = ((c * 0.21f - cloudScroll + 1f) % 1.2f - 0.1f) * width
            val cy = vanishingY * (0.16f + (c % 3) * 0.12f)
            val cw = width * (0.18f + (c % 2) * 0.08f)
            val ch = vanishingY * 0.055f
            drawRoundRect(
                color = cloudColor,
                topLeft = Offset(baseCx, cy),
                size = Size(cw, ch),
                cornerRadius = CornerRadius(ch * 0.5f)
            )
            drawRoundRect(
                color = cloudColor,
                topLeft = Offset(baseCx + cw * 0.18f, cy - ch * 0.45f),
                size = Size(cw * 0.55f, ch * 0.75f),
                cornerRadius = CornerRadius(ch * 0.5f)
            )
        }

        // 4. Far Horizon Silhouette Layer (Mountains / Distant Ridges / Skylines)
        val farOffset = (distance * 0.025f) % 1f
        val waterTopY = vanishingY * 0.90f
        for (wrap in 0..1) {
            val ox = (wrap - farOffset) * width
            when (city) {
                City.TOKYO -> {
                    // Iconic Mount Fuji with volcanic ridges and snow cap
                    val fujiCenterX = ox + width * 0.45f
                    val fujiBaseW = width * 0.78f
                    val fujiPeakY = vanishingY * 0.20f
                    val fujiTopW = width * 0.07f
                    sharedPath.reset()
                    sharedPath.moveTo(fujiCenterX - fujiBaseW * 0.5f, waterTopY)
                    sharedPath.quadraticBezierTo(
                        fujiCenterX - fujiBaseW * 0.18f,
                        vanishingY * 0.62f,
                        fujiCenterX - fujiTopW * 0.5f,
                        fujiPeakY
                    )
                    sharedPath.lineTo(fujiCenterX + fujiTopW * 0.5f, fujiPeakY)
                    sharedPath.quadraticBezierTo(
                        fujiCenterX + fujiBaseW * 0.18f,
                        vanishingY * 0.62f,
                        fujiCenterX + fujiBaseW * 0.5f,
                        waterTopY
                    )
                    sharedPath.close()
                    drawPath(
                        sharedPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF3949AB), Color(0xFF1A1B35)),
                            startY = fujiPeakY,
                            endY = waterTopY
                        )
                    )
                    // Snow cap with jagged glacial gullies
                    sharedPath.reset()
                    sharedPath.moveTo(fujiCenterX - fujiTopW * 0.5f, fujiPeakY)
                    sharedPath.lineTo(fujiCenterX + fujiTopW * 0.5f, fujiPeakY)
                    sharedPath.lineTo(fujiCenterX + fujiTopW * 1.45f, vanishingY * 0.42f)
                    sharedPath.lineTo(fujiCenterX + fujiTopW * 0.7f, vanishingY * 0.37f)
                    sharedPath.lineTo(fujiCenterX + fujiTopW * 0.25f, vanishingY * 0.44f)
                    sharedPath.lineTo(fujiCenterX - fujiTopW * 0.2f, vanishingY * 0.38f)
                    sharedPath.lineTo(fujiCenterX - fujiTopW * 0.8f, vanishingY * 0.43f)
                    sharedPath.lineTo(fujiCenterX - fujiTopW * 1.45f, vanishingY * 0.41f)
                    sharedPath.close()
                    drawPath(sharedPath, Color(0xFFF0F4F8).copy(alpha = 0.92f))
                }
                City.CAIRO -> {
                    // Distant desert sand dunes
                    sharedPath.reset()
                    sharedPath.moveTo(ox, waterTopY)
                    sharedPath.quadraticBezierTo(ox + width * 0.25f, vanishingY * 0.68f, ox + width * 0.52f, waterTopY)
                    sharedPath.quadraticBezierTo(ox + width * 0.78f, vanishingY * 0.72f, ox + width, waterTopY)
                    sharedPath.close()
                    drawPath(sharedPath, Color(0xFFC88A4B).copy(alpha = 0.55f))
                }
                else -> {
                    // Distant atmospheric high-rise cluster
                    var cx = 0f
                    var idx = 0
                    while (cx < width * 0.96f) {
                        val bw = width * (0.045f + hashFloat(idx * 17 + city.ordinal) * 0.055f)
                        val bh = vanishingY * (0.18f + hashFloat(idx * 29 + city.ordinal) * 0.28f)
                        drawSilhouetteTower(
                            x = ox + cx,
                            baseY = waterTopY,
                            w = bw,
                            h = bh,
                            color = Color(0xFF1E293B).copy(alpha = 0.35f),
                            windowColor = Color.Transparent,
                            seed = idx + city.ordinal * 100
                        )
                        cx += bw + width * 0.008f
                        idx++
                    }
                }
            }
        }

        // 5. Mid Parallax Layer: Realistic City Skyline & Iconic Landmarks
        val midOffset = (distance * 0.06f) % 1f
        for (wrap in 0..1) {
            val baseX = (wrap - midOffset) * width
            when (city) {
                City.NEW_YORK -> drawNewYorkSkyline(baseX, waterTopY, width, vanishingY)
                City.PARIS -> drawParisSkyline(baseX, waterTopY, width, vanishingY)
                City.TOKYO -> drawTokyoSkyline(baseX, waterTopY, width, vanishingY)
                City.LONDON -> drawLondonSkyline(baseX, waterTopY, width, vanishingY)
                City.CAIRO -> drawCairoSkyline(baseX, waterTopY, width, vanishingY)
                City.SYDNEY -> drawSydneySkyline(baseX, waterTopY, width, vanishingY)
            }
        }

        // 6. Waterfront / Riverband at the base of the skyline (Hudson, Seine, Tokyo Bay, Thames, Nile, Sydney Harbour)
        val waterHeight = vanishingY - waterTopY
        val waterColors = when (city) {
            City.NEW_YORK -> listOf(Color(0xFF1B435D), Color(0xFF0F293A))
            City.PARIS -> listOf(Color(0xFF3D6B82), Color(0xFF284B5E))
            City.TOKYO -> listOf(Color(0xFF121329), Color(0xFF241734))
            City.LONDON -> listOf(Color(0xFF45525C), Color(0xFF2F3940))
            City.CAIRO -> listOf(Color(0xFF8C4A32), Color(0xFF592E20))
            City.SYDNEY -> listOf(Color(0xFF0277BD), Color(0xFF01579B))
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = waterColors,
                startY = waterTopY,
                endY = vanishingY
            ),
            topLeft = Offset(0f, waterTopY),
            size = Size(width, waterHeight)
        )

        // Shimmering water reflections & stone embankment seawall
        val shimmerColor = when (city) {
            City.TOKYO -> Color(0xFFFF4081).copy(alpha = 0.35f)
            City.CAIRO -> Color(0xFFFFD54F).copy(alpha = 0.45f)
            else -> Color(0xFFFFF59D).copy(alpha = 0.32f)
        }
        for (i in 0 until 14) {
            val rx = ((i * 0.08f - midOffset * 1.3f + 2f) % 1f) * width
            val ry = waterTopY + (i % 3) * (waterHeight * 0.28f) + waterHeight * 0.15f
            drawLine(
                color = shimmerColor,
                start = Offset(rx, ry),
                end = Offset(rx + width * 0.04f, ry),
                strokeWidth = 2f
            )
        }
        // Embankment wall separating water from street horizon
        drawRect(
            color = Color(0xFF37474F),
            topLeft = Offset(0f, vanishingY - waterHeight * 0.18f),
            size = Size(width, waterHeight * 0.18f)
        )
        // Horizon atmospheric haze glow
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, skyColors.last().copy(alpha = 0.25f)),
                startY = vanishingY * 0.7f,
                endY = vanishingY
            ),
            topLeft = Offset(0f, vanishingY * 0.7f),
            size = Size(width, vanishingY * 0.3f)
        )
    }

    private fun DrawScope.drawSilhouetteTower(
        x: Float,
        baseY: Float,
        w: Float,
        h: Float,
        color: Color,
        windowColor: Color,
        seed: Int
    ) {
        val topY = baseY - h
        drawRect(color = color, topLeft = Offset(x, topY), size = Size(w, h))
        // Subtle 3D side shading
        drawRect(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(x + w * 0.78f, topY),
            size = Size(w * 0.22f, h)
        )
        if (seed % 3 == 0) {
            // Setback crown
            drawRect(
                color = color,
                topLeft = Offset(x + w * 0.18f, topY - h * 0.12f),
                size = Size(w * 0.64f, h * 0.12f)
            )
        }
        if (windowColor.alpha > 0.05f) {
            val rows = 6
            val cols = 3
            val winW = w * 0.16f
            val winH = h * 0.06f
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if ((fastHash(seed + r * 7 + c * 13) % 10) > 4) {
                        val wx = x + w * 0.14f + c * (w * 0.24f)
                        val wy = topY + h * 0.1f + r * (h * 0.13f)
                        drawRect(windowColor, Offset(wx, wy), Size(winW, winH))
                    }
                }
            }
        }
    }

    private fun DrawScope.drawNewYorkSkyline(baseX: Float, baseY: Float, width: Float, vanishingY: Float) {
        val bldgColor = Color(0xFF263849)
        val winColor = Color(0xFFFFE082).copy(alpha = 0.65f)

        // Background Manhattan high-rises
        val slots = floatArrayOf(0.02f, 0.09f, 0.27f, 0.35f, 0.56f, 0.64f, 0.88f)
        val heights = floatArrayOf(0.42f, 0.52f, 0.46f, 0.38f, 0.54f, 0.44f, 0.48f)
        for (i in slots.indices) {
            drawSilhouetteTower(
                x = baseX + width * slots[i],
                baseY = baseY,
                w = width * 0.065f,
                h = vanishingY * heights[i],
                color = bldgColor,
                windowColor = winColor,
                seed = i + 10
            )
        }

        // 1. One World Trade Center (Freedom Tower) with chamfered glass prism facets
        val owtcX = baseX + width * 0.16f
        val owtcW = width * 0.075f
        val owtcH = vanishingY * 0.78f
        val owtcTop = baseY - owtcH
        drawRect(Color(0xFF34546D), Offset(owtcX, owtcTop), Size(owtcW, owtcH))
        sharedPath.reset()
        sharedPath.moveTo(owtcX + owtcW * 0.5f, owtcTop)
        sharedPath.lineTo(owtcX + owtcW, baseY)
        sharedPath.lineTo(owtcX, baseY)
        sharedPath.close()
        drawPath(sharedPath, Color(0xFF5C88A6).copy(alpha = 0.55f))
        // Spire
        drawLine(
            color = Color(0xFFE0E0E0),
            start = Offset(owtcX + owtcW * 0.5f, owtcTop),
            end = Offset(owtcX + owtcW * 0.5f, owtcTop - vanishingY * 0.16f),
            strokeWidth = 3f
        )
        drawCircle(Color.Red, radius = 3.5f, center = Offset(owtcX + owtcW * 0.5f, owtcTop - vanishingY * 0.16f))

        // 2. Empire State Building (Art Deco tiered limestone setbacks + spire)
        val esbX = baseX + width * 0.43f
        val esbW = width * 0.095f
        val esbH = vanishingY * 0.74f
        drawRect(Color(0xFF3E4C59), Offset(esbX, baseY - esbH * 0.65f), Size(esbW, esbH * 0.65f))
        drawRect(Color(0xFF4A5A6A), Offset(esbX + esbW * 0.14f, baseY - esbH * 0.88f), Size(esbW * 0.72f, esbH * 0.23f))
        drawRect(Color(0xFF5B6D80), Offset(esbX + esbW * 0.28f, baseY - esbH), Size(esbW * 0.44f, esbH * 0.12f))
        // Mooring mast & spire
        drawRect(Color(0xFF90A4AE), Offset(esbX + esbW * 0.42f, baseY - esbH * 1.12f), Size(esbW * 0.16f, esbH * 0.12f))
        drawLine(
            color = Color(0xFFCFD8DC),
            start = Offset(esbX + esbW * 0.5f, baseY - esbH * 1.12f),
            end = Offset(esbX + esbW * 0.5f, baseY - esbH * 1.28f),
            strokeWidth = 3f
        )
        // Vertical window strips on ESB
        for (c in 1..4) {
            val lx = esbX + esbW * (0.2f * c)
            drawLine(
                color = winColor,
                start = Offset(lx, baseY - esbH * 0.85f),
                end = Offset(lx, baseY - esbH * 0.05f),
                strokeWidth = 2f
            )
        }

        // 3. Brooklyn Bridge (Stone Gothic tower + suspension cables)
        val brX = baseX + width * 0.68f
        val brW = width * 0.22f
        val towerX = brX + brW * 0.42f
        val towerW = width * 0.042f
        val towerH = vanishingY * 0.44f
        // Bridge deck
        drawLine(
            color = Color(0xFF37474F),
            start = Offset(brX, baseY - vanishingY * 0.14f),
            end = Offset(brX + brW, baseY - vanishingY * 0.14f),
            strokeWidth = 5f
        )
        // Catenary cables
        sharedPath.reset()
        sharedPath.moveTo(brX, baseY - vanishingY * 0.14f)
        sharedPath.quadraticBezierTo(
            brX + brW * 0.22f,
            baseY - vanishingY * 0.16f,
            towerX + towerW * 0.5f,
            baseY - towerH
        )
        sharedPath.quadraticBezierTo(
            brX + brW * 0.75f,
            baseY - vanishingY * 0.16f,
            brX + brW,
            baseY - vanishingY * 0.14f
        )
        drawPath(sharedPath, Color(0xFF90A4AE), style = Stroke(width = 2.5f))
        // Stone tower with twin Gothic arches
        drawRect(Color(0xFF6D6860), Offset(towerX, baseY - towerH), Size(towerW, towerH))
        drawRect(Color(0xFF263849), Offset(towerX + towerW * 0.15f, baseY - towerH * 0.72f), Size(towerW * 0.26f, towerH * 0.32f))
        drawRect(Color(0xFF263849), Offset(towerX + towerW * 0.58f, baseY - towerH * 0.72f), Size(towerW * 0.26f, towerH * 0.32f))

        // 4. Statue of Liberty
        val solX = baseX + width * 0.80f
        val pedW = width * 0.045f
        val pedH = vanishingY * 0.18f
        drawRect(Color(0xFF78716A), Offset(solX, baseY - pedH), Size(pedW, pedH))
        val patina = Color(0xFF68B092)
        sharedPath.reset()
        sharedPath.moveTo(solX + pedW * 0.22f, baseY - pedH)
        sharedPath.lineTo(solX + pedW * 0.78f, baseY - pedH)
        sharedPath.lineTo(solX + pedW * 0.65f, baseY - pedH - vanishingY * 0.26f)
        sharedPath.lineTo(solX + pedW * 0.35f, baseY - pedH - vanishingY * 0.26f)
        sharedPath.close()
        drawPath(sharedPath, patina)
        // Head & Crown
        val headCy = baseY - pedH - vanishingY * 0.29f
        drawCircle(patina, radius = width * 0.01f, center = Offset(solX + pedW * 0.5f, headCy))
        // Raised torch arm & glowing flame
        val torchX = solX + pedW * 0.82f
        val torchY = baseY - pedH - vanishingY * 0.40f
        drawLine(patina, Offset(solX + pedW * 0.62f, baseY - pedH - vanishingY * 0.24f), Offset(torchX, torchY), strokeWidth = 4.5f)
        drawCircle(Color(0xFFFFD54F), radius = 6f, center = Offset(torchX, torchY))
    }

    private fun DrawScope.drawParisSkyline(baseX: Float, baseY: Float, width: Float, vanishingY: Float) {
        val haussmannStone = Color(0xFF6D635B)
        val zincRoof = Color(0xFF3E4A56)

        // Haussmann mid-rise blocks along the Seine
        var cx = 0.02f
        while (cx < 0.92f) {
            if (cx in 0.36f..0.58f) {
                cx = 0.60f
                continue
            }
            val bw = width * 0.09f
            val bh = vanishingY * 0.25f
            val bx = baseX + width * cx
            drawRect(haussmannStone, Offset(bx, baseY - bh), Size(bw, bh))
            // Mansard sloped zinc roof
            sharedPath.reset()
            sharedPath.moveTo(bx, baseY - bh)
            sharedPath.lineTo(bx + bw, baseY - bh)
            sharedPath.lineTo(bx + bw * 0.88f, baseY - bh * 1.24f)
            sharedPath.lineTo(bx + bw * 0.12f, baseY - bh * 1.24f)
            sharedPath.close()
            drawPath(sharedPath, zincRoof)
            cx += 0.11f
        }

        // 1. Arc de Triomphe (Left landmark)
        val arcX = baseX + width * 0.14f
        val arcW = width * 0.11f
        val arcH = vanishingY * 0.38f
        drawRect(Color(0xFF9E8F7E), Offset(arcX, baseY - arcH), Size(arcW, arcH))
        drawRect(Color(0xFF847666), Offset(arcX - arcW * 0.04f, baseY - arcH), Size(arcW * 1.08f, arcH * 0.14f))
        // Central vaulted arch cutout
        drawRoundRect(
            color = Color(0xFF4A6382),
            topLeft = Offset(arcX + arcW * 0.32f, baseY - arcH * 0.62f),
            size = Size(arcW * 0.36f, arcH * 0.62f),
            cornerRadius = CornerRadius(arcW * 0.18f)
        )

        // 2. Detailed Eiffel Tower (Center landmark)
        val etCenterX = baseX + width * 0.48f
        val etBaseHalfW = width * 0.085f
        val etH = vanishingY * 0.92f
        val ironColor = Color(0xFF4A3C31)
        val ironHighlight = Color(0xFF705B4B)

        val yDeck1 = baseY - etH * 0.28f
        val yDeck2 = baseY - etH * 0.56f
        val yTop = baseY - etH * 0.88f

        // Left & Right curved lattice legs
        sharedPath.reset()
        sharedPath.moveTo(etCenterX - etBaseHalfW, baseY)
        sharedPath.quadraticBezierTo(etCenterX - etBaseHalfW * 0.45f, yDeck1, etCenterX - etBaseHalfW * 0.14f, yTop)
        sharedPath.lineTo(etCenterX + etBaseHalfW * 0.14f, yTop)
        sharedPath.quadraticBezierTo(etCenterX + etBaseHalfW * 0.45f, yDeck1, etCenterX + etBaseHalfW, baseY)
        sharedPath.lineTo(etCenterX + etBaseHalfW * 0.62f, baseY)
        // Base arch cutout
        sharedPath.quadraticBezierTo(etCenterX, baseY - etH * 0.24f, etCenterX - etBaseHalfW * 0.62f, baseY)
        sharedPath.close()
        drawPath(sharedPath, ironColor)

        // Inner shaft opening between Deck 1 and Deck 2
        sharedPath.reset()
        sharedPath.moveTo(etCenterX - etBaseHalfW * 0.22f, yDeck1)
        sharedPath.lineTo(etCenterX + etBaseHalfW * 0.22f, yDeck1)
        sharedPath.lineTo(etCenterX + etBaseHalfW * 0.10f, yDeck2)
        sharedPath.lineTo(etCenterX - etBaseHalfW * 0.10f, yDeck2)
        sharedPath.close()
        drawPath(sharedPath, Color(0xFF7E8FA6).copy(alpha = 0.55f))

        // Observation Decks 1, 2, and 3
        drawRect(ironHighlight, Offset(etCenterX - etBaseHalfW * 0.68f, yDeck1 - 4f), Size(etBaseHalfW * 1.36f, 8f))
        drawRect(ironHighlight, Offset(etCenterX - etBaseHalfW * 0.42f, yDeck2 - 3.5f), Size(etBaseHalfW * 0.84f, 7f))
        drawRect(ironHighlight, Offset(etCenterX - etBaseHalfW * 0.20f, yTop - 3f), Size(etBaseHalfW * 0.40f, 6f))

        // Cross-lattice truss lines
        for (k in 0..5) {
            val ty = yDeck2 - k * ((yDeck2 - yTop) / 6f)
            val hw = etBaseHalfW * (0.28f - k * 0.025f)
            drawLine(ironHighlight, Offset(etCenterX - hw, ty), Offset(etCenterX + hw, ty - 8f), strokeWidth = 1.5f)
            drawLine(ironHighlight, Offset(etCenterX + hw, ty), Offset(etCenterX - hw, ty - 8f), strokeWidth = 1.5f)
        }
        // Cupola & Beacon Spire
        drawLine(ironColor, Offset(etCenterX, yTop), Offset(etCenterX, baseY - etH), strokeWidth = 3f)
        drawCircle(Color(0xFFFFF59D), radius = 4.5f, center = Offset(etCenterX, baseY - etH))

        // 3. Notre-Dame / Sacré-Cœur Domes (Right landmark)
        val scX = baseX + width * 0.78f
        val scW = width * 0.10f
        val scH = vanishingY * 0.42f
        val domeColor = Color(0xFFEAE2D6)
        drawRect(domeColor, Offset(scX, baseY - scH * 0.55f), Size(scW, scH * 0.55f))
        // Central ovoid dome + flanking domes
        drawOval(domeColor, Offset(scX + scW * 0.28f, baseY - scH), Size(scW * 0.44f, scH * 0.65f))
        drawOval(domeColor, Offset(scX + scW * 0.06f, baseY - scH * 0.8f), Size(scW * 0.24f, scH * 0.45f))
        drawOval(domeColor, Offset(scX + scW * 0.70f, baseY - scH * 0.8f), Size(scW * 0.24f, scH * 0.45f))
    }

    private fun DrawScope.drawTokyoSkyline(baseX: Float, baseY: Float, width: Float, vanishingY: Float) {
        // High-density Shinjuku/Shibuya skyscrapers with neon signage
        val towerX = floatArrayOf(0.03f, 0.12f, 0.22f, 0.34f, 0.54f, 0.63f, 0.86f)
        val towerH = floatArrayOf(0.50f, 0.64f, 0.45f, 0.58f, 0.62f, 0.48f, 0.55f)
        val neonColors = arrayOf(Color(0xFF00E5FF), Color(0xFFFF1744), Color(0xFF00E676), Color(0xFFFFEA00))

        for (i in towerX.indices) {
            val tx = baseX + width * towerX[i]
            val tw = width * 0.075f
            val th = vanishingY * towerH[i]
            drawSilhouetteTower(
                x = tx,
                baseY = baseY,
                w = tw,
                h = th,
                color = Color(0xFF15192B),
                windowColor = Color(0xFF80DEEA).copy(alpha = 0.6f),
                seed = i + 50
            )
            // Rooftop neon billboard
            val nColor = neonColors[i % neonColors.size]
            drawRect(
                color = nColor.copy(alpha = 0.85f),
                topLeft = Offset(tx + tw * 0.15f, baseY - th * 0.86f),
                size = Size(tw * 0.7f, th * 0.08f)
            )
        }

        // Tokyo Tower (Iconic international orange & white lattice tower)
        val ttCenterX = baseX + width * 0.76f
        val ttHalfW = width * 0.065f
        val ttH = vanishingY * 0.88f
        val redOrange = Color(0xFFE53935)
        val crispWhite = Color(0xFFF5F5F5)

        val bands = 6
        for (b in 0 until bands) {
            val t0 = b / bands.toFloat()
            val t1 = (b + 1) / bands.toFloat()
            val y0 = baseY - ttH * 0.82f * t0
            val y1 = baseY - ttH * 0.82f * t1
            val w0 = ttHalfW * (1f - 0.78f * t0)
            val w1 = ttHalfW * (1f - 0.78f * t1)
            sharedPath.reset()
            sharedPath.moveTo(ttCenterX - w0, y0)
            sharedPath.lineTo(ttCenterX + w0, y0)
            sharedPath.lineTo(ttCenterX + w1, y1)
            sharedPath.lineTo(ttCenterX - w1, y1)
            sharedPath.close()
            drawPath(sharedPath, if (b % 2 == 0) redOrange else crispWhite)
        }
        // Main observation deck
        drawRect(crispWhite, Offset(ttCenterX - ttHalfW * 0.65f, baseY - ttH * 0.34f), Size(ttHalfW * 1.3f, vanishingY * 0.045f))
        // Top antenna spire
        drawLine(redOrange, Offset(ttCenterX, baseY - ttH * 0.82f), Offset(ttCenterX, baseY - ttH), strokeWidth = 3.5f)
        drawCircle(Color(0xFFFF5252), radius = 4f, center = Offset(ttCenterX, baseY - ttH))
    }

    private fun DrawScope.drawLondonSkyline(baseX: Float, baseY: Float, width: Float, vanishingY: Float) {
        // 1. The Shard (Tapered glass pyramidal tower)
        val shardX = baseX + width * 0.08f
        val shardW = width * 0.085f
        val shardH = vanishingY * 0.82f
        sharedPath.reset()
        sharedPath.moveTo(shardX, baseY)
        sharedPath.lineTo(shardX + shardW, baseY)
        sharedPath.lineTo(shardX + shardW * 0.56f, baseY - shardH)
        sharedPath.lineTo(shardX + shardW * 0.44f, baseY - shardH)
        sharedPath.close()
        drawPath(
            sharedPath,
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF607D8B), Color(0xFFB0BEC5), Color(0xFF37474F)),
                startX = shardX,
                endX = shardX + shardW
            )
        )

        // 2. Elizabeth Tower (Big Ben) & Houses of Parliament
        val bbX = baseX + width * 0.26f
        val bbW = width * 0.058f
        val bbH = vanishingY * 0.72f
        // Parliament wing
        drawRect(Color(0xFF7D6E57), Offset(bbX - width * 0.08f, baseY - vanishingY * 0.28f), Size(width * 0.09f, vanishingY * 0.28f))
        // Big Ben shaft
        drawRect(Color(0xFF8D7B62), Offset(bbX, baseY - bbH * 0.74f), Size(bbW, bbH * 0.74f))
        // Belfry & Gothic spire
        sharedPath.reset()
        sharedPath.moveTo(bbX - bbW * 0.06f, baseY - bbH * 0.74f)
        sharedPath.lineTo(bbX + bbW * 1.06f, baseY - bbH * 0.74f)
        sharedPath.lineTo(bbX + bbW * 0.5f, baseY - bbH)
        sharedPath.close()
        drawPath(sharedPath, Color(0xFF37474F))
        // Illuminated Golden Clock Face & Hands
        val clockCenter = Offset(bbX + bbW * 0.5f, baseY - bbH * 0.62f)
        val clockR = bbW * 0.34f
        drawRect(Color(0xFFD4AF37), Offset(bbX + bbW * 0.08f, clockCenter.y - clockR * 1.2f), Size(bbW * 0.84f, clockR * 2.4f))
        drawCircle(Color(0xFFFFF9C4), radius = clockR, center = clockCenter)
        drawLine(Color.Black, clockCenter, Offset(clockCenter.x, clockCenter.y - clockR * 0.7f), strokeWidth = 2f)
        drawLine(Color.Black, clockCenter, Offset(clockCenter.x + clockR * 0.5f, clockCenter.y), strokeWidth = 2f)

        // 3. Tower Bridge (Twin Gothic towers & high-level walkways)
        val tbX = baseX + width * 0.45f
        val tbSpan = width * 0.18f
        val tW = width * 0.036f
        val tH = vanishingY * 0.46f
        // Twin towers
        drawRect(Color(0xFF8D8478), Offset(tbX, baseY - tH), Size(tW, tH))
        drawRect(Color(0xFF8D8478), Offset(tbX + tbSpan, baseY - tH), Size(tW, tH))
        // Upper walkways & lower bascule deck
        drawLine(Color(0xFF546E7A), Offset(tbX + tW, baseY - tH * 0.78f), Offset(tbX + tbSpan, baseY - tH * 0.78f), strokeWidth = 5f)
        drawLine(Color(0xFF29B6F6), Offset(tbX - width * 0.04f, baseY - tH * 0.25f), Offset(tbX + tbSpan + tW + width * 0.04f, baseY - tH * 0.25f), strokeWidth = 4f)

        // 4. London Eye (Cantilevered observation wheel with passenger pods)
        val eyeCenter = Offset(baseX + width * 0.80f, baseY - vanishingY * 0.42f)
        val eyeR = vanishingY * 0.34f
        drawLine(Color(0xFFCFD8DC), Offset(eyeCenter.x - eyeR * 0.35f, baseY), eyeCenter, strokeWidth = 5f)
        drawLine(Color(0xFFCFD8DC), Offset(eyeCenter.x + eyeR * 0.35f, baseY), eyeCenter, strokeWidth = 5f)
        drawCircle(Color(0xFFECEFF1), radius = eyeR, center = eyeCenter, style = Stroke(width = 4f))
        drawCircle(Color(0xFFB0BEC5), radius = eyeR * 0.88f, center = eyeCenter, style = Stroke(width = 1.5f))
        for (p in 0 until 12) {
            val angle = (p * 30f) * (kotlin.math.PI.toFloat() / 180f)
            val px = eyeCenter.x + cos(angle) * eyeR
            val py = eyeCenter.y + sin(angle) * eyeR
            drawLine(Color(0xFFB0BEC5).copy(alpha = 0.5f), eyeCenter, Offset(px, py), strokeWidth = 1f)
            drawOval(Color(0xFFE0F7FA), Offset(px - 4.5f, py - 3f), Size(9f, 6f))
        }
    }

    private fun DrawScope.drawCairoSkyline(baseX: Float, baseY: Float, width: Float, vanishingY: Float) {
        // 1. Great Pyramids of Giza (3D sunlit and shaded limestone faces)
        val pyramids = arrayOf(
            Triple(0.14f, 0.38f, 0.68f), // Khufu
            Triple(0.42f, 0.34f, 0.58f), // Khafre
            Triple(0.68f, 0.22f, 0.38f)  // Menkaure
        )
        for ((xStart, wRatio, hRatio) in pyramids) {
            val px = baseX + width * xStart
            val pw = width * wRatio
            val ph = vanishingY * hRatio
            val apexX = px + pw * 0.46f
            val apexY = baseY - ph

            // Sunlit left face
            sharedPath.reset()
            sharedPath.moveTo(px, baseY)
            sharedPath.lineTo(px + pw * 0.56f, baseY)
            sharedPath.lineTo(apexX, apexY)
            sharedPath.close()
            drawPath(sharedPath, Color(0xFFE0A96D))

            // Shadowed right face
            sharedPath.reset()
            sharedPath.moveTo(px + pw * 0.56f, baseY)
            sharedPath.lineTo(px + pw, baseY)
            sharedPath.lineTo(apexX, apexY)
            sharedPath.close()
            drawPath(sharedPath, Color(0xFF9C6635))
        }

        // 2. Mosque of Muhammad Ali / Citadel Domes & Minarets
        val mX = baseX + width * 0.84f
        val mW = width * 0.11f
        val mH = vanishingY * 0.36f
        val stone = Color(0xFFC48B52)
        drawRect(stone, Offset(mX, baseY - mH * 0.55f), Size(mW, mH * 0.55f))
        drawOval(stone, Offset(mX + mW * 0.2f, baseY - mH), Size(mW * 0.6f, mH * 0.7f))
        // Twin slender pencil minarets
        drawLine(stone, Offset(mX - width * 0.01f, baseY), Offset(mX - width * 0.01f, baseY - mH * 1.35f), strokeWidth = 4f)
        drawLine(stone, Offset(mX + mW + width * 0.01f, baseY), Offset(mX + mW + width * 0.01f, baseY - mH * 1.35f), strokeWidth = 4f)
    }

    private fun DrawScope.drawSydneySkyline(baseX: Float, baseY: Float, width: Float, vanishingY: Float) {
        // CBD glass skyscrapers & Sydney Tower Eye on left
        val cbdSlots = floatArrayOf(0.03f, 0.11f, 0.19f)
        val cbdHeights = floatArrayOf(0.46f, 0.58f, 0.42f)
        for (i in cbdSlots.indices) {
            drawSilhouetteTower(
                x = baseX + width * cbdSlots[i],
                baseY = baseY,
                w = width * 0.065f,
                h = vanishingY * cbdHeights[i],
                color = Color(0xFF1E4E68),
                windowColor = Color(0xFFB3E5FC).copy(alpha = 0.6f),
                seed = i + 80
            )
        }
        // Sydney Tower Eye
        val stX = baseX + width * 0.142f
        val stTop = baseY - vanishingY * 0.78f
        drawLine(Color(0xFFCFD8DC), Offset(stX, baseY - vanishingY * 0.58f), Offset(stX, stTop - vanishingY * 0.12f), strokeWidth = 3.5f)
        drawOval(Color(0xFFFFB300), Offset(stX - width * 0.022f, stTop), Size(width * 0.044f, vanishingY * 0.055f))

        // 1. Sydney Opera House (Iconic interlocking white vaulted shells on granite podium)
        val ohX = baseX + width * 0.29f
        val ohW = width * 0.24f
        val podiumH = vanishingY * 0.08f
        drawRect(Color(0xFF8D7B68), Offset(ohX - width * 0.01f, baseY - podiumH), Size(ohW, podiumH))

        val shellBaseY = baseY - podiumH
        val shellSpecs = arrayOf(
            Triple(0.00f, 0.09f, 0.34f),
            Triple(0.06f, 0.11f, 0.44f),
            Triple(0.14f, 0.09f, 0.30f)
        )
        for ((offsetR, widthR, heightR) in shellSpecs) {
            val sx = ohX + width * offsetR
            val sw = width * widthR
            val sh = vanishingY * heightR
            sharedPath.reset()
            sharedPath.moveTo(sx, shellBaseY)
            sharedPath.quadraticBezierTo(sx + sw * 0.18f, shellBaseY - sh, sx + sw * 0.85f, shellBaseY - sh * 0.88f)
            sharedPath.quadraticBezierTo(sx + sw * 0.62f, shellBaseY - sh * 0.32f, sx + sw, shellBaseY)
            sharedPath.close()
            drawPath(sharedPath, Color(0xFFFDFBF7))
            // Inner shadowed vault rib
            sharedPath.reset()
            sharedPath.moveTo(sx + sw * 0.45f, shellBaseY)
            sharedPath.quadraticBezierTo(sx + sw * 0.65f, shellBaseY - sh * 0.55f, sx + sw * 0.85f, shellBaseY - sh * 0.88f)
            sharedPath.lineTo(sx + sw, shellBaseY)
            sharedPath.close()
            drawPath(sharedPath, Color(0xFFD7CCC8))
        }

        // 2. Sydney Harbour Bridge ("The Coathanger" steel through-arch + stone pylons)
        val brX = baseX + width * 0.58f
        val brW = width * 0.36f
        val deckY = baseY - vanishingY * 0.18f
        val archColor = Color(0xFF37474F)

        // Upper & Lower parabolic steel arch chords
        sharedPath.reset()
        sharedPath.moveTo(brX, baseY)
        sharedPath.quadraticBezierTo(brX + brW * 0.5f, baseY - vanishingY * 0.72f, brX + brW, baseY)
        drawPath(sharedPath, archColor, style = Stroke(width = 4.5f))

        sharedPath.reset()
        sharedPath.moveTo(brX, deckY)
        sharedPath.quadraticBezierTo(brX + brW * 0.5f, baseY - vanishingY * 0.56f, brX + brW, deckY)
        drawPath(sharedPath, archColor, style = Stroke(width = 3.5f))

        // Vertical steel hangers
        for (h in 1..9) {
            val t = h / 10f
            val hx = brX + brW * t
            val archY = baseY - 4f * t * (1f - t) * (vanishingY * 0.58f)
            drawLine(archColor.copy(alpha = 0.75f), Offset(hx, archY), Offset(hx, deckY), strokeWidth = 1.8f)
        }
        // Bridge road deck
        drawLine(archColor, Offset(brX - width * 0.02f, deckY), Offset(brX + brW + width * 0.02f, deckY), strokeWidth = 5f)
        // Twin granite pylons at each end
        val pylonW = width * 0.032f
        val pylonH = vanishingY * 0.34f
        drawRect(Color(0xFF78716A), Offset(brX - pylonW * 0.3f, baseY - pylonH), Size(pylonW, pylonH))
        drawRect(Color(0xFF78716A), Offset(brX + brW - pylonW * 0.7f, baseY - pylonH), Size(pylonW, pylonH))
    }
}
