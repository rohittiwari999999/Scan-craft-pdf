package com.example.ui.crop

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DocumentFilter
import com.example.model.ScanSource
import com.example.util.ImageProcessing
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("Free", null),
    A4("A4 (1:1.41)", 1f / 1.4142f),
    LETTER("Letter (8.5:11)", 8.5f / 11f),
    SQUARE("1:1", 1f)
}

private enum class DragHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    EDGE_LEFT, EDGE_TOP, EDGE_RIGHT, EDGE_BOTTOM, INSIDE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    initialBitmap: Bitmap,
    pageNumber: Int = 1,
    lastUsedSource: ScanSource = ScanSource.CAMERA,
    onBack: () -> Unit,
    onProceedToPreview: (processedBitmap: Bitmap) -> Unit,
    onScanNextPage: (processedBitmap: Bitmap) -> Unit
) {
    BackHandler { onBack() }

    var baseBitmap by remember { mutableStateOf(initialBitmap) }
    var selectedFilter by remember { mutableStateOf(DocumentFilter.MAGIC_COLOR) }
    var selectedAspect by remember { mutableStateOf(CropAspectRatio.FREE) }

    // Filtered preview cache
    val filteredBitmap = remember(baseBitmap, selectedFilter) {
        ImageProcessing.applyFilter(baseBitmap, selectedFilter)
    }

    // Normalized crop rectangle: 0.0 .. 1.0
    var cropLeft by remember { mutableFloatStateOf(0.05f) }
    var cropTop by remember { mutableFloatStateOf(0.05f) }
    var cropRight by remember { mutableFloatStateOf(0.95f) }
    var cropBottom by remember { mutableFloatStateOf(0.95f) }

    var activeDragHandle by remember { mutableStateOf(DragHandle.NONE) }

    // Helper to constrain aspect ratio if non-free
    fun applyAspectRatio(aspect: Float?, imageW: Float, imageH: Float) {
        if (aspect == null) return
        val currentW = cropRight - cropLeft
        val targetHNorm = (currentW * imageW) / (aspect * imageH)
        val centerY = (cropTop + cropBottom) / 2f
        val newHalfH = targetHNorm / 2f
        if (centerY - newHalfH >= 0f && centerY + newHalfH <= 1f) {
            cropTop = centerY - newHalfH
            cropBottom = centerY + newHalfH
        } else {
            val clampedH = min(1f, targetHNorm)
            cropTop = 0f
            cropBottom = clampedH
            val newWNorm = (clampedH * imageH * aspect) / imageW
            val centerX = (cropLeft + cropRight) / 2f
            cropLeft = max(0f, centerX - newWNorm / 2f)
            cropRight = min(1f, centerX + newWNorm / 2f)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Page $pageNumber • Crop & Enhance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap '+ Next Photo' to scan more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("crop_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            cropLeft = 0.05f
                            cropTop = 0.05f
                            cropRight = 0.95f
                            cropBottom = 0.95f
                        },
                        modifier = Modifier.testTag("crop_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Crop"
                        )
                    }

                    // Directly scans/picks next photo without having to go through preview!
                    FilledTonalButton(
                        onClick = {
                            val rectF = RectF(cropLeft, cropTop, cropRight, cropBottom)
                            val croppedRaw = ImageProcessing.cropBitmapNormalized(baseBitmap, rectF)
                            val finalEnhanced = ImageProcessing.applyFilter(croppedRaw, selectedFilter)
                            onScanNextPage(finalEnhanced)
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("crop_scan_next_button")
                    ) {
                        Icon(
                            imageVector = if (lastUsedSource == ScanSource.CAMERA) Icons.Default.CameraAlt else Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (lastUsedSource == ScanSource.CAMERA) "+ Next" else "+ Pick",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Done / Proceed to PDF Preview
                    Button(
                        onClick = {
                            val rectF = RectF(cropLeft, cropTop, cropRight, cropBottom)
                            val croppedRaw = ImageProcessing.cropBitmapNormalized(baseBitmap, rectF)
                            val finalEnhanced = ImageProcessing.applyFilter(croppedRaw, selectedFilter)
                            onProceedToPreview(finalEnhanced)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .testTag("crop_done_button")
                    ) {
                        Text("Preview", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Filter selection row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enhance Filter",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Quick Rotation controls
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    baseBitmap = ImageProcessing.rotateBitmap(baseBitmap, -90f)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("rotate_ccw_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Rotate90DegreesCcw,
                                    contentDescription = "Rotate CCW",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    baseBitmap = ImageProcessing.rotateBitmap(baseBitmap, 90f)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("rotate_cw_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Rotate90DegreesCw,
                                    contentDescription = "Rotate CW",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Filter chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DocumentFilter.values().forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.displayName, fontSize = 12.sp) },
                                modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Aspect ratio chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ratio:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CropAspectRatio.values().forEach { aspect ->
                            val isSelected = selectedAspect == aspect
                            FilledTonalButton(
                                onClick = {
                                    selectedAspect = aspect
                                    applyAspectRatio(
                                        aspect.ratio,
                                        baseBitmap.width.toFloat(),
                                        baseBitmap.height.toFloat()
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(aspect.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF121418)),
            contentAlignment = Alignment.Center
        ) {
            val containerWidthPx = constraints.maxWidth.toFloat()
            val containerHeightPx = constraints.maxHeight.toFloat()

            val imgW = filteredBitmap.width.toFloat()
            val imgH = filteredBitmap.height.toFloat()
            val imgAspect = imgW / imgH
            val containerAspect = containerWidthPx / containerHeightPx

            val displayedW: Float
            val displayedH: Float
            val offsetX: Float
            val offsetY: Float

            // Fit image inside container with 16dp margins
            val maxDrawW = containerWidthPx - 32f
            val maxDrawH = containerHeightPx - 32f
            val drawAspect = maxDrawW / maxDrawH

            if (imgAspect > drawAspect) {
                displayedW = maxDrawW
                displayedH = maxDrawW / imgAspect
            } else {
                displayedH = maxDrawH
                displayedW = maxDrawH * imgAspect
            }

            offsetX = (containerWidthPx - displayedW) / 2f
            offsetY = (containerHeightPx - displayedH) / 2f

            // Handle touch points
            val handleRadiusPx = with(LocalDensity.current) { 26.dp.toPx() }
            val hitRadiusPx = with(LocalDensity.current) { 38.dp.toPx() }

            val cropRectDisplayLeft = offsetX + (cropLeft * displayedW)
            val cropRectDisplayTop = offsetY + (cropTop * displayedH)
            val cropRectDisplayRight = offsetX + (cropRight * displayedW)
            val cropRectDisplayBottom = offsetY + (cropBottom * displayedH)

            // Canvas drawing both image, dim overlay, grid, and pins
            val imageBitmap = remember(filteredBitmap) { filteredBitmap.asImageBitmap() }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(displayedW, displayedH, offsetX, offsetY) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val x = startOffset.x
                                val y = startOffset.y

                                val dTL = Offset(cropRectDisplayLeft, cropRectDisplayTop)
                                val dTR = Offset(cropRectDisplayRight, cropRectDisplayTop)
                                val dBL = Offset(cropRectDisplayLeft, cropRectDisplayBottom)
                                val dBR = Offset(cropRectDisplayRight, cropRectDisplayBottom)

                                activeDragHandle = when {
                                    (startOffset - dTL).getDistance() <= hitRadiusPx -> DragHandle.TOP_LEFT
                                    (startOffset - dTR).getDistance() <= hitRadiusPx -> DragHandle.TOP_RIGHT
                                    (startOffset - dBL).getDistance() <= hitRadiusPx -> DragHandle.BOTTOM_LEFT
                                    (startOffset - dBR).getDistance() <= hitRadiusPx -> DragHandle.BOTTOM_RIGHT
                                    abs(x - cropRectDisplayLeft) <= hitRadiusPx && y in cropRectDisplayTop..cropRectDisplayBottom -> DragHandle.EDGE_LEFT
                                    abs(x - cropRectDisplayRight) <= hitRadiusPx && y in cropRectDisplayTop..cropRectDisplayBottom -> DragHandle.EDGE_RIGHT
                                    abs(y - cropRectDisplayTop) <= hitRadiusPx && x in cropRectDisplayLeft..cropRectDisplayRight -> DragHandle.EDGE_TOP
                                    abs(y - cropRectDisplayBottom) <= hitRadiusPx && x in cropRectDisplayLeft..cropRectDisplayRight -> DragHandle.EDGE_BOTTOM
                                    x in cropRectDisplayLeft..cropRectDisplayRight && y in cropRectDisplayTop..cropRectDisplayBottom -> DragHandle.INSIDE
                                    else -> DragHandle.NONE
                                }
                            },
                            onDragEnd = {
                                activeDragHandle = DragHandle.NONE
                            },
                            onDragCancel = {
                                activeDragHandle = DragHandle.NONE
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dxNorm = dragAmount.x / displayedW
                                val dyNorm = dragAmount.y / displayedH
                                val minSpan = 0.12f

                                when (activeDragHandle) {
                                    DragHandle.TOP_LEFT -> {
                                        cropLeft = (cropLeft + dxNorm).coerceIn(0f, cropRight - minSpan)
                                        cropTop = (cropTop + dyNorm).coerceIn(0f, cropBottom - minSpan)
                                    }
                                    DragHandle.TOP_RIGHT -> {
                                        cropRight = (cropRight + dxNorm).coerceIn(cropLeft + minSpan, 1f)
                                        cropTop = (cropTop + dyNorm).coerceIn(0f, cropBottom - minSpan)
                                    }
                                    DragHandle.BOTTOM_LEFT -> {
                                        cropLeft = (cropLeft + dxNorm).coerceIn(0f, cropRight - minSpan)
                                        cropBottom = (cropBottom + dyNorm).coerceIn(cropTop + minSpan, 1f)
                                    }
                                    DragHandle.BOTTOM_RIGHT -> {
                                        cropRight = (cropRight + dxNorm).coerceIn(cropLeft + minSpan, 1f)
                                        cropBottom = (cropBottom + dyNorm).coerceIn(cropTop + minSpan, 1f)
                                    }
                                    DragHandle.EDGE_LEFT -> {
                                        cropLeft = (cropLeft + dxNorm).coerceIn(0f, cropRight - minSpan)
                                    }
                                    DragHandle.EDGE_RIGHT -> {
                                        cropRight = (cropRight + dxNorm).coerceIn(cropLeft + minSpan, 1f)
                                    }
                                    DragHandle.EDGE_TOP -> {
                                        cropTop = (cropTop + dyNorm).coerceIn(0f, cropBottom - minSpan)
                                    }
                                    DragHandle.EDGE_BOTTOM -> {
                                        cropBottom = (cropBottom + dyNorm).coerceIn(cropTop + minSpan, 1f)
                                    }
                                    DragHandle.INSIDE -> {
                                        val widthSpan = cropRight - cropLeft
                                        val heightSpan = cropBottom - cropTop
                                        var newLeft = cropLeft + dxNorm
                                        var newTop = cropTop + dyNorm

                                        if (newLeft < 0f) newLeft = 0f
                                        if (newLeft + widthSpan > 1f) newLeft = 1f - widthSpan
                                        if (newTop < 0f) newTop = 0f
                                        if (newTop + heightSpan > 1f) newTop = 1f - heightSpan

                                        cropLeft = newLeft
                                        cropRight = newLeft + widthSpan
                                        cropTop = newTop
                                        cropBottom = newTop + heightSpan
                                    }
                                    DragHandle.NONE -> {}
                                }
                            }
                        )
                    }
            ) {
                // 1. Draw image centered
                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                    dstSize = IntSize(displayedW.toInt(), displayedH.toInt())
                )

                // 2. Dim areas outside crop rect
                val overlayColor = Color(0x99000000)
                // Top
                drawRect(
                    color = overlayColor,
                    topLeft = Offset(offsetX, offsetY),
                    size = Size(displayedW, cropRectDisplayTop - offsetY)
                )
                // Bottom
                drawRect(
                    color = overlayColor,
                    topLeft = Offset(offsetX, cropRectDisplayBottom),
                    size = Size(displayedW, (offsetY + displayedH) - cropRectDisplayBottom)
                )
                // Left
                drawRect(
                    color = overlayColor,
                    topLeft = Offset(offsetX, cropRectDisplayTop),
                    size = Size(cropRectDisplayLeft - offsetX, cropRectDisplayBottom - cropRectDisplayTop)
                )
                // Right
                drawRect(
                    color = overlayColor,
                    topLeft = Offset(cropRectDisplayRight, cropRectDisplayTop),
                    size = Size((offsetX + displayedW) - cropRectDisplayRight, cropRectDisplayBottom - cropRectDisplayTop)
                )

                // 3. Draw crop box border
                val cropBoxW = cropRectDisplayRight - cropRectDisplayLeft
                val cropBoxH = cropRectDisplayBottom - cropRectDisplayTop
                drawRect(
                    color = Color.White,
                    topLeft = Offset(cropRectDisplayLeft, cropRectDisplayTop),
                    size = Size(cropBoxW, cropBoxH),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                )

                // 4. Draw rule of thirds subtle grid lines inside crop box
                val gridColor = Color(0x66FFFFFF)
                val thirdW = cropBoxW / 3f
                val thirdH = cropBoxH / 3f

                drawLine(gridColor, Offset(cropRectDisplayLeft + thirdW, cropRectDisplayTop), Offset(cropRectDisplayLeft + thirdW, cropRectDisplayBottom), strokeWidth = 1f)
                drawLine(gridColor, Offset(cropRectDisplayLeft + thirdW * 2, cropRectDisplayTop), Offset(cropRectDisplayLeft + thirdW * 2, cropRectDisplayBottom), strokeWidth = 1f)
                drawLine(gridColor, Offset(cropRectDisplayLeft, cropRectDisplayTop + thirdH), Offset(cropRectDisplayRight, cropRectDisplayTop + thirdH), strokeWidth = 1f)
                drawLine(gridColor, Offset(cropRectDisplayLeft, cropRectDisplayTop + thirdH * 2), Offset(cropRectDisplayRight, cropRectDisplayTop + thirdH * 2), strokeWidth = 1f)

                // 5. Draw 4 bright corner handles with glowing cyan/accent color
                val pinColor = Color(0xFF38BDF8) // Sky blue / cyan accent
                val corners = listOf(
                    Offset(cropRectDisplayLeft, cropRectDisplayTop),
                    Offset(cropRectDisplayRight, cropRectDisplayTop),
                    Offset(cropRectDisplayLeft, cropRectDisplayBottom),
                    Offset(cropRectDisplayRight, cropRectDisplayBottom)
                )

                corners.forEach { corner ->
                    // Outer circle
                    drawCircle(Color.White, radius = 18f, center = corner)
                    // Inner colored dot
                    drawCircle(pinColor, radius = 12f, center = corner)
                }

                // Edge indicators (small pills on edges)
                val edgeColor = Color.White
                drawCircle(edgeColor, radius = 8f, center = Offset((cropRectDisplayLeft + cropRectDisplayRight) / 2f, cropRectDisplayTop))
                drawCircle(edgeColor, radius = 8f, center = Offset((cropRectDisplayLeft + cropRectDisplayRight) / 2f, cropRectDisplayBottom))
                drawCircle(edgeColor, radius = 8f, center = Offset(cropRectDisplayLeft, (cropRectDisplayTop + cropRectDisplayBottom) / 2f))
                drawCircle(edgeColor, radius = 8f, center = Offset(cropRectDisplayRight, (cropRectDisplayTop + cropRectDisplayBottom) / 2f))
            }
        }
    }
}
