package com.creator.automation

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito

class VisualStateAnalyzerTest {

    @Test
    fun testAnalyzeFrame_NullOrRecycledBitmap_ReturnsNull() {
        assertNull(VisualStateAnalyzer.analyzeFrame(null))

        val mockRecycledBmp = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(mockRecycledBmp.isRecycled).thenReturn(true)
        assertNull(VisualStateAnalyzer.analyzeFrame(mockRecycledBmp))
    }

    @Test
    fun testAnalyzeFrame_ValidBitmap_ReturnsVisualFrameAnalysis() {
        val mockBmp = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(mockBmp.isRecycled).thenReturn(false)
        Mockito.`when`(mockBmp.width).thenReturn(100)
        Mockito.`when`(mockBmp.height).thenReturn(100)

        val analysis = VisualStateAnalyzer.analyzeFrame(mockBmp)
        assertNotNull(analysis)
        assertEquals(100, analysis?.width)
        assertEquals(100, analysis?.height)
        assertNotNull(analysis?.visualSignature)
    }

    @Test
    fun testAnalyzeFrame_IdenticalBitmaps_ProduceIdenticalSignatures() {
        val mockBmp1 = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(mockBmp1.isRecycled).thenReturn(false)
        Mockito.`when`(mockBmp1.width).thenReturn(50)
        Mockito.`when`(mockBmp1.height).thenReturn(50)

        val mockBmp2 = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(mockBmp2.isRecycled).thenReturn(false)
        Mockito.`when`(mockBmp2.width).thenReturn(50)
        Mockito.`when`(mockBmp2.height).thenReturn(50)

        val analysis1 = VisualStateAnalyzer.analyzeFrame(mockBmp1)
        val analysis2 = VisualStateAnalyzer.analyzeFrame(mockBmp2)

        assertEquals(analysis1?.visualSignature, analysis2?.visualSignature)
    }

    @Test
    fun testCompareSignatures_BehavesCorrectly() {
        assertEquals(
            VisualChangeResult.NO_VISUAL_CHANGE,
            VisualStateAnalyzer.compareSignatures("V_W100H100_abc", "V_W100H100_abc")
        )

        assertEquals(
            VisualChangeResult.VISUAL_CHANGE,
            VisualStateAnalyzer.compareSignatures("V_W100H100_abc", "V_W100H100_xyz")
        )

        assertEquals(
            VisualChangeResult.UNKNOWN,
            VisualStateAnalyzer.compareSignatures(null, "V_W100H100_xyz")
        )

        assertEquals(
            VisualChangeResult.UNKNOWN,
            VisualStateAnalyzer.compareSignatures("V_W100H100_abc", "")
        )
    }
}
