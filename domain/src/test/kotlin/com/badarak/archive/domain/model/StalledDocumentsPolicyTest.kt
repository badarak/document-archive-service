package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class StalledDocumentsPolicyTest {

    @Test
    fun `a document is stalled when it was last updated before the grace period`() {
        val policy = StalledDocumentsPolicy(Duration.ofMinutes(15), 100)

        assertEquals(Instant.parse("2026-10-20T09:45:00Z"), policy.stalledBefore(Instant.parse("2026-10-20T10:00:00Z")))
    }

    @Test
    fun `a zero grace period treats every document in progress as stalled`() {
        val now = Instant.parse("2026-10-20T10:00:00Z")

        assertEquals(now, StalledDocumentsPolicy(Duration.ZERO, 1).stalledBefore(now))
    }

    @Test
    fun `refuses a negative grace period`() {
        assertThrows(IllegalArgumentException::class.java) { StalledDocumentsPolicy(Duration.ofMinutes(-1), 100) }
    }

    @Test
    fun `refuses a batch size that is not positive`() {
        assertThrows(IllegalArgumentException::class.java) { StalledDocumentsPolicy(Duration.ofMinutes(15), 0) }
    }
}
