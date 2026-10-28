package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.FileContent

internal fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

internal fun ByteArray.hasAt(offset: Int, expected: String): Boolean = hasAt(offset, expected.toByteArray(Charsets.US_ASCII))

internal fun ByteArray.hasAt(offset: Int, expected: ByteArray): Boolean =
    offset >= 0 && size >= offset + expected.size && expected.indices.all { this[offset + it] == expected[it] }

/** Reads at most [length] bytes from [offset]: the bytes before it are skipped, which a file-backed stream does by seeking. */
internal fun FileContent.read(offset: Long, length: Int): ByteArray = open().use { stream ->
    stream.skipNBytes(offset)
    stream.readNBytes(length)
}

/** Reads the last [length] bytes of a content of [sizeBytes] bytes, or all of them when it is shorter. */
internal fun FileContent.tail(sizeBytes: Long, length: Int): ByteArray = read(maxOf(0, sizeBytes - length), length)

internal fun ByteArray.u16(offset: Int): Int = (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

internal fun ByteArray.u32(offset: Int): Long = u16(offset).toLong() or (u16(offset + 2).toLong() shl 16)

internal fun ByteArray.lastIndexOf(expected: ByteArray): Int = (size - expected.size downTo 0).firstOrNull { hasAt(it, expected) } ?: -1
