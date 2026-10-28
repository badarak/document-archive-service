package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.FileContent

/**
 * An MP3 file is a sequence of MPEG audio frames, after an optional ID3v2 tag. Its first frame must be found shortly
 * after the tag, with a valid header (version, layer, bitrate, sample rate), and must be followed by another valid
 * frame where its length says: a single pair of bytes looking like a frame start is not enough.
 */
internal object Mp3Structure : StructureCheck {

    private const val ID3_HEADER = 10
    private const val SEARCH_WINDOW = 8 * 1024
    private const val MAX_FRAME_LENGTH = 2_881

    private val BITRATES_V1 = listOf(
        intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448),
        intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384),
        intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320),
    )
    private val BITRATES_V2 = listOf(
        intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256),
        intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160),
        intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160),
    )
    private val SAMPLE_RATES = mapOf(
        3 to intArrayOf(44_100, 48_000, 32_000),
        2 to intArrayOf(22_050, 24_000, 16_000),
        0 to intArrayOf(11_025, 12_000, 8_000),
    )

    override fun malformation(content: FileContent, sizeBytes: Long, header: ByteArray): String? {
        val audioStart = if (header.hasAt(0, "ID3")) tagLength(header) else 0L
        if (audioStart >= sizeBytes) return "it holds no audio frame"
        val window = content.read(audioStart, SEARCH_WINDOW + 2 * MAX_FRAME_LENGTH)
        val found = (0 until minOf(SEARCH_WINDOW, window.size)).any { offset ->
            val length = frameLength(window, offset) ?: return@any false
            val lastFrame = audioStart + offset + length >= sizeBytes
            lastFrame || frameLength(window, offset + length) != null
        }
        return if (found) null else "it holds no audio frame"
    }

    private fun tagLength(header: ByteArray): Long {
        val size = (6 until 10).fold(0L) { value, index -> (value shl 7) or (header[index].toLong() and 0x7F) }
        val footer = if (header[5].toInt() and 0x10 != 0) ID3_HEADER else 0
        return ID3_HEADER + size + footer
    }

    /** Returns the length of the frame whose header starts at [offset], or null when no valid header starts there. */
    private fun frameLength(bytes: ByteArray, offset: Int): Int? {
        if (offset + 4 > bytes.size || bytes[offset] != 0xFF.toByte()) return null
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        val version = (b1 shr 3) and 3
        val layer = 4 - ((b1 shr 1) and 3)
        val bitrateIndex = b2 shr 4
        val sampleRateIndex = (b2 shr 2) and 3
        val reserved = version == 1 || layer == 4 || bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3
        if (b1 and 0xE0 != 0xE0 || reserved) return null
        val bitrate = (if (version == 3) BITRATES_V1 else BITRATES_V2)[layer - 1][bitrateIndex] * 1000
        val sampleRate = SAMPLE_RATES.getValue(version)[sampleRateIndex]
        val padding = (b2 shr 1) and 1
        return when {
            layer == 1 -> (12 * bitrate / sampleRate + padding) * 4
            layer == 3 && version != 3 -> 72 * bitrate / sampleRate + padding
            else -> 144 * bitrate / sampleRate + padding
        }
    }
}
