package com.badarak.archive.domain.model

/**
 * File formats the service knows how to archive, identified by their extension, with the content types a caller may
 * declare for each, the first one being its own [mediaType]. Whether a content really is of its format is told by the
 * [com.badarak.archive.domain.port.outbound.ContentInspector].
 */
enum class DocumentFormat(val extension: String, val contentTypes: Set<String>) {
    PDF("pdf", setOf("application/pdf")),
    DOC("doc", setOf("application/msword")),
    DOCX("docx", setOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
    MP3("mp3", setOf("audio/mpeg", "audio/mp3")),
    WAV("wav", setOf("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave")),
    M4A("m4a", setOf("audio/mp4", "audio/x-m4a", "audio/m4a")),
    EML("eml", setOf("message/rfc822")),
    ;

    val mediaType: String
        get() = contentTypes.first()

    companion object {
        fun ofExtension(extension: String): DocumentFormat? = entries.find { it.extension == extension.lowercase() }
    }
}
