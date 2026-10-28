package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.infrastructure.adapter.validation.Samples.asContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class Mp3StructureTest {

    @Test
    fun `frames following each other after an ID3 tag are sound`() {
        assertNull(check(Samples.mp3()))
    }

    @Test
    fun `frames without ID3 tag are sound`() {
        assertNull(check(Samples.mp3(tagged = false)))
    }

    @Test
    fun `a single frame filling the file is sound`() {
        assertNull(check(Samples.mp3(frames = 1, tagged = false)))
    }

    @Test
    fun `a few padding bytes before the first frame are tolerated`() {
        val tag = Samples.mp3(frames = 0)
        val audio = Samples.mp3(tagged = false)

        assertNull(check(tag + ByteArray(100) + audio))
    }

    @Test
    fun `a file whose second frame is missing where the first one ends is malformed`() {
        val frame = Samples.mp3(frames = 1, tagged = false)

        assertEquals("it holds no audio frame", check(frame + ByteArray(1_000)))
    }

    @Test
    fun `a frame start with a reserved bitrate is not a frame`() {
        val reserved = Samples.mp3(frames = 1, tagged = false).also { it[2] = 0xF0.toByte() }

        assertEquals("it holds no audio frame", check(reserved))
    }

    @Test
    fun `an ID3 tag followed by anything but audio is malformed`() {
        assertEquals("it holds no audio frame", check(Samples.mp3(frames = 0) + Samples.ascii("not audio at all").copyOf(10_000)))
    }

    @Test
    fun `an ID3 tag announcing more bytes than the file holds is malformed`() {
        assertEquals("it holds no audio frame", check(Samples.ascii("ID3") + bytes(4, 0, 0, 0x7F, 0x7F, 0x7F, 0x7F)))
    }

    private fun check(mp3: ByteArray): String? = Mp3Structure.malformation(mp3.asContent(), mp3.size.toLong(), mp3.copyOf(64))
}
