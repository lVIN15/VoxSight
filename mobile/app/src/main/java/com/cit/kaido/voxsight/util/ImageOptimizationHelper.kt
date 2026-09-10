package com.cit.kaido.voxsight.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ImageOptimizationHelper {

    private const val TAG = "ImageOptimizer"
    // Optimal height for 300 DPI sheet music scan (standard 11-inch letter paper at 300 DPI ≈ 3300px)
    private const val TARGET_MAX_DIMENSION = 3200

    /**
     * Optimizes a captured sheet music photo:
     * 1. Fixes camera sensor EXIF rotation.
     * 2. Optionally crops to viewfinder bounds (removing surrounding desk/clutter).
     * 3. Rescales to standard 300 DPI equivalent resolution (~3200px) using inSampleSize.
     * 4. Applies clean grayscale conversion with gentle contrast preservation.
     * 5. Saves as high-quality compressed JPEG.
     *
     * @param cropRect Optional normalized crop rectangle (0.0–1.0) relative to the full image.
     *                 Used to crop the photo to the viewfinder guide box bounds.
     */
    fun optimizeSheetMusicImage(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        cropRect: RectF? = null
    ): File {
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

            var bitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: run {
                Log.w(TAG, "Failed to decode bitmap. Copying raw file.")
                copyUriToFile(context, sourceUri, outputFile)
                return outputFile
            }

            // Step 5: Apply EXIF Rotation & Rescale to target dimension
            bitmap = rotateAndScaleBitmap(bitmap, orientation, TARGET_MAX_DIMENSION)

            // Step 6: Crop to viewfinder bounds if provided
            if (cropRect != null) {
                bitmap = cropToViewfinder(bitmap, cropRect)
            }

            // Step 7: Apply clean grayscale conversion (preserving fine staff line detail)
            bitmap = cleanGrayscaleConvert(bitmap)

            // Step 8: Write to output file as high-quality JPEG
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bitmap.recycle()

            Log.i(TAG, "Optimized image saved: ${outputFile.name} (${outputFile.length() / 1024} KB, ${bitmap.width}x${bitmap.height})")
            return outputFile

        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing image: ${e.message}", e)
            copyUriToFile(context, sourceUri, outputFile)
            return outputFile
        } finally {
            inputStream?.close()
        }
    }

    /**
     * Crops the bitmap to the normalized viewfinder rectangle.
     * The cropRect values are in 0.0–1.0 coordinates relative to the full bitmap.
     * A 5% margin is added to avoid cutting edge notation.
     */
    private fun cropToViewfinder(bitmap: Bitmap, cropRect: RectF): Bitmap {
        val w = bitmap.width
        val h = bitmap.height

        // Apply 5% safety margin to avoid cutting notes at the edge
        val marginX = (cropRect.width() * 0.05f).coerceAtMost(0.05f)
        val marginY = (cropRect.height() * 0.05f).coerceAtMost(0.05f)

        val left = ((cropRect.left - marginX).coerceAtLeast(0f) * w).roundToInt()
        val top = ((cropRect.top - marginY).coerceAtLeast(0f) * h).roundToInt()
        val right = ((cropRect.right + marginX).coerceAtMost(1f) * w).roundToInt()
        val bottom = ((cropRect.bottom + marginY).coerceAtMost(1f) * h).roundToInt()

        val cropW = (right - left).coerceAtLeast(100)
        val cropH = (bottom - top).coerceAtLeast(100)

        if (cropW >= w * 0.9f && cropH >= h * 0.9f) {
            // Crop would be nearly the whole image — skip
            return bitmap
        }

        Log.i(TAG, "Cropping to viewfinder: ($left,$top)-($right,$bottom) from ${w}x${h}")
        val cropped = Bitmap.createBitmap(bitmap, left, top, cropW.coerceAtMost(w - left), cropH.coerceAtMost(h - top))
        if (cropped != bitmap) {
            bitmap.recycle()
        }
        return cropped
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

        val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (result != bitmap) {
            bitmap.recycle()
        }
        return result
    }

    /**
     * Clean grayscale conversion that preserves thin staff line detail.
     * Uses standard luminance weights without destructive brightness offset.
     * Applies gentle contrast (1.15x) to darken notation without blowing out lines.
     */
    private fun cleanGrayscaleConvert(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Gentle grayscale + slight contrast boost (1.15x, no brightness offset)
        // This preserves thin anti-aliased staff lines instead of blowing them out
        val contrast = 1.15f
        val brightness = 0f  // No brightness offset — preserves line continuity

        val cm = ColorMatrix(
            floatArrayOf(
                contrast * 0.2126f, contrast * 0.7152f, contrast * 0.0722f, 0f, brightness,
                contrast * 0.2126f, contrast * 0.7152f, contrast * 0.0722f, 0f, brightness,
                contrast * 0.2126f, contrast * 0.7152f, contrast * 0.0722f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        if (source != result) {
            source.recycle()
        }
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
