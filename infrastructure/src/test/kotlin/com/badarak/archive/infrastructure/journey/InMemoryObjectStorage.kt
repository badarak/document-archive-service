package com.badarak.archive.infrastructure.journey

import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.port.outbound.MissingObjectException
import com.badarak.archive.domain.port.outbound.ObjectStorage
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** Object storage held in memory for the journey tests. Switching it to [unavailable] simulates an outage of the storage. */
class InMemoryObjectStorage : ObjectStorage {

    private val objects = ConcurrentHashMap<ObjectKey, ByteArray>()

    @Volatile
    var unavailable = false

    override fun store(key: ObjectKey, content: InputStream, sizeBytes: Long, contentType: String) {
        checkAvailable()
        objects[key] = content.readAllBytes()
    }

    override fun open(key: ObjectKey): InputStream {
        checkAvailable()
        return objects[key]?.inputStream() ?: throw MissingObjectException(key, IllegalStateException("No object under $key"))
    }

    override fun delete(key: ObjectKey) {
        checkAvailable()
        objects.remove(key)
    }

    fun keys(): Set<ObjectKey> = objects.keys.toSet()

    fun reset() {
        objects.clear()
        unavailable = false
    }

    private fun checkAvailable() {
        if (unavailable) throw ObjectStorageException("The in-memory storage is switched off", IllegalStateException("Outage"))
    }
}
