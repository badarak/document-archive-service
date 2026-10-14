package com.badarak.archive.domain

/**
 * Business rule on the format of an archived document: its extension must name one of
 * [allowedFormats], the content type declared by the caller must match that format, and so must
 * the first bytes of the content when the format has a signature.
 */
class DocumentFormatPolicy(allowedFormats: Set<DocumentFormat> = DocumentFormat.entries.toSet()) {
    val allowedFormats: Set<DocumentFormat> = allowedFormats.toSet()

    init {
        require(this.allowedFormats.isNotEmpty()) { "At least one document format must be allowed" }
    }

    /** Returns the format of the document, given the first [DocumentFormat.HEADER_LENGTH] bytes of its content. */
    fun check(fileName: FileName, declaredContentType: String, contentHeader: ByteArray): DocumentFormat {
        val format = DocumentFormat.ofExtension(fileName.extension)?.takeIf { it in allowedFormats }
            ?: throw UnsupportedDocumentFormatException(fileName.value, allowedFormats.map { it.extension })
        if (mediaType(declaredContentType) !in format.contentTypes) throw ContentTypeMismatchException(declaredContentType, format)
        if (!format.matchesContent(contentHeader)) throw ContentMismatchException(format)
        return format
    }

    private fun mediaType(contentType: String): String = contentType.substringBefore(';').trim().lowercase()

    companion object {
        /** Builds the policy from configured extensions; an extension the service does not know is a configuration error. */
        fun ofExtensions(extensions: Collection<String>): DocumentFormatPolicy = DocumentFormatPolicy(
            extensions.map { extension ->
                DocumentFormat.ofExtension(extension.trim())
                    ?: throw IllegalArgumentException(
                        "Unknown document extension '$extension': supported extensions are " +
                            DocumentFormat.entries.joinToString { it.extension },
                    )
            }.toSet(),
        )
    }
}
