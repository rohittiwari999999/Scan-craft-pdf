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
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import com.example.model.DocumentFilter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

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
            DocumentFilter.GRAYSCALE -> applyGrayscale(source)
            DocumentFilter.MAGIC_COLOR -> applyMagicColor(source)
            DocumentFilter.BW_DOCUMENT -> applyBwDocument(source)
        }
    }

    private fun applyGrayscale(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private fun applyMagicColor(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        // Boost contrast and vibrant saturation
        val contrast = 1.35f
        val brightness = 15f
        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        // Slight saturation boost
        val satMatrix = ColorMatrix().apply { setSaturation(1.25f) }
        cm.postConcat(satMatrix)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private fun applyBwDocument(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        // High contrast grayscale filter to whiten paper and deepen dark ink text
        val contrast = 2.2f
        val brightness = -40f
        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val grayMatrix = ColorMatrix().apply { setSaturation(0f) }
        cm.preConcat(grayMatrix)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
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
