package com.cit.kaido.voxsight.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File

class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val onProgress: (Float) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = file.length()
        var uploaded: Long = 0
        file.source().use { source ->
            var read: Long
            val buffer = okio.Buffer()
            while (source.read(buffer, 8192).also { read = it } != -1L) {
                sink.write(buffer, read)
                uploaded += read
                onProgress(uploaded.toFloat() / total.toFloat())
            }
        }
    }
}
