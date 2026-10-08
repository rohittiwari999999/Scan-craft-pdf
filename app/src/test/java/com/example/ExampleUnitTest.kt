package com.example

import com.example.model.PaperSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testPaperSizes() {
        assertEquals(595, PaperSize.A4.widthPoints)
        assertEquals(842, PaperSize.A4.heightPoints)
        assertEquals(420, PaperSize.A5.widthPoints)
        assertEquals(595, PaperSize.A5.heightPoints)
        assertEquals(612, PaperSize.LETTER.widthPoints)
        assertEquals(792, PaperSize.LETTER.heightPoints)

        assertTrue(PaperSize.A4.aspectRatio < 1.0f)
        assertTrue(PaperSize.LETTER.aspectRatio < 1.0f)
    }
}
