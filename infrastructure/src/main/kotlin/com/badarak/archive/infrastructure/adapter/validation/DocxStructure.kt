package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.FileContent

/**
 * A Word document is a ZIP package holding the content types of its parts and the main document part; any other ZIP file
 * (an archive, a Java or Android application) lacks them. A package with a VBA project carries macros: it is a .docm file
 * under another name.
 */
internal object DocxStructure : StructureCheck {

    private const val CONTENT_TYPES = "[Content_Types].xml"
    private const val MAIN_DOCUMENT = "word/document.xml"
    private const val MACROS = "word/vbaProject.bin"

    override fun malformation(content: FileContent, sizeBytes: Long, header: ByteArray): String? {
        val names = try {
            ZipDirectory.names(content, sizeBytes).toSet()
        } catch (e: MalformedZipException) {
            return e.reason
        }
        return when {
            CONTENT_TYPES !in names || MAIN_DOCUMENT !in names -> "it is a ZIP file, but not a Word document"
            MACROS in names -> "it contains macros"
            else -> null
        }
    }
}
