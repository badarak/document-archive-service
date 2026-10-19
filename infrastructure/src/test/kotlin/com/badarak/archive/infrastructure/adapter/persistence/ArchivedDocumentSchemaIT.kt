package com.badarak.archive.infrastructure.adapter.persistence

import jakarta.persistence.EntityManager
import liquibase.integration.spring.SpringLiquibase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ArchivedDocumentSchemaIT {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var liquibase: SpringLiquibase

    @Test
    fun `liquibase creates the archived_document table and its constraints at startup`() {
        val constraints = jdbcTemplate.queryForList(
            "SELECT lower(constraint_name) FROM information_schema.table_constraints " +
                "WHERE lower(table_schema) = 'archive' AND lower(table_name) = 'archived_document'",
            String::class.java,
        )

        assertEquals(
            setOf(
                "pk_archived_document",
                "uk_archived_document_object_key",
                "ck_archived_document_size_bytes",
                "ck_archived_document_status",
                "ck_archived_document_deleted_at",
            ),
            constraints.toSet(),
        )
    }

    @Test
    fun `liquibase creates the index on tenant, source and business reference`() {
        val indexes = indexes()

        assertTrue("idx_archived_document_tenant_source_business_reference" in indexes)
        assertFalse("idx_archived_document_source_business_reference" in indexes)
    }

    @Test
    fun `liquibase creates the search indexes on tenant and creation date`() {
        val indexes = indexes()

        assertTrue("idx_archived_document_tenant_created" in indexes)
        assertTrue("idx_archived_document_tenant_type_created" in indexes)
    }

    @Test
    fun `liquibase creates the index on status and update date`() {
        assertTrue("idx_archived_document_status_updated" in indexes())
    }

    @Test
    fun `running liquibase again does not replay applied changesets`() {
        liquibase.afterPropertiesSet()

        assertEquals(5, appliedChangeSetCount())
    }

    @Test
    fun `an archived document round-trips through the JPA mapping`() {
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val document = ArchivedDocumentEntity(
            id = UUID.randomUUID(),
            tenantId = "acme",
            documentType = "INVOICE",
            source = "BILLING-SERVICE",
            businessReference = "INV-2026-000123",
            description = "Customer invoice",
            tags = mapOf("customerId" to "CUST-456"),
            fileName = "invoice-123.pdf",
            contentType = "application/pdf",
            sizeBytes = 248_120,
            objectKey = "documents/${UUID.randomUUID()}",
            status = "AVAILABLE",
            createdAt = now,
            updatedAt = now,
        )

        entityManager.persist(document)
        entityManager.flush()
        entityManager.clear()

        val reloaded = entityManager.find(ArchivedDocumentEntity::class.java, document.id)
        assertNotNull(reloaded)
        assertEquals(document.tenantId, reloaded.tenantId)
        assertEquals(document.documentType, reloaded.documentType)
        assertEquals(document.source, reloaded.source)
        assertEquals(document.businessReference, reloaded.businessReference)
        assertEquals(document.description, reloaded.description)
        assertEquals(document.tags, reloaded.tags)
        assertEquals(document.fileName, reloaded.fileName)
        assertEquals(document.contentType, reloaded.contentType)
        assertEquals(document.sizeBytes, reloaded.sizeBytes)
        assertEquals(document.objectKey, reloaded.objectKey)
        assertEquals(document.status, reloaded.status)
        assertEquals(document.createdAt, reloaded.createdAt)
        assertEquals(document.updatedAt, reloaded.updatedAt)
    }

    @Test
    fun `the database refuses an empty document`() {
        assertThrows(DataIntegrityViolationException::class.java) { insertRow(sizeBytes = 0) }
    }

    @Test
    fun `the database refuses a document without tenant`() {
        assertThrows(DataIntegrityViolationException::class.java) { insertRow(tenantId = null) }
    }

    @Test
    fun `the database refuses an unknown status`() {
        assertThrows(DataIntegrityViolationException::class.java) { insertRow(status = "ARCHIVED") }
    }

    @Test
    fun `the database refuses a deleted document without deletion date`() {
        assertThrows(DataIntegrityViolationException::class.java) { insertRow(status = "DELETED") }
    }

    @Test
    fun `the database refuses a deletion date on a document that is not deleted`() {
        assertThrows(DataIntegrityViolationException::class.java) { insertRow(status = "DELETING", deletedAt = OffsetDateTime.now()) }
    }

    @Test
    fun `the database accepts a deleted document with its deletion date`() {
        insertRow(status = "DELETED", deletedAt = OffsetDateTime.now())
    }

    private fun insertRow(tenantId: String? = "acme", sizeBytes: Long = 1, status: String = "PENDING", deletedAt: OffsetDateTime? = null) {
        jdbcTemplate.update(
            "INSERT INTO archive.archived_document (id, tenant_id, document_type, source, tags, file_name, content_type, " +
                "size_bytes, object_key, status, created_at, updated_at, deleted_at) " +
                "VALUES (?, ?, 'INVOICE', 'BILLING-SERVICE', '{}', 'invoice.pdf', 'application/pdf', ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(),
            tenantId,
            sizeBytes,
            "documents/${UUID.randomUUID()}",
            status,
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            deletedAt,
        )
    }

    private fun indexes(): List<String?> = jdbcTemplate.queryForList(
        "SELECT lower(index_name) FROM information_schema.indexes " +
            "WHERE lower(table_schema) = 'archive' AND lower(table_name) = 'archived_document'",
        String::class.java,
    )

    private fun appliedChangeSetCount(): Int? = jdbcTemplate.queryForObject(
        "SELECT count(*) FROM archive.databasechangelog",
        Int::class.java,
    )
}
