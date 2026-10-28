package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.infrastructure.adapter.validation.Samples.asContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class M4aStructureTest {

    @ParameterizedTest
    @ValueSource(strings = ["M4A ", "M4B ", "isom", "mp42"])
    fun `an audio or generic MPEG-4 brand is sound`(major: String) {
        assertNull(check(Samples.m4a(major)))
    }

    @Test
    fun `another major brand is sound when an audio brand is compatible`() {
        assertNull(check(Samples.m4a("dash", "iso6", "M4A ")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["qt  ", "heic", "3gp5", "avif"])
    fun `a video, image or 3GPP brand is refused`(major: String) {
        assertEquals("its brand '${major.trim()}' is not an audio one", check(Samples.m4a(major, major)))
    }

    @Test
    fun `a file type box larger than the file is invalid`() {
        val m4a = Samples.m4a().also { it[2] = 0x7F }

        assertEquals("its file type box is invalid", check(m4a))
    }

    @Test
    fun `a file type box too small to hold its brand is invalid`() {
        val m4a = Samples.m4a().also { it[3] = 8 }

        assertEquals("its file type box is invalid", check(m4a))
    }

    private fun check(m4a: ByteArray): String? = M4aStructure.malformation(m4a.asContent(), m4a.size.toLong(), m4a.copyOf(64))
}
