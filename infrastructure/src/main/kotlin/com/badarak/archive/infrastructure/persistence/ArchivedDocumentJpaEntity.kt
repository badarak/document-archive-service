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
@Table(name = "archived_document")
class ArchivedDocumentJpaEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,
    @Column(name = "document_type", nullable = false, length = 50)
    var documentType: String,
    @Column(name = "source", nullable = false, length = 100)
    var source: String,
    @Column(name = "business_reference")
    var businessReference: String?,
    @Column(name = "description", length = 1000)
    var description: String?,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", nullable = false)
    var tags: Map<String, String>,
    @Column(name = "file_name", nullable = false)
    var fileName: String,
    @Column(name = "content_type", nullable = false)
    var contentType: String,
    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long,
    @Column(name = "object_key", nullable = false, unique = true, length = 512)
    var objectKey: String,
    @Column(name = "status", nullable = false, length = 20)
    var status: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
