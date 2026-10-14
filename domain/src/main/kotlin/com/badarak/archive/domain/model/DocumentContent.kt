package com.badarak.archive.domain.model

/** Binary content of a document as stored: its name and type as shown to callers, its size and its key in the object storage. */
data class DocumentContent(val fileName: String, val contentType: String, val sizeBytes: Long, val objectKey: ObjectKey) {

    init {
        requireText("fileName", fileName, FILE_NAME_MAX_LENGTH)
        requireText("contentType", contentType, CONTENT_TYPE_MAX_LENGTH)
        if (sizeBytes <= 0) throw EmptyDocumentException(sizeBytes)
    }

    companion object {
        const val FILE_NAME_MAX_LENGTH = 255
        const val CONTENT_TYPE_MAX_LENGTH = 255
    }
}
