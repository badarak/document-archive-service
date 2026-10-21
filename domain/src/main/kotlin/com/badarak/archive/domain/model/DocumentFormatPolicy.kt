package com.badarak.archive.domain.model

/**
 * Business rule on the format of an archived document: its extension must name one of
 * [allowedFormats], the content type declared by the caller must match that format, and so must
 * the first [DocumentFormat.HEADER_LENGTH] bytes of the content when the format has a signature.
 *
 * Built from configuration with [ofExtensions], where an extension the service does not know is an error.
 */
class DocumentFormatPolicy(allowedFormats: Set<DocumentFormat> = DocumentFormat.entries.toSet()) {
    val allowedFormats: Set<DocumentFormat> = allowedFormats.toSet()

    init {
        require(this.allowedFormats.isNotEmpty()) { "At least one document format must be allowed" }
    }

    /**
     * Checks a document against the rule and returns its declared content type reduced to its media type, as it is stored:
     * without parameters, surrounding spaces nor upper case.
     *
     * @throws UnsupportedDocumentFormatException when the extension names no allowed format
     * @throws ContentTypeMismatchException when the declared content type does not match the format
     * @throws ContentMismatchException when the content does not match the format
     */
    fun check(fileName: FileName, declaredContentType: String, contentHeader: ByteArray): String {
        val format = DocumentFormat.ofExtension(fileName.extension)?.takeIf { it in allowedFormats }
            ?: throw UnsupportedDocumentFormatException(fileName.value, allowedFormats.map { it.extension })
        val mediaType = declaredContentType.substringBefore(';').trim().lowercase()
        if (mediaType !in format.contentTypes) throw ContentTypeMismatchException(declaredContentType, format)
        if (!format.matchesContent(contentHeader)) throw ContentMismatchException(format)
        return mediaType
    }

    companion object {
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
