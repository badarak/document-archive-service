package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.model.DocumentMetadata
import com.badarak.archive.domain.model.DocumentType
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "ArchiveMetadata",
    description = "Business metadata of a document; any source field is ignored, the source comes from the token",
)
data class ArchiveMetadataRequest(
    @field:Schema(example = "INVOICE")
    val documentType: String,
    @field:Schema(example = "INV-2026-000123")
    val businessReference: String? = null,
    @field:Schema(example = "Customer invoice")
    val description: String? = null,
    @field:Schema(example = """{"customerId": "CUST-456"}""")
    val tags: Map<String, String> = emptyMap(),
) {
    fun toDomain(): DocumentMetadata = DocumentMetadata(DocumentType(documentType), businessReference, description, tags)
}
