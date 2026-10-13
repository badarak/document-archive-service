package com.badarak.archive.domain

/**
 * Business category of a document (INVOICE, CONTRACT, ...), chosen by the calling system.
 * It is independent of the technical content type: an INVOICE may be a PDF or an image.
 * The set of types is open, so only the format is validated.
 */
@JvmInline
value class DocumentType(val value: String) {

    init {
        if (!FORMAT.matches(value)) throw InvalidDocumentTypeException(value)
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 50
        const val FORMAT_DESCRIPTION =
            "an uppercase letter followed by uppercase letters, digits or underscores, $MAX_LENGTH characters at most"

        private val FORMAT = Regex("[A-Z][A-Z0-9_]{0,${MAX_LENGTH - 1}}")
    }
}
