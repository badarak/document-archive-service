package com.badarak.archive.domain.model

import java.text.Normalizer

/**
 * Original name of a document, as shown back to callers. It is informative only: the object
 * storage key never derives from it.
 *
 * [of] normalises the name sent by the client: any directory path is dropped, the text is put in
 * Unicode NFC form, control characters are removed and surrounding spaces are trimmed.
 */
@JvmInline
value class FileName private constructor(val value: String) {

    /** Lowercase extension without the dot, or an empty string when the name has none. */
    val extension: String
        get() = value.substringAfterLast('.', "").lowercase()

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = ArchivedDocument.FILE_NAME_MAX_LENGTH

        fun of(raw: String): FileName {
            val name = Normalizer.normalize(raw.substringAfterLast('/').substringAfterLast('\\'), Normalizer.Form.NFC)
                .filterNot { it.isISOControl() }
                .trim()
            if (name.isEmpty() || name == "." || name == "..") throw InvalidDocumentException("fileName", "must contain a file name")
            if (name.length > MAX_LENGTH) throw InvalidDocumentException("fileName", "must not exceed $MAX_LENGTH characters")
            return FileName(name)
        }
    }
}
