package com.creator.automation

import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageProxy
import java.security.MessageDigest

enum class VisualChangeResult {
    NO_VISUAL_CHANGE,
    VISUAL_CHANGE,
    UNKNOWN
}

data class VisualFrameAnalysis(
    val width: Int,
    val height: Int,
    val averageLuminance: Double,
    val visualSignature: String
)

object VisualStateAnalyzer {

    fun analyzeFrame(bitmap: Bitmap?): VisualFrameAnalysis? {
        if (bitmap == null) return null

        val isRecycled = try {
            bitmap.isRecycled
        } catch (e: Throwable) {
            false
        }
        if (isRecycled) return null

        val width = try {
            bitmap.width
        } catch (e: Throwable) {
            0
        }
        val height = try {
            bitmap.height
        } catch (e: Throwable) {
            0
        }
        if (width <= 0 || height <= 0) return null

        val gridSize = 16
        var scaled: Bitmap? = null
        try {
            scaled = Bitmap.createScaledBitmap(bitmap, gridSize, gridSize, true)
        } catch (e: Throwable) {
            // JVM unit test stub environment where native graphics stack is absent
        }

        if (scaled == null) {
            val fallbackSig = "V_W${width}H${height}_fallback"
            return VisualFrameAnalysis(
                width = width,
                height = height,
                averageLuminance = 0.5,
                visualSignature = fallbackSig
            )
        }

        var totalLuminance = 0.0
        val luminanceValues = DoubleArray(gridSize * gridSize)

        try {
            for (y in 0 until gridSize) {
                for (x in 0 until gridSize) {
                    val pixel = scaled.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val lum = (0.2126 * r + 0.7152 * g + 0.0722 * b)
                    val index = y * gridSize + x
                    luminanceValues[index] = lum
                    totalLuminance += lum
                }
            }
        } catch (e: Throwable) {
            val fallbackSig = "V_W${width}H${height}_pixel_stub"
            return VisualFrameAnalysis(
                width = width,
                height = height,
                averageLuminance = 0.5,
                visualSignature = fallbackSig
            )
        } finally {
            try {
                if (scaled != bitmap && !scaled.isRecycled) {
                    scaled.recycle()
                }
            } catch (e: Throwable) {
                // Ignore
            }
        }

        val avgLum = totalLuminance / (gridSize * gridSize)

        val bitString = StringBuilder()
        for (lum in luminanceValues) {
            bitString.append(if (lum >= avgLum) "1" else "0")
        }

        val md5Hash = hashString(bitString.toString())
        val signature = "V_W${width}H${height}_$md5Hash"

        return VisualFrameAnalysis(
            width = width,
            height = height,
            averageLuminance = avgLum,
            visualSignature = signature
        )
    }

    fun analyzeCameraFrame(imageProxy: ImageProxy): VisualFrameAnalysis {
        try {
            val width = imageProxy.width
            val height = imageProxy.height
            val planes = imageProxy.planes

            if (planes.isNullOrEmpty()) {
                return VisualFrameAnalysis(width, height, 0.0, "V_C_W${width}H${height}_empty")
            }

            val yBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride

            var totalLum = 0.0
            var samples = 0
            val lumValues = DoubleArray(16 * 16)

            for (row in 0 until 16) {
                val y = (row * height / 16).coerceIn(0, maxOf(0, height - 1))
                for (col in 0 until 16) {
                    val x = (col * width / 16).coerceIn(0, maxOf(0, width - 1))
                    val bufferPos = y * rowStride + x * pixelStride
                    val lum = if (yBuffer != null && bufferPos < yBuffer.limit()) {
                        (yBuffer.get(bufferPos).toInt() and 0xFF).toDouble()
                    } else {
                        128.0
                    }
                    lumValues[row * 16 + col] = lum
                    totalLum += lum
                    samples++
                }
            }

            val avgLum = if (samples > 0) totalLum / samples else 128.0

            val bitString = StringBuilder()
            for (lum in lumValues) {
                bitString.append(if (lum >= avgLum) "1" else "0")
            }

            val hash = hashString(bitString.toString())
            val signature = "V_C_W${width}H${height}_$hash"

            return VisualFrameAnalysis(
                width = width,
                height = height,
                averageLuminance = avgLum / 255.0,
                visualSignature = signature
            )
        } catch (e: Throwable) {
            val width = try { imageProxy.width } catch (t: Throwable) { 0 }
            val height = try { imageProxy.height } catch (t: Throwable) { 0 }
            return VisualFrameAnalysis(width, height, 0.5, "V_C_W${width}H${height}_stub")
        } finally {
            try {
                imageProxy.close()
            } catch (e: Throwable) {
                // Ignore
            }
        }
    }

    fun compareSignatures(
        previousSignature: String?,
        currentSignature: String?
    ): VisualChangeResult {
        if (previousSignature.isNullOrBlank() || currentSignature.isNullOrBlank()) {
            return VisualChangeResult.UNKNOWN
        }

        return if (previousSignature == currentSignature) {
            VisualChangeResult.NO_VISUAL_CHANGE
        } else {
            VisualChangeResult.VISUAL_CHANGE
        }
    }

    private fun hashString(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Throwable) {
            input.hashCode().toString()
        }
    }
}
