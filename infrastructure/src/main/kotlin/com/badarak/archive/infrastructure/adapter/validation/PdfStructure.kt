package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.FileContent

/**
 * A PDF ends with an end-of-file marker, which readers look for in its last kilobyte: a file without one is truncated or
 * is not a PDF past its first bytes. The file is not parsed further.
 */
internal object PdfStructure : StructureCheck {

    private const val TAIL_LENGTH = 1024
    private val END_OF_FILE = "%%EOF".toByteArray(Charsets.US_ASCII)

    override fun malformation(content: FileContent, sizeBytes: Long, header: ByteArray): String? =
        if (content.tail(sizeBytes, TAIL_LENGTH).lastIndexOf(END_OF_FILE) < 0) "it has no end-of-file marker" else null
}
