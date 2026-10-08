package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import com.example.model.DocumentFilter
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageProcessing {

    /**
     * Loads a bitmap from a content URI with maximum dimension downsampling for performance.
     */
    fun loadBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var inSampleSize = 1
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= maxDimension && halfWidth / inSampleSize >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Rotates a bitmap by a given angle in degrees.
     */
    fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        if (angle % 360 == 0f) return source
        val matrix = Matrix().apply { postRotate(angle) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Crops the bitmap according to normalized coordinates (0.0 .. 1.0).
     */
    fun cropBitmapNormalized(source: Bitmap, cropRectNorm: RectF): Bitmap {
        val left = (cropRectNorm.left * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (cropRectNorm.top * source.height).toInt().coerceIn(0, source.height - 1)
        val right = (cropRectNorm.right * source.width).toInt().coerceIn(left + 1, source.width)
        val bottom = (cropRectNorm.bottom * source.height).toInt().coerceIn(top + 1, source.height)

        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)

        return Bitmap.createBitmap(source, left, top, width, height)
    }

    /**
     * Applies document filters (Original, Magic Color, B&W Doc, Grayscale).
     */
    fun applyFilter(source: Bitmap, filter: DocumentFilter): Bitmap {
        return when (filter) {
            DocumentFilter.ORIGINAL -> source
            DocumentFilter.GRAYSCALE -> applyGrayscaleDocument(source)
            DocumentFilter.MAGIC_COLOR -> applyMagicColor(source)
            DocumentFilter.BW_DOCUMENT -> applyBwDocument(source)
        }
    }

    /**
     * Pro-Grade "Magic Color" Document Scanner Algorithm (CamScanner / Adobe Scan style):
     * 1. Dynamic background paper tone estimation (removes shadows, yellow/gray camera tint).
     * 2. Bleaches background paper to clean crisp white.
     * 3. Enhances text ink contrast so black text becomes deep and sharp.
     * 4. Preserves and boosts vibrant color in signatures, stamps, badges, and charts.
     * 5. Applies an unsharp mask edge-sharpening pass for laser-crisp document clarity.
     */
    fun applyMagicColor(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val totalPixels = width * height
        if (totalPixels <= 0) return source

        val pixels = IntArray(totalPixels)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Build fast luminance histogram on stride to detect background paper & ink
        val histogram = IntArray(256)
        val stride = max(1, totalPixels / 40000)
        var sampledCount = 0
        for (i in 0 until totalPixels step stride) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
            histogram[lum]++
            sampledCount++
        }

        // 2. Determine paper white point (approx 88th percentile) and dark ink point (10th percentile)
        var cumulative = 0
        var darkPoint = 40
        var whitePoint = 210

        val targetDark = (sampledCount * 0.08f).toInt()
        val targetWhite = (sampledCount * 0.88f).toInt()

        for (i in 0..255) {
            cumulative += histogram[i]
            if (cumulative >= targetDark && darkPoint == 40) {
                darkPoint = i.coerceIn(15, 80)
            }
            if (cumulative >= targetWhite) {
                whitePoint = i.coerceIn(170, 245)
                break
            }
        }

        if (whitePoint <= darkPoint + 20) {
            whitePoint = 220
            darkPoint = 45
        }

        // 3. Build adaptive contrast & whitening Look-Up Table
        val range = (whitePoint - darkPoint).toFloat()
        val lut = IntArray(256)
        for (i in 0..255) {
            when {
                i >= whitePoint -> {
                    // Bleach background to crisp white
                    lut[i] = 255
                }
                i <= darkPoint -> {
                    // Deepen dark ink strokes
                    lut[i] = (i * 0.4f).toInt().coerceIn(0, 255)
                }
                else -> {
                    // Smooth S-curve contrast transition between text and paper
                    val norm = (i - darkPoint) / range
                    val curved = (norm * norm * (3f - 2f * norm)) // Smooth Hermite curve
                    val res = (curved * 255f).roundToInt().coerceIn(0, 255)
                    lut[i] = res
                }
            }
        }

        // 4. Pixel transformation with Color Protection
        val processedPixels = IntArray(totalPixels)
        for (i in 0 until totalPixels) {
            val p = pixels[i]
            val a = (p shr 24) and 0xFF
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val chroma = maxC - minC

            val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
            val newLum = lut[lum]

            if (chroma > 22 && lum < whitePoint + 10) {
                // Colored content (e.g. blue pen ink, red official stamp, green seal)
                // Boost saturation and keep vivid chromaticity
                val lumScale = if (lum > 0) newLum.toFloat() / lum.toFloat() else 1f
                val satBoost = 1.35f
                val mean = (r + g + b) / 3f

                val newR = (mean + (r - mean) * satBoost) * lumScale
                val newG = (mean + (g - mean) * satBoost) * lumScale
                val newB = (mean + (b - mean) * satBoost) * lumScale

                processedPixels[i] = (a shl 24) or
                        (newR.toInt().coerceIn(0, 255) shl 16) or
                        (newG.toInt().coerceIn(0, 255) shl 8) or
                        (newB.toInt().coerceIn(0, 255))
            } else {
                // Neutral paper or text: scale directly according to LUT
                val newR = lut[r]
                val newG = lut[g]
                val newB = lut[b]

                processedPixels[i] = (a shl 24) or
                        (newR shl 16) or
                        (newG shl 8) or
                        newB
            }
        }

        // 5. Unsharp Masking / Edge Sharpening pass (makes text borders razor-sharp)
        val finalPixels = IntArray(totalPixels)
        val sharpenAmount = 0.45f // Subtle high-frequency boost

        for (y in 0 until height) {
            val yOffset = y * width
            for (x in 0 until width) {
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    finalPixels[yOffset + x] = processedPixels[yOffset + x]
                    continue
                }

                val center = processedPixels[yOffset + x]
                val left = processedPixels[yOffset + x - 1]
                val right = processedPixels[yOffset + x + 1]
                val up = processedPixels[(y - 1) * width + x]
                val down = processedPixels[(y + 1) * width + x]

                val a = (center shr 24) and 0xFF

                val cr = (center shr 16) and 0xFF
                val cg = (center shr 8) and 0xFF
                val cb = center and 0xFF

                // Fast cross laplacian edge difference
                val diffR = (4 * cr - ((left shr 16) and 0xFF) - ((right shr 16) and 0xFF) - ((up shr 16) and 0xFF) - ((down shr 16) and 0xFF))
                val diffG = (4 * cg - ((left shr 8) and 0xFF) - ((right shr 8) and 0xFF) - ((up shr 8) and 0xFF) - ((down shr 8) and 0xFF))
                val diffB = (4 * cb - (left and 0xFF) - (right and 0xFF) - (up and 0xFF) - (down and 0xFF))

                val outR = (cr + diffR * sharpenAmount).toInt().coerceIn(0, 255)
                val outG = (cg + diffG * sharpenAmount).toInt().coerceIn(0, 255)
                val outB = (cb + diffB * sharpenAmount).toInt().coerceIn(0, 255)

                finalPixels[yOffset + x] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(finalPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Pro-Grade Black & White Document Scanner Filter:
     * High-contrast binarization with smooth anti-aliased text edges and bleached paper.
     */
    fun applyBwDocument(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val totalPixels = width * height
        if (totalPixels <= 0) return source

        val pixels = IntArray(totalPixels)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // Histogram to find threshold
        val histogram = IntArray(256)
        val stride = max(1, totalPixels / 40000)
        var sampled = 0
        for (i in 0 until totalPixels step stride) {
            val p = pixels[i]
            val lum = (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)).toInt().coerceIn(0, 255)
            histogram[lum]++
            sampled++
        }

        var cumulative = 0
        var threshold = 145
        val target = (sampled * 0.35f).toInt()
        for (i in 0..255) {
            cumulative += histogram[i]
            if (cumulative >= target) {
                threshold = i.coerceIn(120, 175)
                break
            }
        }

        val lowCutoff = (threshold - 35).coerceAtLeast(10)
        val highCutoff = (threshold + 30).coerceAtMost(245)
        val range = (highCutoff - lowCutoff).toFloat()

        val outputPixels = IntArray(totalPixels)
        for (i in 0 until totalPixels) {
            val p = pixels[i]
            val a = (p shr 24) and 0xFF
            val lum = (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)).toInt()

            val v = when {
                lum <= lowCutoff -> 0
                lum >= highCutoff -> 255
                else -> {
                    val norm = (lum - lowCutoff) / range
                    (norm * 255f).toInt().coerceIn(0, 255)
                }
            }
            outputPixels[i] = (a shl 24) or (v shl 16) or (v shl 8) or v
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(outputPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Pro-Grade Grayscale Document Scanner Filter:
     * Levels paper background shadows while maintaining smooth continuous grayscale tones.
     */
    fun applyGrayscaleDocument(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val totalPixels = width * height
        if (totalPixels <= 0) return source

        val pixels = IntArray(totalPixels)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val outputPixels = IntArray(totalPixels)
        for (i in 0 until totalPixels) {
            val p = pixels[i]
            val a = (p shr 24) and 0xFF
            val lum = (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)).toInt()

            // Contrast stretch: dark ink pushed down, paper highlights pushed up
            val v = when {
                lum > 215 -> 255
                lum < 35 -> (lum * 0.5f).toInt()
                else -> {
                    val norm = (lum - 35f) / 180f
                    (norm * 255f).toInt().coerceIn(0, 255)
                }
            }
            outputPixels[i] = (a shl 24) or (v shl 16) or (v shl 8) or v
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(outputPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Saves bitmap to a temporary JPEG file in cache and returns file.
     */
    fun saveBitmapToCache(context: Context, bitmap: Bitmap, prefix: String = "scan_page"): File {
        val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return file
    }

    /**
     * Creates a realistic document bitmap (Invoice/Receipt layout) for immediate testing.
     */
    fun createSampleDocumentBitmap(): Bitmap {
        val width = 1240
        val height = 1754 // A4 aspect at 150 DPI approx
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Subtle document background (clean off-white paper texture)
        canvas.drawColor(Color.rgb(250, 250, 252))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Outer margin border line
        paint.color = Color.rgb(220, 224, 230)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(RectF(60f, 60f, width - 60f, height - 60f), paint)

        // Header accent banner
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(30, 58, 138) // Deep Blue
        canvas.drawRect(RectF(80f, 80f, width - 80f, 220f), paint)

        // Header Title
        paint.color = Color.WHITE
        paint.textSize = 52f
        paint.isFakeBoldText = true
        canvas.drawText("TAX INVOICE & RECEIPT", 120f, 165f, paint)

        // Document ID & Date
        paint.textSize = 28f
        paint.isFakeBoldText = false
        paint.color = Color.rgb(219, 234, 254)
        canvas.drawText("INV-2026-08492", width - 360f, 145f, paint)
        canvas.drawText("Date: Oct 06, 2026", width - 360f, 185f, paint)

        // Bill To section
        paint.color = Color.rgb(30, 41, 59)
        paint.textSize = 34f
        paint.isFakeBoldText = true
        canvas.drawText("Billed To:", 120f, 300f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 26f
        paint.color = Color.rgb(71, 85, 105)
        canvas.drawText("Acme Technologies Inc.", 120f, 345f, paint)
        canvas.drawText("100 Innovation Way, Suite 400", 120f, 385f, paint)
        canvas.drawText("San Francisco, CA 94105", 120f, 425f, paint)
        canvas.drawText("Email: billing@acmetech.io", 120f, 465f, paint)

        // Vendor section
        paint.isFakeBoldText = true
        paint.textSize = 34f
        paint.color = Color.rgb(30, 41, 59)
        canvas.drawText("Provider:", width - 480f, 300f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 26f
        paint.color = Color.rgb(71, 85, 105)
        canvas.drawText("ScanCraft Solutions Ltd.", width - 480f, 345f, paint)
        canvas.drawText("Digital Document Services", width - 480f, 385f, paint)
        canvas.drawText("VAT: US-8823901-X", width - 480f, 425f, paint)

        // Table Header
        val tableTop = 530f
        paint.color = Color.rgb(241, 245, 249)
        paint.style = Paint.Style.FILL
        canvas.drawRect(RectF(120f, tableTop, width - 120f, tableTop + 55f), paint)

        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 26f
        paint.isFakeBoldText = true
        canvas.drawText("ITEM DESCRIPTION", 140f, tableTop + 38f, paint)
        canvas.drawText("QTY", 680f, tableTop + 38f, paint)
        canvas.drawText("RATE", 820f, tableTop + 38f, paint)
        canvas.drawText("AMOUNT", width - 260f, tableTop + 38f, paint)

        // Table rows
        val items = listOf(
            Triple("Cloud Document OCR Processing", "1", "$250.00"),
            Triple("Multi-Page PDF Compression Engine", "1", "$180.00"),
            Triple("High-Res Scanning Workflow Suite", "1", "$320.00"),
            Triple("WhatsApp & Export Cloud Connector", "1", "$95.00"),
            Triple("Hardware Document Perspective Tool", "1", "$155.00")
        )

        var rowY = tableTop + 105f
        paint.isFakeBoldText = false
        paint.textSize = 24f
        paint.color = Color.rgb(51, 65, 85)

        for (item in items) {
            canvas.drawText(item.first, 140f, rowY, paint)
            canvas.drawText(item.second, 695f, rowY, paint)
            canvas.drawText(item.third, 810f, rowY, paint)
            canvas.drawText(item.third, width - 260f, rowY, paint)

            paint.color = Color.rgb(226, 232, 240)
            paint.strokeWidth = 1f
            canvas.drawLine(120f, rowY + 20f, width - 120f, rowY + 20f, paint)

            paint.color = Color.rgb(51, 65, 85)
            rowY += 65f
        }

        // Summary calculations
        val sumY = rowY + 40f
        paint.color = Color.rgb(100, 116, 139)
        paint.textSize = 26f
        canvas.drawText("Subtotal:", width - 420f, sumY, paint)
        canvas.drawText("Tax (8.5%):", width - 420f, sumY + 45f, paint)

        paint.color = Color.rgb(15, 23, 42)
        paint.isFakeBoldText = true
        canvas.drawText("$1,000.00", width - 260f, sumY, paint)
        canvas.drawText("$85.00", width - 260f, sumY + 45f, paint)

        // Total highlight box
        paint.color = Color.rgb(238, 242, 255)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(width - 440f, sumY + 75f, width - 120f, sumY + 155f), 12f, 12f, paint)

        paint.color = Color.rgb(30, 58, 138)
        paint.textSize = 34f
        paint.isFakeBoldText = true
        canvas.drawText("Total Due:", width - 420f, sumY + 130f, paint)
        canvas.drawText("$1,085.00", width - 270f, sumY + 130f, paint)

        // Stamp / signature watermark
        paint.color = Color.rgb(16, 185, 129) // Emerald
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawRoundRect(RectF(140f, sumY + 70f, 400f, sumY + 160f), 10f, 10f, paint)

        paint.style = Paint.Style.FILL
        paint.textSize = 32f
        paint.isFakeBoldText = true
        canvas.drawText("PAID & VERIFIED", 155f, sumY + 128f, paint)

        // Footer note
        paint.color = Color.rgb(148, 163, 184)
        paint.textSize = 22f
        paint.isFakeBoldText = false
        canvas.drawText("Thank you for your business! Scanned with ScanCraft PDF Mobile.", 120f, height - 120f, paint)
        canvas.drawText("Document Security Hash: 8F3D-4A9B-C71E-9022-PDF", 120f, height - 85f, paint)

        return bitmap
    }
}
