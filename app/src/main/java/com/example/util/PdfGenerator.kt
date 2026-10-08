package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.PaperSize
import com.example.model.ScannedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object PdfGenerator {

    /**
     * Generates a PDF file from a list of bitmaps with the selected paper size.
     * Saves the PDF to internal/external app storage and optionally publishes to public Downloads.
     */
    suspend fun generatePdf(
        context: Context,
        pages: List<Bitmap>,
        paperSize: PaperSize,
        customTitle: String? = null
    ): Result<ScannedDocument> = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No pages to generate PDF"))
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanTitle = if (!customTitle.isNullOrBlank()) {
            customTitle.trim().removeSuffix(".pdf")
        } else {
            "Scan_$timestamp"
        }
        val fileName = "$cleanTitle.pdf"

        val pdfDoc = PdfDocument()

        try {
            val pageWidth = paperSize.widthPoints
            val pageHeight = paperSize.heightPoints
            val margin = 20f // Margin in points

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            pages.forEachIndexed { index, bitmap ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas

                // Background white
                canvas.drawColor(Color.WHITE)

                // Calculate fitted bounds within margin
                val printableWidth = pageWidth - (margin * 2)
                val printableHeight = pageHeight - (margin * 2)

                val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                val printableAspect = printableWidth / printableHeight

                val drawWidth: Float
                val drawHeight: Float
                if (bitmapAspect > printableAspect) {
                    // Fit to width
                    drawWidth = printableWidth
                    drawHeight = printableWidth / bitmapAspect
                } else {
                    // Fit to height
                    drawHeight = printableHeight
                    drawWidth = printableHeight * bitmapAspect
                }

                val left = margin + (printableWidth - drawWidth) / 2f
                val top = margin + (printableHeight - drawHeight) / 2f
                val destRect = RectF(left, top, left + drawWidth, top + drawHeight)

                canvas.drawBitmap(bitmap, null, destRect, paint)
                pdfDoc.finishPage(page)
            }

            // Save to internal app documents directory
            val docsDir = File(context.filesDir, "scanned_pdfs").apply { mkdirs() }
            val outputFile = File(docsDir, fileName)

            FileOutputStream(outputFile).use { out ->
                pdfDoc.writeTo(out)
            }

            // Also save a small thumbnail of page 1 for the history list
            val thumbFile = File(context.cacheDir, "thumb_${outputFile.nameWithoutExtension}.jpg")
            try {
                val firstBitmap = pages.first()
                val thumbBitmap = Bitmap.createScaledBitmap(
                    firstBitmap,
                    180,
                    (180 / (firstBitmap.width.toFloat() / firstBitmap.height.toFloat())).toInt().coerceAtLeast(180),
                    true
                )
                FileOutputStream(thumbFile).use { out ->
                    thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Save copy into public MediaStore Downloads if Android Q+
            tryPublishToDownloads(context, outputFile, fileName)

            val scannedDoc = ScannedDocument(
                id = UUID.randomUUID().toString(),
                title = cleanTitle,
                filePath = outputFile.absolutePath,
                fileSizeBytes = outputFile.length(),
                createdAtMillis = System.currentTimeMillis(),
                paperSize = paperSize,
                pageCount = pages.size,
                thumbnailPath = if (thumbFile.exists()) thumbFile.absolutePath else null
            )

            Result.success(scannedDoc)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            try {
                pdfDoc.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun tryPublishToDownloads(context: Context, sourceFile: File, displayName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ScanCraft")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(sourceFile).use { inStream ->
                            inStream.copyTo(out)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Non-fatal, app internal copy remains safe
            e.printStackTrace()
        }
    }
}
