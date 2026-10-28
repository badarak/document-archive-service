package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.ContentInspection
import com.badarak.archive.domain.model.DocumentFormat
import com.badarak.archive.domain.model.FileContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.InputStream

class ContentInspectorAdapterTest {

    private val inspector = ContentInspectorAdapter()

    @Test
    fun `accepts a well-formed file of each format`() {
        val files = mapOf(
            DocumentFormat.PDF to Samples.pdf(),
            DocumentFormat.DOC to bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1, 0, 0, 0, 0),
            DocumentFormat.DOCX to Samples.docx(),
            DocumentFormat.EML to Samples.eml(),
            DocumentFormat.MP3 to Samples.mp3(),
            DocumentFormat.WAV to Samples.wav(),
            DocumentFormat.M4A to Samples.m4a(),
        )

        files.forEach { (format, file) -> assertEquals(ContentInspection.Conforming, inspect(file, format), "$format") }
    }

    @Test
    fun `recognises an mp3 file without ID3 tag from its frame header`() {
        assertEquals(ContentInspection.Conforming, inspect(Samples.mp3(tagged = false), DocumentFormat.MP3))
    }

    @ParameterizedTest
    @EnumSource(DocumentFormat::class, names = ["EML"], mode = EnumSource.Mode.EXCLUDE)
    fun `rejects content that carries another signature`(format: DocumentFormat) {
        assertEquals(ContentInspection.OtherFormat(null), inspect(ascii("GIF89a......"), format))
    }

    @ParameterizedTest
    @EnumSource(DocumentFormat::class, names = ["EML"], mode = EnumSource.Mode.EXCLUDE)
    fun `rejects content too short to carry the signature`(format: DocumentFormat) {
        assertEquals(ContentInspection.OtherFormat(null), inspect(ascii("%P"), format))
    }

    @Test
    fun `wav needs both the RIFF container and the WAVE type`() {
        val video = ascii("RIFF") + bytes(0x24, 0x08, 0, 0) + ascii("AVI ")

        assertEquals(ContentInspection.OtherFormat(null), inspect(video, DocumentFormat.WAV))
    }

    @ParameterizedTest
    @EnumSource(DocumentFormat::class)
    fun `never takes a Windows program for a document, whatever its format`(format: DocumentFormat) {
        val program = ascii("MZ") + ByteArray(62)

        assertEquals(ContentInspection.OtherFormat("application/vnd.microsoft.portable-executable"), inspect(program, format))
    }

    @Test
    fun `recognises the programs of every system and scripts`() {
        mapOf(
            bytes(0x7F, 0x45, 0x4C, 0x46, 2, 1) to "application/x-executable",
            bytes(0xCF, 0xFA, 0xED, 0xFE, 7, 0) to "application/x-mach-binary",
            bytes(0xFE, 0xED, 0xFA, 0xCE, 0, 0) to "application/x-mach-binary",
            bytes(0xCA, 0xFE, 0xBA, 0xBE, 0, 0) to "application/x-mach-binary",
            ascii("#!/bin/sh\nrm -rf /") to "text/x-script",
        ).forEach { (header, mediaType) ->
            assertEquals(ContentInspection.OtherFormat(mediaType), inspect(header, DocumentFormat.EML), mediaType)
        }
    }

    @Test
    fun `reports why a file of the format is malformed`() {
        assertEquals(ContentInspection.Malformed("it contains macros"), inspect(Samples.docx("word/vbaProject.bin"), DocumentFormat.DOCX))
        assertEquals(ContentInspection.Malformed("it has no end-of-file marker"), inspect(ascii("%PDF-1.7\n"), DocumentFormat.PDF))
        assertEquals(ContentInspection.Malformed("it is not text"), inspect(ascii("From: a@b.c\n\u0000"), DocumentFormat.EML))
    }

    @Test
    fun `a ZIP file that is not a Word document is not taken for one`() {
        val application = Samples.zip("META-INF/MANIFEST.MF", "App.class")

        assertEquals(ContentInspection.Malformed("it is a ZIP file, but not a Word document"), inspect(application, DocumentFormat.DOCX))
    }

    @Test
    fun `reads only the first bytes of a content whose signature does not match`() {
        var read = 0
        val content = FileContent {
            object : InputStream() {
                override fun read(): Int = if (read++ < 1_000_000) 'A'.code else -1
            }
        }

        inspector.inspect(content, 1_000_000, DocumentFormat.WAV)

        assertEquals(ContentInspectorAdapter.HEADER_LENGTH, read)
    }

    private fun inspect(content: ByteArray, format: DocumentFormat): ContentInspection =
        inspector.inspect(FileContent { content.inputStream() }, content.size.toLong(), format)

    private fun ascii(value: String): ByteArray = value.toByteArray(Charsets.US_ASCII)
}
