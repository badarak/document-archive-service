package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.FileContent
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Smallest well-formed files of each format, built in memory, and helpers to inspect them. */
internal object Samples {

    fun pdf(): ByteArray = ascii("%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n")

    fun docx(vararg extraEntries: String): ByteArray = zip("[Content_Types].xml", "word/document.xml", *extraEntries)

    fun zip(vararg entries: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write("<xml/>".toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    fun eml(): ByteArray = ascii(
        "Received: from mail.acme.test\r\n\tby mx.acme.test; Mon, 26 Oct 2026 09:00:00 +0000\r\n" +
            "From: Alice <alice@acme.test>\r\nTo: bob@acme.test\r\nDate: Mon, 26 Oct 2026 09:00:00 +0000\r\n" +
            "Subject: Invoice\r\n\r\nPlease find the invoice attached.\r\n",
    )

    /** MPEG-1 Layer III frames at 128 kbit/s and 44.1 kHz, 417 bytes each, after an ID3v2 tag when [tagged]. */
    fun mp3(frames: Int = 3, tagged: Boolean = true): ByteArray {
        val tag = if (tagged) ascii("ID3") + bytes(4, 0, 0, 0, 0, 0, 20) + ByteArray(20) else ByteArray(0)
        val frame = bytes(0xFF, 0xFB, 0x90, 0x00) + ByteArray(413)
        return tag + (1..frames).fold(ByteArray(0)) { audio, _ -> audio + frame }
    }

    /** A 16-bit mono PCM WAV file at 8 kHz, with [beforeFormat] chunks before its format chunk. */
    fun wav(vararg beforeFormat: ByteArray): ByteArray {
        val format = ascii("fmt ") + le32(16) + le16(1) + le16(1) + le32(8_000) + le32(16_000) + le16(2) + le16(16)
        val data = ascii("data") + le32(4) + ByteArray(4)
        val chunks = beforeFormat.fold(ByteArray(0)) { all, chunk -> all + chunk } + format + data
        return ascii("RIFF") + le32(4 + chunks.size) + ascii("WAVE") + chunks
    }

    fun chunk(id: String, content: ByteArray): ByteArray = ascii(id) + le32(content.size) + content + ByteArray(content.size % 2)

    /** An MPEG-4 file type box with [major] and [compatible] brands, followed by some media data. */
    fun m4a(major: String = "M4A ", vararg compatible: String = arrayOf("M4A ", "mp42", "isom")): ByteArray {
        val brands = compatible.fold(ByteArray(0)) { all, brand -> all + ascii(brand) }
        val ftyp = be32(16 + brands.size) + ascii("ftyp") + ascii(major) + ByteArray(4) + brands
        return ftyp + be32(16) + ascii("mdat") + ByteArray(8)
    }

    fun le16(value: Int): ByteArray = bytes(value and 0xFF, (value shr 8) and 0xFF)

    fun le32(value: Int): ByteArray = le16(value and 0xFFFF) + le16(value ushr 16)

    private fun be32(value: Int): ByteArray = bytes(value ushr 24, (value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF)

    fun ascii(value: String): ByteArray = value.toByteArray(Charsets.US_ASCII)

    fun ByteArray.asContent(): FileContent = FileContent { inputStream() }
}
