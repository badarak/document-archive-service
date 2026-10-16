package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.model.ArchivedDocument
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(name = "ArchivedDocument")
data class ArchivedDocumentResponse(
    val id: UUID,
    val documentType: String,
    val businessReference: String?,
    val description: String?,
    val tags: Map<String, String>,
    val fileName: String,
    val contentType: String,
    val size: Long,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(document: ArchivedDocument) = ArchivedDocumentResponse(
            id = document.id.value,
            documentType = document.metadata.documentType.value,
            businessReference = document.metadata.businessReference,
            description = document.metadata.description,
            tags = document.metadata.tags,
            fileName = document.file.fileName,
            contentType = document.file.contentType,
            size = document.file.sizeBytes,
            status = document.status.name,
            createdAt = document.createdAt,
            updatedAt = document.updatedAt,
        )
    }
}
