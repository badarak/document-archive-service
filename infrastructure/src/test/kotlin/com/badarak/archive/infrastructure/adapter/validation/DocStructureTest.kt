package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.infrastructure.adapter.validation.Samples.asContent
import com.badarak.archive.infrastructure.adapter.validation.Samples.le32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DocStructureTest {

    @Test
    fun `a compound file holding a WordDocument stream is a Word document`() {
        assertNull(check(Samples.ole2("WordDocument", "1Table", "\u0005SummaryInformation")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["Workbook", "PowerPoint Document", "䡀㬿䕷䐬䠶"])
    fun `an Excel, PowerPoint or MSI file is not a Word document`(stream: String) {
        assertEquals("it is an OLE2 file, but not a Word document", check(Samples.ole2(stream)))
    }

    @Test
    fun `a Word document with macros is refused`() {
        assertEquals("it contains macros", check(Samples.ole2("WordDocument", "Macros")))
    }

    @Test
    fun `a header in another byte order is invalid`() {
        val doc = Samples.ole2().also { it[0x1C] = 0 }

        assertEquals("its OLE2 header is invalid", check(doc))
    }

    @Test
    fun `a header with an unknown sector size is invalid`() {
        val doc = Samples.ole2().also { it[0x1E] = 10 }

        assertEquals("its OLE2 header is invalid", check(doc))
    }

    @Test
    fun `a directory beyond the end of the file is out of bounds`() {
        val doc = Samples.ole2().also { le32(40).copyInto(it, 0x30) }

        assertEquals("its OLE2 directory is out of bounds", check(doc))
    }

    @Test
    fun `a truncated file is out of bounds`() {
        val doc = Samples.ole2()

        assertEquals("its OLE2 directory is out of bounds", check(doc.copyOf(doc.size - 100)))
    }

    @Test
    fun `a directory chain looping on itself is corrupted, and its walk ends`() {
        val doc = Samples.ole2().also { le32(1).copyInto(it, 512 + 4) }

        assertEquals("its OLE2 directory is corrupted", check(doc))
    }

    @Test
    fun `an allocation table larger than listed is out of bounds`() {
        val doc = Samples.ole2().also { le32(500).copyInto(it, 0x2C) }

        assertEquals("its OLE2 allocation table is out of bounds", check(doc))
    }

    private fun check(doc: ByteArray): String? = DocStructure.malformation(doc.asContent(), doc.size.toLong(), doc.copyOf(64))
}
