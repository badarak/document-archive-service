package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.infrastructure.adapter.validation.Samples.asContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DocxStructureTest {

    @Test
    fun `a package with its content types and main document is a Word document`() {
        assertNull(check(Samples.docx("word/styles.xml", "docProps/core.xml")))
    }

    @Test
    fun `another ZIP file, such as a Java application, is not a Word document`() {
        assertEquals("it is a ZIP file, but not a Word document", check(Samples.zip("META-INF/MANIFEST.MF", "App.class")))
    }

    @Test
    fun `a spreadsheet, which has content types but no Word document, is not a Word document`() {
        assertEquals("it is a ZIP file, but not a Word document", check(Samples.zip("[Content_Types].xml", "xl/workbook.xml")))
    }

    @Test
    fun `a Word document with macros is refused`() {
        assertEquals("it contains macros", check(Samples.docx("word/vbaProject.bin")))
    }

    @Test
    fun `a truncated package, without its directory, is malformed`() {
        val docx = Samples.docx()

        assertEquals("its ZIP directory is missing", check(docx.copyOf(docx.size / 2)))
    }

    @Test
    fun `a directory pointing outside the file is malformed`() {
        val docx = Samples.docx()
        val end = docx.size - 22
        docx[end + 19] = 0x7F

        assertEquals("its ZIP directory is out of bounds", check(docx))
    }

    @Test
    fun `a ZIP64 package is refused, as a document of this size never needs it`() {
        val docx = Samples.docx()
        val end = docx.size - 22
        docx[end + 10] = 0xFF.toByte()
        docx[end + 11] = 0xFF.toByte()

        assertEquals("it uses the ZIP64 extension, which a document of this size never needs", check(docx))
    }

    @Test
    fun `a corrupted directory is malformed`() {
        val docx = Samples.docx()
        val directory = docx.size - 22 - docx.u32(docx.size - 22 + 12).toInt()
        docx[directory] = 0

        assertEquals("its ZIP directory is corrupted", check(docx))
    }

    private fun check(docx: ByteArray): String? = DocxStructure.malformation(docx.asContent(), docx.size.toLong(), docx)
}
