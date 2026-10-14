package com.badarak.archive.domain.port.outbound

import com.badarak.archive.domain.model.ObjectKey
import java.io.InputStream

/** Stores the binary content of documents, whatever the object storage provider. */
interface ObjectStoragePort {

    /**
     * Stores the [sizeBytes] bytes of [content] under [key], read as a stream.
     *
     * @throws ObjectStorageException when the storage cannot be reached or refuses the content
     */
    fun store(key: ObjectKey, content: InputStream, sizeBytes: Long, contentType: String)
}

/** The object storage failed; the cause holds the provider's own error. */
class ObjectStorageException(message: String, cause: Throwable) : RuntimeException(message, cause)
