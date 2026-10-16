package com.badarak.archive.domain.model

import java.time.Instant

/** Filters of a document search: a null filter matches every document, and both creation bounds are inclusive. */
data class DocumentSearchCriteria(
    val documentType: DocumentType? = null,
    val source: String? = null,
    val businessReference: String? = null,
    val createdFrom: Instant? = null,
    val createdTo: Instant? = null,
) {

    init {
        source?.let { requireCriterion("source", it, DocumentOwner.SOURCE_MAX_LENGTH) }
        businessReference?.let { requireCriterion("businessReference", it, DocumentMetadata.BUSINESS_REFERENCE_MAX_LENGTH) }
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw InvalidSearchException("createdFrom", "must not be after createdTo")
        }
    }
}

private fun requireCriterion(parameter: String, value: String, maxLength: Int) {
    if (value.isBlank()) throw InvalidSearchException(parameter, "must not be blank")
    if (value.length > maxLength) throw InvalidSearchException(parameter, "must not exceed $maxLength characters")
}
