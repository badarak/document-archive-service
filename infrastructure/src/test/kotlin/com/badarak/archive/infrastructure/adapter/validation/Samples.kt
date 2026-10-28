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

    fun ascii(value: String): ByteArray = value.toByteArray(Charsets.US_ASCII)

    fun ByteArray.asContent(): FileContent = FileContent { inputStream() }
}
