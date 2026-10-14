package com.badarak.archive.domain.port.inbound

import java.io.InputStream

/**
 * File as received from the caller, before any normalisation.
 *
 * [content] must yield exactly [sizeBytes] bytes. The use case reads it but does not close it: that
 * stays with whoever opened it.
 */
class UploadedFile(val fileName: String, val contentType: String, val sizeBytes: Long, val content: InputStream)
