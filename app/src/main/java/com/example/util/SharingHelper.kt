package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object SharingHelper {

    private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

    /**
     * Obtains a secure content:// URI for a given file.
     */
    fun getContentUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}$FILE_PROVIDER_AUTHORITY_SUFFIX"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Shares the PDF directly to WhatsApp.
     * If WhatsApp is not installed, gracefully opens the system share chooser.
     */
    fun shareToWhatsApp(context: Context, file: File, onFallbackToGeneral: () -> Unit = {}) {
        if (!file.exists()) {
            Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = getContentUri(context, file)
        val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Scanned Document: ${file.name}")
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            // Check if WhatsApp or WhatsApp Business is installed
            val packageManager = context.packageManager
            val activities = packageManager.queryIntentActivities(whatsappIntent, 0)
            if (activities.isNotEmpty()) {
                context.startActivity(whatsappIntent)
            } else {
                // Try WhatsApp Business package
                whatsappIntent.setPackage("com.whatsapp.w4b")
                val w4bActivities = packageManager.queryIntentActivities(whatsappIntent, 0)
                if (w4bActivities.isNotEmpty()) {
                    context.startActivity(whatsappIntent)
                } else {
                    Toast.makeText(
                        context,
                        "WhatsApp not found. Opening general share menu…",
                        Toast.LENGTH_LONG
                    ).show()
                    onFallbackToGeneral()
                    shareGeneral(context, file)
                }
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "WhatsApp not found. Opening general share menu…",
                Toast.LENGTH_LONG
            ).show()
            onFallbackToGeneral()
            shareGeneral(context, file)
        } catch (e: Exception) {
            e.printStackTrace()
            shareGeneral(context, file)
        }
    }

    /**
     * General share chooser for the PDF.
     */
    fun shareGeneral(context: Context, file: File) {
        if (!file.exists()) return
        val uri = getContentUri(context, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TEXT, "Scanned PDF Document: ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share PDF with…").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Opens the PDF with any installed PDF viewer app.
     */
    fun viewPdf(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = getContentUri(context, file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(viewIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No PDF viewer application found on device.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
