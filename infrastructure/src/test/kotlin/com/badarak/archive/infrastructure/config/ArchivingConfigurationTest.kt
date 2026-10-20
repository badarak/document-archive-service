package com.badarak.archive.infrastructure.config

import com.badarak.archive.infrastructure.adapter.storage.S3StorageProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
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
