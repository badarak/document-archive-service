package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.FileContent

/**
 * An M4A file is an MPEG-4 file whose `ftyp` box names an audio brand, or a generic MPEG-4 one: a QuickTime video, a HEIC
 * image or a 3GPP file shares the container but not the brands.
 */
internal object M4aStructure : StructureCheck {

    private const val MAX_BOX_SIZE = 4096
    private val AUDIO_BRANDS = setOf("M4A ", "M4B ")
    private val GENERIC_BRANDS = setOf("isom", "iso2", "mp41", "mp42")

    override fun malformation(content: FileContent, sizeBytes: Long, header: ByteArray): String? {
        val boxSize = header.u32BigEndian(0)
        if (boxSize < 16 || boxSize > MAX_BOX_SIZE || boxSize > sizeBytes) return "its file type box is invalid"
        val box = content.read(0, boxSize.toInt())
        val major = box.brand(8)
        val compatible = (16 until box.size - 3 step 4).map { box.brand(it) }
        return when {
            major in AUDIO_BRANDS || major in GENERIC_BRANDS || compatible.any { it in AUDIO_BRANDS } -> null
            else -> "its brand '${major.trim()}' is not an audio one"
        }
    }

    private fun ByteArray.brand(offset: Int): String = String(this, offset, 4, Charsets.ISO_8859_1)

    private fun ByteArray.u32BigEndian(offset: Int): Long =
        (0 until 4).fold(0L) { value, index -> (value shl 8) or (this[offset + index].toLong() and 0xFF) }
}
