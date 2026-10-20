package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.model.DocumentSearchCriteria
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.PageRequest
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * Query parameters of a document search, bound by their names. They are turned into domain objects only when the
 * controller asks for them, so that an invalid value is answered with the domain's own error code.
 */
data class SearchDocumentsRequest(
    @field:Parameter(description = "Exact document type, such as INVOICE")
    val documentType: String? = null,
    @field:Parameter(description = "Exact source, the service that archived the document")
    val source: String? = null,
    @field:Parameter(description = "Exact business reference")
    val businessReference: String? = null,
    @field:Parameter(description = "Earliest creation instant, inclusive, in ISO-8601 with an offset (2026-10-15T14:30:00Z)")
    val createdFrom: Instant? = null,
    @field:Parameter(description = "Latest creation instant, inclusive, in ISO-8601 with an offset (2026-10-15T14:30:00Z)")
    val createdTo: Instant? = null,
    @field:Parameter(description = "Number of the page, from 0", schema = Schema(defaultValue = "0", minimum = "0"))
    val page: Int = PageRequest.FIRST_PAGE,
    @field:Parameter(description = "Number of documents per page", schema = Schema(defaultValue = "20", minimum = "1", maximum = "100"))
    val size: Int = PageRequest.DEFAULT_SIZE,
) {
    fun criteria(): DocumentSearchCriteria =
        DocumentSearchCriteria(documentType?.let(::DocumentType), source, businessReference, createdFrom, createdTo)

    fun pageRequest(): PageRequest = PageRequest(page, size)
}
