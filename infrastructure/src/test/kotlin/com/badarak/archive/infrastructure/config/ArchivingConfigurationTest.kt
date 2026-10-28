package com.badarak.archive.infrastructure.config

import com.badarak.archive.domain.model.DocumentFormat
import com.badarak.archive.infrastructure.adapter.storage.S3StorageProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.boot.servlet.autoconfigure.MultipartProperties
import org.springframework.util.unit.DataSize
import java.net.URI
import java.time.Duration

class ArchivingConfigurationTest {

    private val configuration = ArchivingConfiguration()

    @Test
    fun `the policy is built from the reconciliation settings when the grace period outlasts the longest upload`() {
        val policy = policy(gracePeriod = Duration.ofMinutes(15), apiCallTimeout = Duration.ofMinutes(2))

        assertEquals(Duration.ofMinutes(15), policy.gracePeriod)
        assertEquals(50, policy.batchSize)
    }

    @Test
    fun `refuses a grace period that an upload could outlast`() {
        assertThrows(IllegalArgumentException::class.java) {
            policy(gracePeriod = Duration.ofMinutes(2), apiCallTimeout = Duration.ofMinutes(2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy(gracePeriod = Duration.ofMinutes(1), apiCallTimeout = Duration.ofMinutes(2))
        }
    }

    @Test
    fun `the size policy takes the configured maximum size when the multipart limits let such a file through`() {
        val policy = configuration.documentSizePolicy(validation(maxFileSize = 10), multipart(maxFileSize = 10, maxRequestSize = 11))

        assertEquals(10L * 1024 * 1024, policy.maxSizeBytes)
    }

    @Test
    fun `refuses multipart limits that would reject a file the validation accepts`() {
        assertThrows(IllegalArgumentException::class.java) {
            configuration.documentSizePolicy(validation(maxFileSize = 10), multipart(maxFileSize = 9, maxRequestSize = 11))
        }
        assertThrows(IllegalArgumentException::class.java) {
            configuration.documentSizePolicy(validation(maxFileSize = 10), multipart(maxFileSize = 10, maxRequestSize = 10))
        }
    }

    @Test
    fun `the format policy takes the configured extensions and mismatch policy`() {
        val policy = configuration.documentFormatPolicy(validation(maxFileSize = 10, rejectMimeMismatch = false))

        assertEquals(setOf(DocumentFormat.PDF, DocumentFormat.EML), policy.allowedFormats)
        assertFalse(policy.rejectMimeMismatch)
    }

    private fun validation(maxFileSize: Long, rejectMimeMismatch: Boolean = true) =
        ValidationProperties(DataSize.ofMegabytes(maxFileSize), listOf("pdf", "eml"), rejectMimeMismatch)

    private fun multipart(maxFileSize: Long, maxRequestSize: Long) = MultipartProperties().apply {
        this.maxFileSize = DataSize.ofMegabytes(maxFileSize)
        this.maxRequestSize = DataSize.ofMegabytes(maxRequestSize)
    }

    private fun policy(gracePeriod: Duration, apiCallTimeout: Duration) = configuration.stalledDocumentsPolicy(
        properties = ReconciliationProperties(gracePeriod = gracePeriod, batchSize = 50),
        storageProperties = S3StorageProperties(
            endpoint = URI("http://localhost:9000"),
            region = "us-east-1",
            bucket = "test-bucket",
            accessKey = "test-access-key",
            secretKey = "test-secret-key",
            apiCallTimeout = apiCallTimeout,
        ),
    )
}
