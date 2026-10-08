package com.example.model

enum class DocumentFilter(val displayName: String, val description: String) {
    ORIGINAL("Original", "Natural camera colors"),
    MAGIC_COLOR("Magic Color", "Vibrant text & contrast enhancement"),
    BW_DOCUMENT("B&W Doc", "Crisp paper & high contrast text"),
    GRAYSCALE("Grayscale", "Smooth monochrome tone")
}
