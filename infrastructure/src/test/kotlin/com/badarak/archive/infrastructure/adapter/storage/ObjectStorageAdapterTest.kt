package com.badarak.archive.infrastructure.adapter.storage

import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import java.net.URI

class ObjectStorageAdapterTest {

    private val s3Client = mockk<S3Client>()
    private val adapter = ObjectStorageAdapter(s3Client, properties())

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

    private fun store() = adapter.store(key, content.inputStream(), content.size.toLong(), "application/pdf")

    private fun properties() = S3StorageProperties(
        endpoint = URI.create("http://localhost:9000"),
        region = "us-east-1",
        bucket = "archive-documents",
        accessKey = "access",
        secretKey = "secret",
    )
}
