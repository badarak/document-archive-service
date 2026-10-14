package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DocumentOwnerTest {

    @Test
    fun `keeps the tenant and the source`() {
        val owner = DocumentOwner(TenantId("acme"), "billing-service")

        assertEquals(TenantId("acme"), owner.tenantId)
        assertEquals("billing-service", owner.source)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `rejects a blank source`(source: String) {
        assertInvalid("source") { DocumentOwner(TenantId("acme"), source) }
    }

    @Test
    fun `accepts a source of the maximum length`() {
        val source = "s".repeat(DocumentOwner.SOURCE_MAX_LENGTH)

        assertEquals(source, DocumentOwner(TenantId("acme"), source).source)
    }

    @Test
    fun `rejects a source longer than the maximum length`() {
        assertInvalid("source") { DocumentOwner(TenantId("acme"), "s".repeat(DocumentOwner.SOURCE_MAX_LENGTH + 1)) }
    }

    private fun assertInvalid(field: String, block: () -> Unit) {
        assertEquals(field, assertThrows(InvalidDocumentException::class.java) { block() }.field)
    }
}
