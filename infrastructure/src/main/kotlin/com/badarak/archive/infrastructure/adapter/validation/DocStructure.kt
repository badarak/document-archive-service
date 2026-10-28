package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.FileContent

/**
 * A Word 97-2003 document is an OLE2 compound file holding a `WordDocument` stream; the other files sharing the container
 * (an MSI installer, an Excel or PowerPoint file) lack it. A document holding a `Macros` storage carries macros.
 */
internal object DocStructure : StructureCheck {

    private const val MAIN_STREAM = "WordDocument"
    private const val MACROS = "Macros"

    override fun malformation(content: FileContent, sizeBytes: Long, header: ByteArray): String? {
        val names = try {
            Ole2Directory.names(content, sizeBytes).toSet()
        } catch (e: MalformedOle2Exception) {
            return e.reason
        }
        return when {
            MAIN_STREAM !in names -> "it is an OLE2 file, but not a Word document"
            MACROS in names -> "it contains macros"
            else -> null
        }
    }
}
