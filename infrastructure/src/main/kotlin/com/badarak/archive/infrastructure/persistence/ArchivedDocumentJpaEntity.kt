package com.badarak.archive.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "archived_document", schema = "archive")
class ArchivedDocumentJpaEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,
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
)
