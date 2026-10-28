package com.badarak.archive.domain.model

import java.text.Normalizer

/**
 * Original name of a document, as shown back to callers. It is informative only: the object
 * storage key never derives from it.
 *
 * [of] refuses a name that could mislead or reach another place once shown or saved by a client:
 * a path (`/`, `\`, `.` or `..`), a control character, or an invisible Unicode format character,
 * such as the right-to-left override that displays `invoice\u202Efdp.exe` as `invoiceexe.pdf`. The
 * accepted name is put in Unicode NFC form, and its surrounding spaces are trimmed.
 */
@JvmInline
value class FileName private constructor(val value: String) {

    val extension: String
        get() = value.substringAfterLast('.', "").lowercase()

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = StoredFile.FILE_NAME_MAX_LENGTH

        fun of(raw: String): FileName {
            val name = Normalizer.normalize(raw, Normalizer.Form.NFC).trim()
            if (name.isEmpty()) throw InvalidDocumentException("fileName", "must contain a file name")
            if (name == "." || name == ".." || name.any { it == '/' || it == '\\' }) {
                throw InvalidDocumentException("fileName", "must not contain a path")
            }
            if (name.any { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }) {
                throw InvalidDocumentException("fileName", "must not contain control or invisible formatting characters")
            }
            if (name.length > MAX_LENGTH) throw InvalidDocumentException("fileName", "must not exceed $MAX_LENGTH characters")
            return FileName(name)
        }
    }
}
