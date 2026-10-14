package com.badarak.archive.domain.model

/** Business description of a document, supplied by the caller. The tags are copied, so later changes to the given map do not leak in. */
class DocumentMetadata(
    val documentType: DocumentType,
    val businessReference: String?,
    val description: String?,
    tags: Map<String, String>,
) {
    val tags: Map<String, String> = tags.toMap()

    init {
        businessReference?.let { requireText("businessReference", it, BUSINESS_REFERENCE_MAX_LENGTH) }
        description?.let { requireMaxLength("description", it, DESCRIPTION_MAX_LENGTH) }
    }

    companion object {
        const val BUSINESS_REFERENCE_MAX_LENGTH = 255
        const val DESCRIPTION_MAX_LENGTH = 1000
    }
}
