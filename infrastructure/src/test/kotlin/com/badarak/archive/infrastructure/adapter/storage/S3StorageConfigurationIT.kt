package com.badarak.archive.infrastructure.adapter.storage

import com.badarak.archive.domain.port.outbound.ObjectStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.time.Duration

@SpringBootTest
@ActiveProfiles("test")
class S3StorageConfigurationIT {

    @Autowired
    private lateinit var s3Client: S3Client

    @Autowired
    private lateinit var properties: S3StorageProperties

    @Autowired
    private lateinit var storage: ObjectStorage

    @Test
    fun `spring injects an S3 client built from the external configuration`() {
        assertEquals("test-bucket", properties.bucket)
        assertEquals(URI.create("http://localhost:9000"), properties.endpoint)
        assertEquals(properties.endpoint, s3Client.serviceClientConfiguration().endpointOverride().orElseThrow())
        assertEquals(Duration.ofMinutes(2), s3Client.serviceClientConfiguration().overrideConfiguration().apiCallTimeout().orElseThrow())
    }

    @Test
    fun `the use cases get the S3 storage wrapped in its observation`() {
        assertTrue(storage is ObservedObjectStorage)
    }
}
