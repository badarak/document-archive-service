package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.infrastructure.adapter.validation.Samples.asContent
import com.badarak.archive.infrastructure.adapter.validation.Samples.ascii
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EmailStructureTest {

    @Test
    fun `an e-mail with folded header fields and a body is sound`() {
        assertNull(check(Samples.eml()))
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["From: alice@acme.test\n\nHello", "Date: Mon, 26 Oct 2026 09:00:00 +0000\nSubject: Hi", "from: alice@acme.test\r\n"],
    )
    fun `an e-mail needs only a From or Date field, with or without a body, whatever its line endings`(email: String) {
        assertNull(check(ascii(email)))
    }

    @Test
    fun `a binary file is not an e-mail`() {
        assertEquals("it is not text", check(ascii("From: alice@acme.test\r\n\r\n") + byteArrayOf(0, 1, 2)))
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["Hello Bob,\nplease find the invoice.", "From alice@acme.test Mon Oct 26 09:00:00 2026\nFrom: alice@acme.test\n\nHi"],
    )
    fun `plain text, or a mailbox file, is not an e-mail`(text: String) {
        assertEquals("its header holds a line that is not a header field", check(ascii(text)))
    }

    @Test
    fun `an e-mail cannot start with a folded line or an empty line`() {
        assertEquals("it does not start with an e-mail header", check(ascii(" From: alice@acme.test\n\nHi")))
        assertEquals("it does not start with an e-mail header", check(ascii("\nFrom: alice@acme.test")))
    }

    @Test
    fun `an e-mail needs a From or Date field`() {
        assertEquals("its header has neither a From nor a Date field", check(ascii("Subject: Hi\nTo: bob@acme.test\n\nHello")))
    }

    @Test
    fun `an e-mail header must fit in its first 64 KiB`() {
        val endless = ascii("From: alice@acme.test\n" + "X-Padding: ${"a".repeat(80)}\n".repeat(1_000))

        assertEquals("its header exceeds 64 KiB", check(endless))
    }

    private fun check(email: ByteArray): String? = EmailStructure.malformation(email.asContent(), email.size.toLong(), email)
}
