package com.badarak.archive.infrastructure.adapter.storage

import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.port.outbound.MissingObjectException
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import java.net.URI

class ObjectStorageAdapterTest {

    private val s3Client = mockk<S3Client>()
    private val meters = SimpleMeterRegistry()
    private val observations = ObservationRegistry.create().apply {
        observationConfig().observationHandler(DefaultMeterObservationHandler(meters))
    }
    private val adapter = ObjectStorageAdapter(s3Client, properties(), observations)

    private val key = ObjectKey("documents/7d7d57e1")
    private val content = "%PDF-1.7 content".toByteArray()

    @Test
    fun `uploads the content under the key, in the configured bucket, with its type and size`() {
        val request = slot<PutObjectRequest>()
        val body = slot<RequestBody>()
        every { s3Client.putObject(capture(request), capture(body)) } returns PutObjectResponse.builder().build()

        adapter.store(key, content.inputStream(), content.size.toLong(), "application/pdf")

        assertEquals("archive-documents", request.captured.bucket())
        assertEquals("documents/7d7d57e1", request.captured.key())
        assertEquals("application/pdf", request.captured.contentType())
        assertEquals(content.size.toLong(), request.captured.contentLength())
        assertArrayEquals(content, body.captured.contentStreamProvider().newStream().readAllBytes())
    }

    @Test
    fun `an error returned by the storage becomes an object storage error`() {
        val refusal = S3Exception.builder().message("The specified bucket does not exist").statusCode(404).build()
        every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } throws refusal

        val exception = assertThrows(ObjectStorageException::class.java) { store() }

        assertSame(refusal, exception.cause)
        assertEquals("Could not store object 'documents/7d7d57e1' in bucket 'archive-documents'", exception.message)
    }

    @Test
    fun `an unreachable storage becomes an object storage error`() {
        val unreachable = SdkClientException.create("Unable to execute HTTP request: Connection refused")
        every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } throws unreachable

        val exception = assertThrows(ObjectStorageException::class.java) { store() }

        assertSame(unreachable, exception.cause)
    }

    @Test
    fun `opens the object under the key, in the configured bucket, as a stream`() {
        val request = slot<GetObjectRequest>()
        val stream = ResponseInputStream(GetObjectResponse.builder().build(), AbortableInputStream.create(content.inputStream()))
        every { s3Client.getObject(capture(request)) } returns stream

        val opened = adapter.open(key)

        assertEquals("archive-documents", request.captured.bucket())
        assertEquals("documents/7d7d57e1", request.captured.key())
        assertArrayEquals(content, opened.readAllBytes())
    }

    @Test
    fun `a missing object becomes a missing object error`() {
        val missing = NoSuchKeyException.builder().message("The specified key does not exist").statusCode(404).build()
        every { s3Client.getObject(any<GetObjectRequest>()) } throws missing

        val exception = assertThrows(MissingObjectException::class.java) { adapter.open(key) }

        assertEquals(key, exception.key)
        assertSame(missing, exception.cause)
    }

    @Test
    fun `an unreachable storage becomes an object storage error when reading`() {
        val unreachable = SdkClientException.create("Unable to execute HTTP request: Connection refused")
        every { s3Client.getObject(any<GetObjectRequest>()) } throws unreachable

        val exception = assertThrows(ObjectStorageException::class.java) { adapter.open(key) }

        assertSame(unreachable, exception.cause)
        assertEquals("Could not read object 'documents/7d7d57e1' in bucket 'archive-documents'", exception.message)
    }

    @Test
    fun `deletes the object under the key, in the configured bucket`() {
        val request = slot<DeleteObjectRequest>()
        every { s3Client.deleteObject(capture(request)) } returns DeleteObjectResponse.builder().build()

        adapter.delete(key)

        assertEquals("archive-documents", request.captured.bucket())
        assertEquals("documents/7d7d57e1", request.captured.key())
    }

    @Test
    fun `an unreachable storage becomes an object storage error when deleting`() {
        val unreachable = SdkClientException.create("Unable to execute HTTP request: Connection refused")
        every { s3Client.deleteObject(any<DeleteObjectRequest>()) } throws unreachable

        val exception = assertThrows(ObjectStorageException::class.java) { adapter.delete(key) }

        assertSame(unreachable, exception.cause)
        assertEquals("Could not delete object 'documents/7d7d57e1' in bucket 'archive-documents'", exception.message)
    }

    private fun store() = adapter.store(key, content.inputStream(), content.size.toLong(), "application/pdf")

    @Test
    fun `every storage call is timed by operation and outcome`() {
        every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } returns PutObjectResponse.builder().build()
        every { s3Client.getObject(any<GetObjectRequest>()) } throws NoSuchKeyException.builder().message("missing").build()
        every { s3Client.deleteObject(any<DeleteObjectRequest>()) } throws SdkClientException.create("Connection refused")

        store()
        assertThrows(MissingObjectException::class.java) { adapter.open(key) }
        assertThrows(ObjectStorageException::class.java) { adapter.delete(key) }

        assertEquals(1, calls("store", "success"))
        assertEquals(1, calls("open", "missing"))
        assertEquals(1, calls("delete", "failure"))
    }

    private fun calls(operation: String, outcome: String): Long =
        meters.find("archive.storage").tag("operation", operation).tag("outcome", outcome).timer()?.count() ?: 0

    private fun properties() = S3StorageProperties(
        endpoint = URI.create("http://localhost:9000"),
        region = "us-east-1",
        bucket = "archive-documents",
        accessKey = "access",
        secretKey = "secret",
    )
}
