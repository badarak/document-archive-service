package com.badarak.archive.domain.model

/**
 * Business rule on the format of an archived document: its extension must name one of
 * [allowedFormats], and the first [DocumentFormat.HEADER_LENGTH] bytes of the content must match
 * that format when it has a signature. The content type declared by the caller must match the
 * format too, unless [rejectMimeMismatch] is off: a wrong declaration is then replaced by the media
 * type of the format. The content itself is never let through when it does not match.
 *
 * Built from configuration with [ofExtensions], where an extension the service does not know is an error.
 */
class DocumentFormatPolicy(allowedFormats: Set<DocumentFormat> = DocumentFormat.entries.toSet(), val rejectMimeMismatch: Boolean = true) {
    val allowedFormats: Set<DocumentFormat> = allowedFormats.toSet()

    init {
        require(this.allowedFormats.isNotEmpty()) { "At least one document format must be allowed" }
    }

    /**
     * Checks a document against the rule and returns the media type to store: the declared content type without
     * parameters, surrounding spaces nor upper case, or the media type of the format when a mismatching declaration is
     * tolerated.
     *
     * @throws UnsupportedDocumentFormatException when the extension names no allowed format
     * @throws ContentTypeMismatchException when the declared content type does not match the format, and that is rejected
     * @throws ContentMismatchException when the content does not match the format
     */
    fun check(fileName: FileName, declaredContentType: String, contentHeader: ByteArray): String {
        val format = DocumentFormat.ofExtension(fileName.extension)?.takeIf { it in allowedFormats }
            ?: throw UnsupportedDocumentFormatException(fileName.value, allowedFormats.map { it.extension })
        if (!format.matchesContent(contentHeader)) throw ContentMismatchException(format)
        val mediaType = declaredContentType.substringBefore(';').trim().lowercase()
        return when {
            mediaType in format.contentTypes -> mediaType
            rejectMimeMismatch -> throw ContentTypeMismatchException(declaredContentType, format)
            else -> format.mediaType
        }
    }

    companion object {
        fun ofExtensions(extensions: Collection<String>, rejectMimeMismatch: Boolean = true): DocumentFormatPolicy = DocumentFormatPolicy(
            extensions.map { extension ->
                DocumentFormat.ofExtension(extension.trim())
                    ?: throw IllegalArgumentException(
                        "Unknown document extension '$extension': supported extensions are " +
                            DocumentFormat.entries.joinToString { it.extension },
                    )
            }.toSet(),
            rejectMimeMismatch,
        )
    }
}
