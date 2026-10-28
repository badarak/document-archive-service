package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.ContentInspection
import com.badarak.archive.domain.model.DocumentFormat
import com.badarak.archive.domain.model.FileContent
import com.badarak.archive.domain.port.outbound.ContentInspector
import org.springframework.stereotype.Component

/**
 * Inspects a content from its first bytes: an executable is never taken for a document, whatever the format it is sent
 * as, and each format must carry its own signature. E-mails are plain text: they carry none.
 */
@Component
class ContentInspectorAdapter : ContentInspector {

    override fun inspect(content: FileContent, sizeBytes: Long, format: DocumentFormat): ContentInspection {
        val header = content.open().use { it.readNBytes(HEADER_LENGTH) }
        Executable.of(header)?.let { return ContentInspection.OtherFormat(it.mediaType) }
        return if (FormatSignatures.matches(format, header)) ContentInspection.Conforming else ContentInspection.OtherFormat(null)
    }

    companion object {
        const val HEADER_LENGTH = 64
    }
}
