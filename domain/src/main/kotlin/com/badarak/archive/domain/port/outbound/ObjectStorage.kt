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

    /**
     * Opens the content stored under [key] as a stream, read from the storage as it is consumed. The caller must close it.
     *
     * @throws MissingObjectException when nothing is stored under [key]
     * @throws ObjectStorageException when the storage cannot be reached
     */
    fun open(key: ObjectKey): InputStream

    /**
     * Removes the content stored under [key]. Removing a key under which nothing is stored succeeds, so that an interrupted
     * deletion can be replayed.
     *
     * @throws ObjectStorageException when the storage cannot be reached or refuses the deletion
     */
    fun delete(key: ObjectKey)
}

class ObjectStorageException(message: String, cause: Throwable) : RuntimeException(message, cause)

class MissingObjectException(val key: ObjectKey, cause: Throwable) : RuntimeException("Nothing is stored under key '$key'", cause)
