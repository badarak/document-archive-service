package com.badarak.archive.domain.port.outbound

import com.badarak.archive.domain.model.ContentInspection
import com.badarak.archive.domain.model.DocumentFormat
import com.badarak.archive.domain.model.FileContent

/** Tells what a content really is, whatever its name and declared content type say. */
interface ContentInspector {
    /**
     * Inspects [content], of [sizeBytes] bytes, as a file of [format]. Reads only what it needs: never the whole content
     * into memory.
     */
    fun inspect(content: FileContent, sizeBytes: Long, format: DocumentFormat): ContentInspection
}
