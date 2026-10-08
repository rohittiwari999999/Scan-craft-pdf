package com.example.model

/**
 * Standard paper sizes in PDF points (72 points per inch).
 */
enum class PaperSize(
    val displayName: String,
    val description: String,
    val widthPoints: Int,
    val heightPoints: Int
) {
    A4(
        displayName = "A4",
        description = "210 × 297 mm",
        widthPoints = 595,
        heightPoints = 842
    ),
    A5(
        displayName = "A5",
        description = "148 × 210 mm",
        widthPoints = 420,
        heightPoints = 595
    ),
    LETTER(
        displayName = "Letter",
        description = "8.5 × 11 in",
        widthPoints = 612,
        heightPoints = 792
    );

    val aspectRatio: Float
        get() = widthPoints.toFloat() / heightPoints.toFloat()
}
