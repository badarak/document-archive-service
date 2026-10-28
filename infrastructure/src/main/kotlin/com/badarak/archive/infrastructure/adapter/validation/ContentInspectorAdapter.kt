package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.ContentInspection
import com.badarak.archive.domain.model.DocumentFormat
import com.badarak.archive.domain.model.FileContent
import com.badarak.archive.domain.port.outbound.ContentInspector
import org.springframework.stereotype.Component

/**
 * Inspects a content from its first bytes: an executable is never taken for a document, whatever the format it is sent
 * as, and each format must carry its own signature (e-mails, plain text, carry none). The structure of the formats that
 * have a [StructureCheck] is then checked, from bounded reads only: the content is never loaded whole nor decompressed.
 */
@Component
class ContentInspectorAdapter : ContentInspector {

    override fun inspect(content: FileContent, sizeBytes: Long, format: DocumentFormat): ContentInspection {
        val header = content.open().use { it.readNBytes(HEADER_LENGTH) }
        Executable.of(header)?.let { return ContentInspection.OtherFormat(it.mediaType) }
        if (!FormatSignatures.matches(format, header)) return ContentInspection.OtherFormat(null)
        val malformation = STRUCTURES[format]?.malformation(content, sizeBytes, header)
        return malformation?.let { ContentInspection.Malformed(it) } ?: ContentInspection.Conforming
    }

    companion object {
        const val HEADER_LENGTH = 64

        private val STRUCTURES: Map<DocumentFormat, StructureCheck> = mapOf(
            DocumentFormat.PDF to PdfStructure,
            DocumentFormat.DOCX to DocxStructure,
            DocumentFormat.MP3 to Mp3Structure,
            DocumentFormat.WAV to WavStructure,
            DocumentFormat.M4A to M4aStructure,
            DocumentFormat.EML to EmailStructure,
        )
    }
}
