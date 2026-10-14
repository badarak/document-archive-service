package com.badarak.archive.infrastructure.storage

import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `spring injects an S3 client built from the external configuration`() {
        assertEquals("test-bucket", properties.bucket)
        assertEquals(URI.create("http://localhost:9000"), properties.endpoint)
        assertEquals(properties.endpoint, s3Client.serviceClientConfiguration().endpointOverride().orElseThrow())
        assertEquals(Duration.ofMinutes(2), s3Client.serviceClientConfiguration().overrideConfiguration().apiCallTimeout().orElseThrow())
    }
}
