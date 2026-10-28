package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.FileContent

/** Checks the structure of a file whose signature matched its format, reading only bounded parts of it. */
internal fun interface StructureCheck {
    /** Returns why the file is malformed, or null when its structure is sound. */
    fun malformation(content: FileContent, sizeBytes: Long, header: ByteArray): String?
}
