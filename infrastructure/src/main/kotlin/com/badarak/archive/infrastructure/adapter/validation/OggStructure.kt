package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.FileContent

/**
 * An Ogg file starts with a page that opens its stream, whose first packet names its codec. The audio codecs (Vorbis,
 * Opus, FLAC, Speex) are accepted; a video (Theora) or any other codec shares the container but is no audio.
 */
internal object OggStructure : StructureCheck {

    private const val PAGE_HEADER = 27
    private const val BEGINNING_OF_STREAM = 0x02
    private val AUDIO_CODECS = listOf(
        bytes(0x01) + "vorbis".toByteArray(),
        "OpusHead".toByteArray(),
        bytes(0x7F) + "FLAC".toByteArray(),
        "Speex   ".toByteArray(),
    )

    override fun malformation(content: FileContent, sizeBytes: Long, header: ByteArray): String? {
        val page = content.read(0, PAGE_HEADER + 255 + 8)
        if (page.size < PAGE_HEADER || page[4].toInt() != 0 || page[5].toInt() and BEGINNING_OF_STREAM == 0) {
            return "its first page does not open a stream"
        }
        val packet = PAGE_HEADER + (page[26].toInt() and 0xFF)
        return if (AUDIO_CODECS.any { page.hasAt(packet, it) }) null else "its codec is not an audio one"
    }
}
