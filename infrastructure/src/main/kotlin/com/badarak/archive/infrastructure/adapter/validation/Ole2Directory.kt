package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.FileContent

/**
 * Names of the streams and storages of an OLE2 compound file (DOC, XLS, PPT, MSI…), read from its directory. The
 * directory sectors are followed through the allocation table one entry at a time: no stream content is ever read, and
 * the walk stops at the first sector seen twice or past a bound, so that a looping or oversized chain costs no more than
 * a sound one.
 */
internal object Ole2Directory {

    private const val HEADER_LENGTH = 512
    private const val ENTRY_LENGTH = 128
    private const val HEADER_FAT_ENTRIES = 109
    private const val MAX_DIRECTORY_SECTORS = 1_024
    private const val MAX_DIFAT_SECTORS = 64
    private const val END_OF_CHAIN = 0xFFFFFFFEL
    private const val LITTLE_ENDIAN = 0xFFFE

    /** Returns the names of the directory entries, or throws [MalformedOle2Exception] when the directory cannot be read. */
    fun names(content: FileContent, sizeBytes: Long): List<String> {
        val header = content.read(0, HEADER_LENGTH)
        if (header.size < HEADER_LENGTH || header.u16(0x1C) != LITTLE_ENDIAN) throw MalformedOle2Exception("its OLE2 header is invalid")
        val shift = header.u16(0x1E)
        if (shift != 9 && shift != 12) throw MalformedOle2Exception("its OLE2 header is invalid")
        val file = Sectors(content, sizeBytes, 1 shl shift)
        val fat = file.allocationTable(header)
        val names = mutableListOf<String>()
        var sector = header.u32(0x30)
        val visited = mutableSetOf<Long>()
        while (sector != END_OF_CHAIN) {
            if (!visited.add(sector) ||
                visited.size > MAX_DIRECTORY_SECTORS
            ) {
                throw MalformedOle2Exception("its OLE2 directory is corrupted")
            }
            val entries = file.sector(sector)
            (0 until entries.size / ENTRY_LENGTH).mapNotNullTo(names) { entryName(entries, it * ENTRY_LENGTH) }
            sector = file.next(fat, sector)
        }
        return names
    }

    private fun entryName(entries: ByteArray, offset: Int): String? {
        val type = entries[offset + 0x42].toInt()
        val length = entries.u16(offset + 0x40)
        if (type == 0 || length < 2 || length > 64) return null
        return String(entries, offset, length - 2, Charsets.UTF_16LE)
    }

    private class Sectors(private val content: FileContent, private val sizeBytes: Long, private val sectorSize: Int) {

        fun sector(number: Long): ByteArray {
            val offset = (number + 1) * sectorSize
            if (number < 0 || offset + sectorSize > sizeBytes) throw MalformedOle2Exception("its OLE2 directory is out of bounds")
            return content.read(offset, sectorSize)
        }

        /** The sectors holding the allocation table: listed in the header, then in the chained DIFAT sectors. */
        fun allocationTable(header: ByteArray): List<Long> {
            val count = header.u32(0x2C)
            val table = (0 until HEADER_FAT_ENTRIES).map { header.u32(0x4C + it * 4) }.toMutableList()
            var difat = header.u32(0x44)
            repeat(minOf(header.u32(0x48), MAX_DIFAT_SECTORS.toLong()).toInt()) {
                val sector = sector(difat)
                (0 until sectorSize / 4 - 1).mapTo(table) { sector.u32(it * 4) }
                difat = sector.u32(sectorSize - 4)
            }
            if (count > table.size) throw MalformedOle2Exception("its OLE2 allocation table is out of bounds")
            return table.take(count.toInt())
        }

        fun next(fat: List<Long>, sector: Long): Long {
            val perSector = sectorSize / 4
            val fatSector = fat.getOrNull((sector / perSector).toInt()) ?: throw MalformedOle2Exception("its OLE2 directory is corrupted")
            return sector(fatSector).u32((sector % perSector).toInt() * 4)
        }
    }
}

internal class MalformedOle2Exception(val reason: String) : RuntimeException(reason)
