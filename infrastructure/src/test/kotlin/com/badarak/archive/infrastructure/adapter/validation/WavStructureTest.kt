package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.infrastructure.adapter.validation.Samples.asContent
import com.badarak.archive.infrastructure.adapter.validation.Samples.ascii
import com.badarak.archive.infrastructure.adapter.validation.Samples.le32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WavStructureTest {

    @Test
    fun `a format chunk followed by audio data is sound`() {
        assertNull(check(Samples.wav()))
    }

    @Test
    fun `other chunks, even of odd size, may come before the format`() {
        assertNull(check(Samples.wav(Samples.chunk("LIST", ByteArray(31)), Samples.chunk("bext", ByteArray(602)))))
    }

    @Test
    fun `a RIFF file without audio data is malformed`() {
        val wav = Samples.wav()

        assertEquals("it holds no audio data", check(wav.copyOf(wav.size - 12)))
    }

    @Test
    fun `audio data before its format is malformed`() {
        val wav = ascii("RIFF") + le32(16) + ascii("WAVE") + Samples.chunk("data", ByteArray(4))

        assertEquals("its audio data comes before its format", check(wav))
    }

    @Test
    fun `a format without channel is invalid`() {
        val wav = Samples.wav().also { it[12 + 8 + 2] = 0 }

        assertEquals("its audio format is invalid", check(wav))
    }

    @Test
    fun `a format too short to describe the audio is invalid`() {
        val wav = ascii("RIFF") + le32(4) + ascii("WAVE") + Samples.chunk("fmt ", ByteArray(4)) + Samples.chunk("data", ByteArray(4))

        assertEquals("its audio format is invalid", check(wav))
    }

    private fun check(wav: ByteArray): String? = WavStructure.malformation(wav.asContent(), wav.size.toLong(), wav.copyOf(64))
}
