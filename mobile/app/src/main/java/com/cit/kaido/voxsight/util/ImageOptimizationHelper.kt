package com.cit.kaido.voxsight.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

object ImageOptimizationHelper {

    private const val TAG = "ImageOptimizer"
    // Optimal height for 200 DPI sheet music scan (standard 11-inch letter paper at 200 DPI = 2200px)
    private const val TARGET_MAX_DIMENSION = 2200

    /**
     * Optimizes a captured sheet music photo:
     * 1. Fixes camera sensor EXIF rotation.
     * 2. Rescales to standard 200 DPI equivalent resolution (~2200px) using inSampleSize.
     * 3. Enhances contrast to remove shadows and make faded notes deep black on clean white paper.
     * 4. Saves as high-quality compressed JPEG (~400KB - 800KB).
     */
    fun optimizeSheetMusicImage(context: Context, sourceUri: Uri, outputFile: File): File {
        var inputStream: InputStream? = null
        try {
            // Step 1: Read EXIF orientation
            val orientation = getExifOrientation(context, sourceUri)

            // Step 2: Measure dimensions without loading full pixels into RAM
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) {
                Log.w(TAG, "Could not read image bounds. Copying raw file.")
                copyUriToFile(context, sourceUri, outputFile)
                return outputFile
            }

            // Step 3: Compute inSampleSize to prevent OOM
            val maxOriginalDim = max(originalWidth, originalHeight)
            var sampleSize = 1
            while ((maxOriginalDim / (sampleSize * 2)) >= TARGET_MAX_DIMENSION) {
                sampleSize *= 2
            }

            // Step 4: Decode downsampled bitmap
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val rawBitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: run {
                Log.w(TAG, "Failed to decode bitmap. Copying raw file.")
                copyUriToFile(context, sourceUri, outputFile)
                return outputFile
            }

            // Step 5: Apply EXIF Rotation & Rescale to target dimension
            val rotatedAndScaledBitmap = rotateAndScaleBitmap(rawBitmap, orientation, TARGET_MAX_DIMENSION)
            if (rotatedAndScaledBitmap != rawBitmap) {
                rawBitmap.recycle()
            }

            // Step 6: Apply High-Contrast Grayscale Filter for razor-sharp staff lines
            val enhancedBitmap = enhanceContrast(rotatedAndScaledBitmap)
            if (enhancedBitmap != rotatedAndScaledBitmap) {
                rotatedAndScaledBitmap.recycle()
            }

            // Step 7: Write to output file as compressed JPEG
            FileOutputStream(outputFile).use { out ->
                enhancedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            enhancedBitmap.recycle()

            Log.i(TAG, "Optimized image saved: ${outputFile.name} (${outputFile.length() / 1024} KB)")
            return outputFile

        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing image: ${e.message}", e)
            copyUriToFile(context, sourceUri, outputFile)
            return outputFile
        } finally {
            inputStream?.close()
        }
    }

    private fun getExifOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun rotateAndScaleBitmap(bitmap: Bitmap, orientation: Int, targetMaxDim: Int): Bitmap {
        val matrix = Matrix()

        // EXIF Rotation
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }

        // Scale factor
        val currentMax = max(bitmap.width, bitmap.height)
        if (currentMax > targetMaxDim) {
            val scale = targetMaxDim.toFloat() / currentMax.toFloat()
            matrix.postScale(scale, scale)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Boosts contrast and normalizes paper brightness:
     * - Dark notes are pulled towards solid black.
     * - Off-white / shadowy paper is pulled towards pure clean white.
     */
    private fun enhanceContrast(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // ColorMatrix: Grayscale + Contrast Boost (1.35x contrast, +15 brightness)
        val contrast = 1.35f
        val brightness = 15f

        val cm = ColorMatrix(
            floatArrayOf(
                contrast * 0.33f, contrast * 0.59f, contrast * 0.11f, 0f, brightness,
                contrast * 0.33f, contrast * 0.59f, contrast * 0.11f, 0f, brightness,
                contrast * 0.33f, contrast * 0.59f, contrast * 0.11f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun copyUriToFile(context: Context, uri: Uri, dest: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Stitches multiple normalized sheet music images into a single multi-page PDF on-device.
     * Uses Android's native PdfDocument API (zero third-party dependencies, <50ms overhead).
     */
    fun createPdfFromImages(imageFiles: List<File>, outputPdfFile: File): File {
        if (imageFiles.isEmpty()) return outputPdfFile

        val pdfDocument = PdfDocument()
        try {
            imageFiles.forEachIndexed { index, file ->
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(page)
                bitmap.recycle()
            }

            FileOutputStream(outputPdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            Log.i(TAG, "Generated multi-page PDF: ${outputPdfFile.name} with ${imageFiles.size} pages (${outputPdfFile.length() / 1024} KB)")
        } catch (e: Exception) {
            Log.e(TAG, "Error generating multi-page PDF: ${e.message}", e)
        } finally {
            pdfDocument.close()
        }
        return outputPdfFile
    }
}
