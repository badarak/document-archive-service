package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.FileContent

/**
 * Names of the entries of a ZIP file, read from its central directory at the end of the file. No entry is ever
 * decompressed, so that a ZIP bomb costs no more than any other file.
 */
internal object ZipDirectory {

    private const val END_RECORD_LENGTH = 22
    private const val MAX_COMMENT_LENGTH = 0xFFFF
    private const val ENTRY_HEADER_LENGTH = 46
    private const val MAX_DIRECTORY_SIZE = 4 * 1024 * 1024
    private const val MAX_ENTRIES = 10_000
    private val END_RECORD = bytes(0x50, 0x4B, 0x05, 0x06)
    private val ENTRY = bytes(0x50, 0x4B, 0x01, 0x02)

    /** Returns the names of the entries, or throws [MalformedZipException] when the directory cannot be read. */
    fun names(content: FileContent, sizeBytes: Long): List<String> {
        val tail = content.tail(sizeBytes, END_RECORD_LENGTH + MAX_COMMENT_LENGTH)
        val end = tail.lastIndexOf(END_RECORD).takeIf { it >= 0 && it + END_RECORD_LENGTH <= tail.size }
            ?: throw MalformedZipException("its ZIP directory is missing")
        val entries = tail.u16(end + 10)
        val directorySize = tail.u32(end + 12)
        val directoryOffset = tail.u32(end + 16)
        if (entries == 0xFFFF || directorySize == 0xFFFFFFFFL || directoryOffset == 0xFFFFFFFFL) {
            throw MalformedZipException("it uses the ZIP64 extension, which a document of this size never needs")
        }
        if (entries > MAX_ENTRIES || directorySize > MAX_DIRECTORY_SIZE || directoryOffset + directorySize > sizeBytes) {
            throw MalformedZipException("its ZIP directory is out of bounds")
        }
        return readEntries(content.read(directoryOffset, directorySize.toInt()), entries)
    }

    private fun readEntries(directory: ByteArray, entries: Int): List<String> {
        var offset = 0
        return List(entries) {
            if (offset + ENTRY_HEADER_LENGTH > directory.size || !directory.hasAt(offset, ENTRY)) {
                throw MalformedZipException("its ZIP directory is corrupted")
            }
            val nameLength = directory.u16(offset + 28)
            val next = offset + ENTRY_HEADER_LENGTH + nameLength + directory.u16(offset + 30) + directory.u16(offset + 32)
            if (next > directory.size) throw MalformedZipException("its ZIP directory is corrupted")
            String(directory, offset + ENTRY_HEADER_LENGTH, nameLength, Charsets.UTF_8).also { offset = next }
        }
    }
}

internal class MalformedZipException(val reason: String) : RuntimeException(reason)
