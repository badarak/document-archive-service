package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.model.DocumentPage
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "DocumentPage")
data class DocumentPageResponse(
    val content: List<ArchivedDocumentResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Long,
) {
    companion object {
        fun from(page: DocumentPage) = DocumentPageResponse(
            content = page.documents.map { ArchivedDocumentResponse.from(it) },
            page = page.request.page,
            size = page.request.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        )
    }
}
