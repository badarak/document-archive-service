package com.badarak.archive.infrastructure.adapter.storage

import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.port.outbound.MissingObjectException
import com.badarak.archive.domain.port.outbound.ObjectStorage
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ObservedObjectStorageTest {

    private val delegate = mockk<ObjectStorage>()
    private val meters = SimpleMeterRegistry()
    private val observations = ObservationRegistry.create().apply {
        observationConfig().observationHandler(DefaultMeterObservationHandler(meters))
    }
    private val storage = ObservedObjectStorage(delegate, observations)
    private val key = ObjectKey("documents/7d7d57e1")

    @Test
    fun `every call is handed to the storage unchanged`() {
        val content = "%PDF-1.7".byteInputStream()
        val opened = "%PDF-1.7".byteInputStream()
        every { delegate.store(key, content, 8, "application/pdf") } just runs
        every { delegate.open(key) } returns opened
        every { delegate.delete(key) } just runs

        storage.store(key, content, 8, "application/pdf")
        assertSame(opened, storage.open(key))
        storage.delete(key)

        verify(exactly = 1) { delegate.store(key, content, 8, "application/pdf") }
        verify(exactly = 1) { delegate.delete(key) }
    }

    @Test
    fun `every call is timed by operation and outcome, and its failure is passed on`() {
        val missing = MissingObjectException(key, RuntimeException())
        val down = ObjectStorageException("Connection refused", RuntimeException())
        every { delegate.store(any(), any(), any(), any()) } just runs
        every { delegate.open(key) } throws missing
        every { delegate.delete(key) } throws down

        storage.store(key, "x".byteInputStream(), 1, "application/pdf")
        assertSame(missing, assertThrows(MissingObjectException::class.java) { storage.open(key) })
        assertSame(down, assertThrows(ObjectStorageException::class.java) { storage.delete(key) })

        assertEquals(1, calls("store", "success"))
        assertEquals(1, calls("open", "missing"))
        assertEquals(1, calls("delete", "failure"))
    }

    private fun calls(operation: String, outcome: String): Long =
        meters.find("archive.storage").tag("operation", operation).tag("outcome", outcome).timer()?.count() ?: 0
}
