package com.badarak.archive.infrastructure.adapter.storage

import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.port.outbound.MissingObjectException
import com.badarak.archive.domain.port.outbound.ObjectStorage
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import java.io.InputStream

/**
 * Observes every call to the object storage as `archive.storage`, then hands it to [delegate] unchanged: a timer tagged
 * with the `operation` (`store`, `open`, `delete`) and its `outcome` (`success`, `missing` for an object that does not
 * exist, `failure`), which tells whether the storage slows down or fails, and a span when the request is traced. Opening
 * an object is timed until its first bytes, not until the end of the download.
 */
class ObservedObjectStorage(private val delegate: ObjectStorage, private val observations: ObservationRegistry) : ObjectStorage {

    override fun store(key: ObjectKey, content: InputStream, sizeBytes: Long, contentType: String) =
        observe("store") { delegate.store(key, content, sizeBytes, contentType) }

    override fun open(key: ObjectKey): InputStream = observe("open") { delegate.open(key) }

    override fun delete(key: ObjectKey) = observe("delete") { delegate.delete(key) }

    private fun <T> observe(operation: String, call: () -> T): T {
        val observation = Observation.createNotStarted("archive.storage", observations)
            .lowCardinalityKeyValue("operation", operation)
            .start()
        var outcome = "success"
        try {
            return observation.openScope().use { call() }
        } catch (e: RuntimeException) {
            outcome = if (e is MissingObjectException) "missing" else "failure"
            observation.error(e)
            throw e
        } finally {
            observation.lowCardinalityKeyValue("outcome", outcome).stop()
        }
    }
}
