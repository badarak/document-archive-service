package com.badarak.archive.infrastructure.config

import com.badarak.archive.infrastructure.adapter.storage.S3StorageProperties
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Clock
import java.time.Duration

class ArchivingConfigurationTest {

    private val configuration = ArchivingConfiguration()

    @Test
    fun `the archivings are abandoned only after the longest possible upload`() {
        assertDoesNotThrow { abandonment(gracePeriod = Duration.ofMinutes(15), apiCallTimeout = Duration.ofMinutes(2)) }
    }

    @Test
    fun `refuses a grace period that an upload could outlast`() {
        assertThrows(IllegalArgumentException::class.java) {
            abandonment(gracePeriod = Duration.ofMinutes(2), apiCallTimeout = Duration.ofMinutes(2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            abandonment(gracePeriod = Duration.ofMinutes(1), apiCallTimeout = Duration.ofMinutes(2))
        }
    }

    private fun abandonment(gracePeriod: Duration, apiCallTimeout: Duration) = configuration.abandonStalledArchivingsUseCase(
        documents = mockk(),
        storage = mockk(),
        clock = Clock.systemUTC(),
        properties = ReconciliationProperties(gracePeriod = gracePeriod),
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
