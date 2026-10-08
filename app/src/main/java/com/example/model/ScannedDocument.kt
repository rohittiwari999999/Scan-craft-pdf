package com.example.model

data class ScannedDocument(
    val id: String,
    val title: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val createdAtMillis: Long,
    val paperSize: PaperSize,
    val pageCount: Int = 1,
    val thumbnailPath: String? = null
)
