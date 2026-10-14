package com.badarak.archive.domain.model

class DocumentSizePolicy(val maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES) {

    init {
        require(maxSizeBytes > 0) { "The maximum document size must be positive, got $maxSizeBytes" }
    }

    fun check(sizeBytes: Long) {
        if (sizeBytes <= 0) throw EmptyDocumentException(sizeBytes)
        if (sizeBytes > maxSizeBytes) throw DocumentTooLargeException(sizeBytes, maxSizeBytes)
    }

    companion object {
        const val DEFAULT_MAX_SIZE_BYTES = 50L * 1024 * 1024
    }
}
