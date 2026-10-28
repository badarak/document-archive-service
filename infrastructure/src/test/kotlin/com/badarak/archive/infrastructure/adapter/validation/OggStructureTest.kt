package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.infrastructure.adapter.validation.Samples.asContent
import com.badarak.archive.infrastructure.adapter.validation.Samples.ascii
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OggStructureTest {

    @Test
    fun `a stream of an audio codec is sound`() {
        listOf(bytes(0x01) + ascii("vorbis"), ascii("OpusHead"), bytes(0x7F) + ascii("FLAC"), ascii("Speex   ")).forEach {
            assertNull(check(Samples.ogg(it)), String(it))
        }
    }

    @Test
    fun `a video stream is refused`() {
        assertEquals("its codec is not an audio one", check(Samples.ogg(bytes(0x80) + ascii("theora"))))
    }

    @Test
    fun `a first page that does not open a stream is malformed`() {
        val ogg = Samples.ogg().also { it[5] = 0 }

        assertEquals("its first page does not open a stream", check(ogg))
    }

    @Test
    fun `a page of an unknown version is malformed`() {
        val ogg = Samples.ogg().also { it[4] = 1 }

        assertEquals("its first page does not open a stream", check(ogg))
    }

    @Test
    fun `a page cut before its header ends is malformed`() {
        assertEquals("its first page does not open a stream", check(Samples.ogg().copyOf(20)))
    }

    private fun check(ogg: ByteArray): String? = OggStructure.malformation(ogg.asContent(), ogg.size.toLong(), ogg.copyOf(64))
}
