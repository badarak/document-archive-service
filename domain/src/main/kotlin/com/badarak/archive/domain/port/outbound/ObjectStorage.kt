package com.badarak.archive.domain.port.outbound

import com.badarak.archive.domain.model.ObjectKey
import java.io.InputStream

interface ObjectStorage {

    /**
     * Stores the [sizeBytes] bytes of [content] under [key], read as a stream.
     *
     * @throws ObjectStorageException when the storage cannot be reached or refuses the content
     */
    fun store(key: ObjectKey, content: InputStream, sizeBytes: Long, contentType: String)
}

class ObjectStorageException(message: String, cause: Throwable) : RuntimeException(message, cause)
