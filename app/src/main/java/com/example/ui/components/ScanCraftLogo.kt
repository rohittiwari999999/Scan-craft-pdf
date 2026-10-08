package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Brand Colors for the Logo
private val LogoRoyalBlue = Color(0xFF2563EB)
private val LogoCyanGlow = Color(0xFF38BDF8)
private val LogoPdfRedStart = Color(0xFFEF4444)
private val LogoPdfRedEnd = Color(0xFFDC2626)

/**
 * Animated/Styled Scanner Emblem:
 * Layered document with a moving laser scan beam.
 */
@Composable
fun ScanEmblem(
    size: Dp = 38.dp,
    animated: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_beam")
    val beamProgress by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "beam_pos"
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }

    Box(
        modifier = Modifier
            .size(size)
            .shadow(4.dp, RoundedCornerShape(size * 0.26f))
            .clip(RoundedCornerShape(size * 0.26f))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E3A8A), // Deep Blue
                        Color(0xFF0F172A)  // Slate Navy
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF60A5FA), Color(0xFF1E293B))
                ),
                shape = RoundedCornerShape(size * 0.26f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(size * 0.18f)) {
            val w = this.size.width
            val h = this.size.height

            // 1. Draw Document Shape with folded top-right corner
            val docPath = Path().apply {
                moveTo(0f, 0f)
                lineTo(w * 0.65f, 0f)
                lineTo(w, h * 0.35f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = docPath,
                color = Color.White.copy(alpha = 0.95f)
            )

            // 2. Corner fold flap
            val foldPath = Path().apply {
                moveTo(w * 0.65f, 0f)
                lineTo(w * 0.65f, h * 0.35f)
                lineTo(w, h * 0.35f)
                close()
            }
            drawPath(
                path = foldPath,
                color = Color(0xFFCBD5E1) // Slate light
            )

            // 3. Document text lines inside
            val linePaint = Color(0xFF94A3B8)
            val strokeW = (h * 0.06f).coerceAtLeast(1.5f)
            drawLine(linePaint, Offset(w * 0.18f, h * 0.42f), Offset(w * 0.75f, h * 0.42f), strokeWidth = strokeW)
            drawLine(linePaint, Offset(w * 0.18f, h * 0.58f), Offset(w * 0.65f, h * 0.58f), strokeWidth = strokeW)
            drawLine(linePaint, Offset(w * 0.18f, h * 0.74f), Offset(w * 0.82f, h * 0.74f), strokeWidth = strokeW)

            // 4. Glowing Cyan Laser Scan Beam
            val beamY = h * beamProgress
            // Soft laser glow halo
            drawLine(
                color = LogoCyanGlow.copy(alpha = 0.45f),
                start = Offset(-w * 0.1f, beamY),
                end = Offset(w * 1.1f, beamY),
                strokeWidth = (h * 0.14f)
            )
            // Sharp center laser core
            drawLine(
                color = Color.White,
                start = Offset(-w * 0.05f, beamY),
                end = Offset(w * 1.05f, beamY),
                strokeWidth = (h * 0.05f).coerceAtLeast(1.5f)
            )
        }
    }
}

/**
 * Stylish Red/Coral PDF Badge Pill
 */
@Composable
fun PdfBadge(
    fontSize: Float = 11f,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        shadowElevation = 2.dp,
        color = Color.Transparent,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(LogoPdfRedStart, LogoPdfRedEnd)
                    )
                )
                .padding(horizontal = 7.dp, vertical = 2.5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "PDF",
                color = Color.White,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

/**
 * Full Logo-Style Header Component for ScanCraft PDF
 * Features:
 * - Geometric Glowing Laser Emblem
 * - Two-Tone Wordmark: "Scan" (Electric Blue) + "Craft" (Dark Slate / Bright White)
 * - Distinctive Red [PDF] Pill Badge
 * - Clean Subtitle with tracking
 */
@Composable
fun ScanCraftLogo(
    modifier: Modifier = Modifier,
    isLargeHero: Boolean = false,
    showSubtitle: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isLargeHero) 14.dp else 10.dp)
    ) {
        // Emblem Icon
        ScanEmblem(
            size = if (isLargeHero) 48.dp else 36.dp,
            animated = true
        )

        // Wordmark + Badge Column
        Column(
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Wordmark: "Scan" + "Craft"
                val textColorCraft = MaterialTheme.colorScheme.onSurface
                val wordmark = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            color = LogoRoyalBlue,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                    ) {
                        append("Scan")
                    }
                    withStyle(
                        style = SpanStyle(
                            color = textColorCraft,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.2).sp
                        )
                    ) {
                        append("Craft")
                    }
                }

                Text(
                    text = wordmark,
                    fontSize = if (isLargeHero) 24.sp else 18.sp,
                    fontFamily = FontFamily.SansSerif
                )

                // Distinct Red PDF Badge
                PdfBadge(
                    fontSize = if (isLargeHero) 11.5f else 9.5f
                )
            }

            if (showSubtitle) {
                Text(
                    text = if (isLargeHero) "HIGH-PRECISION DOCUMENT SCANNER" else "SMART DOCUMENT SCANNER",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (isLargeHero) 9.sp else 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}
