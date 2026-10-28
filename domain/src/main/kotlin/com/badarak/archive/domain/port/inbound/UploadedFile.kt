package com.badarak.archive.domain.port.inbound

import com.badarak.archive.domain.model.FileContent

/**
 * File as received from the caller, before any normalisation.
 *
 * [content] must yield exactly [sizeBytes] bytes each time it is opened.
 */
class UploadedFile(val fileName: String, val contentType: String, val sizeBytes: Long, val content: FileContent)
