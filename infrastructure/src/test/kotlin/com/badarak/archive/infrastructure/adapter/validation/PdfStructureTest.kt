package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.infrastructure.adapter.validation.Samples.asContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PdfStructureTest {

    @Test
    fun `a pdf ending with its end-of-file marker is sound`() {
        assertNull(check(Samples.pdf()))
    }

    @Test
    fun `a pdf followed by a few trailing bytes after its marker is sound, as readers accept it`() {
        assertNull(check(Samples.pdf() + ByteArray(500)))
    }

    @Test
    fun `a pdf whose marker is not in its last kilobyte is malformed`() {
        assertEquals("it has no end-of-file marker", check(Samples.pdf() + ByteArray(2_000)))
    }

    @Test
    fun `a truncated pdf is malformed`() {
        val pdf = Samples.pdf()

        assertEquals("it has no end-of-file marker", check(pdf.copyOf(pdf.size - 8)))
    }

    private fun check(pdf: ByteArray): String? = PdfStructure.malformation(pdf.asContent(), pdf.size.toLong(), pdf)
}
