package com.badarak.archive.infrastructure.adapter.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

/**
 * The identifier is assigned by the domain, so Spring Data cannot guess that a new entity is new: [isNew] tells it, and
 * `save` then inserts the row in a single statement instead of reading it first to merge it. An entity is new until it
 * is persisted or loaded.
 */
@Entity
@Table(name = "archived_document", schema = "archive")
class ArchivedDocumentEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private val id: UUID,
    @Column(name = "tenant_id", nullable = false, length = 100, updatable = false)
    val tenantId: String,
    @Column(name = "document_type", nullable = false, length = 50)
    val documentType: String,
    @Column(name = "source", nullable = false, length = 100)
    val source: String,
    @Column(name = "business_reference")
    val businessReference: String?,
    @Column(name = "description", length = 1000)
    val description: String?,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", nullable = false)
    val tags: Map<String, String>,
    @Column(name = "file_name", nullable = false)
    val fileName: String,
    @Column(name = "content_type", nullable = false)
    val contentType: String,
    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Long,
    @Column(name = "object_key", nullable = false, unique = true, length = 512)
    val objectKey: String,
    @Column(name = "status", nullable = false, length = 20)
    var status: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
) : Persistable<UUID> {

    @Transient
    private var persisted = false

    override fun getId(): UUID = id

    override fun isNew(): Boolean = !persisted

    @PostLoad
    @PostPersist
    fun markPersisted() {
        persisted = true
    }
}
