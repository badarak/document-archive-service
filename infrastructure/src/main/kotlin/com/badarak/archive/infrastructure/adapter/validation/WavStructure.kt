package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.FileContent

/**
 * A WAV file is a RIFF file whose chunks describe the audio (`fmt `: an encoding, at least one channel, a sample rate)
 * before holding it (`data`). The chunks are walked from header to header, without reading their content.
 */
internal object WavStructure : StructureCheck {

    private const val FIRST_CHUNK = 12L
    private const val CHUNK_HEADER = 8
    private const val MAX_CHUNKS = 64

    override fun malformation(content: FileContent, sizeBytes: Long, header: ByteArray): String? {
        var offset = FIRST_CHUNK
        var described = false
        repeat(MAX_CHUNKS) {
            if (offset + CHUNK_HEADER > sizeBytes) return "it holds no audio data"
            val chunk = content.read(offset, CHUNK_HEADER + 16)
            val size = chunk.u32(4)
            when (String(chunk, 0, 4, Charsets.ISO_8859_1)) {
                "fmt " -> {
                    if (size < 16 || !chunk.describesAudio()) return "its audio format is invalid"
                    described = true
                }

                "data" -> return if (described) null else "its audio data comes before its format"
            }
            offset += CHUNK_HEADER + size + (size and 1)
        }
        return "it holds no audio data"
    }

    private fun ByteArray.describesAudio(): Boolean = size >= CHUNK_HEADER + 8 && u16(8) != 0 && u16(10) > 0 && u32(12) > 0
}
