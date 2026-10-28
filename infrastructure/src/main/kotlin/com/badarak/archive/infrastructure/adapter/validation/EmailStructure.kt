package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.FileContent

/**
 * An e-mail (RFC 5322) is text that starts with header fields, `Name: value`, possibly folded on the following lines,
 * among them `From` or `Date`, then an empty line before its body. The header must fit in its first [HEADER_LIMIT] bytes,
 * which must hold no NUL byte: a binary file is never an e-mail.
 */
internal object EmailStructure : StructureCheck {

    private const val HEADER_LIMIT = 64 * 1024
    private val FIELD = Regex("^[!-9;-~]+:")
    private val REQUIRED_FIELDS = setOf("from", "date")

    override fun malformation(content: FileContent, sizeBytes: Long, header: ByteArray): String? {
        val start = content.read(0, HEADER_LIMIT)
        if (start.any { it == 0.toByte() }) return "it is not text"
        val lines = String(start, Charsets.ISO_8859_1).split('\n').map { it.removeSuffix("\r") }
        val headerEnd = lines.indexOf("")
        if (headerEnd < 0 && sizeBytes > start.size) return "its header exceeds ${HEADER_LIMIT / 1024} KiB"
        val headerLines = if (headerEnd < 0) lines else lines.subList(0, headerEnd)
        if (headerLines.isEmpty() || headerLines.first().startsWith(' ') || headerLines.first().startsWith('\t')) {
            return "it does not start with an e-mail header"
        }
        val fields = headerLines.filterNot { it.startsWith(' ') || it.startsWith('\t') }
        if (fields.any { !FIELD.containsMatchIn(it) }) return "its header holds a line that is not a header field"
        val names = fields.map { it.substringBefore(':').lowercase() }.toSet()
        return if (names.none { it in REQUIRED_FIELDS }) "its header has neither a From nor a Date field" else null
    }
}
