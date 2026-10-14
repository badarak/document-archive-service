package com.badarak.archive.application

import java.io.InputStream

/**
 * Request to archive one document, as received from the caller.
 *
 * [content] must yield exactly [sizeBytes] bytes. The use case reads it but does not close it: that
 * stays with whoever opened it.
 */
class ArchiveDocumentCommand(
    val caller: AuthenticatedCaller,
    val documentType: String,
    val businessReference: String?,
    val description: String?,
    val tags: Map<String, String>,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val content: InputStream,
)
