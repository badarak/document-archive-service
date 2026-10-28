package com.badarak.archive.infrastructure.adapter.validation

/** Programs recognised from their first bytes, so that none is ever archived as a document, whatever its name. */
internal enum class Executable(val mediaType: String, private val signatures: List<ByteArray>) {
    WINDOWS("application/vnd.microsoft.portable-executable", listOf("MZ".toByteArray())),
    LINUX("application/x-executable", listOf(bytes(0x7F, 0x45, 0x4C, 0x46))),
    MACOS(
        "application/x-mach-binary",
        listOf(
            bytes(0xFE, 0xED, 0xFA, 0xCE),
            bytes(0xFE, 0xED, 0xFA, 0xCF),
            bytes(0xCE, 0xFA, 0xED, 0xFE),
            bytes(0xCF, 0xFA, 0xED, 0xFE),
            bytes(0xCA, 0xFE, 0xBA, 0xBE),
        ),
    ),
    SCRIPT("text/x-script", listOf("#!".toByteArray())),
    ;

    companion object {
        fun of(header: ByteArray): Executable? = entries.find { executable -> executable.signatures.any { header.hasAt(0, it) } }
    }
}
