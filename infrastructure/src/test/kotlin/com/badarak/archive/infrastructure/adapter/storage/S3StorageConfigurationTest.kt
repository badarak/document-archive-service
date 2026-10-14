package com.badarak.archive.infrastructure.adapter.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.time.Duration

class S3StorageConfigurationTest {

    private val configuration = S3StorageConfiguration()

    @Test
    fun `path-style access puts the bucket in the URL path`() {
        client(pathStyleAccess = true).use { client ->
            assertEquals("http://localhost:9000/documents/some/key", client.objectUrl("documents", "some/key"))
        }
    }

    @Test
    fun `virtual-hosted access puts the bucket in the host name`() {
        client(pathStyleAccess = false).use { client ->
            assertEquals("http://documents.localhost:9000/some/key", client.objectUrl("documents", "some/key"))
        }
    }

    @Test
    fun `a call to the storage fails after the configured timeout`() {
        client(apiCallTimeout = Duration.ofSeconds(45)).use { client ->
            assertEquals(Duration.ofSeconds(45), client.serviceClientConfiguration().overrideConfiguration().apiCallTimeout().orElseThrow())
        }
    }

    private fun client(pathStyleAccess: Boolean = true, apiCallTimeout: Duration = Duration.ofMinutes(2)): S3Client =
        configuration.s3Client(
            S3StorageProperties(
                endpoint = URI.create("http://localhost:9000"),
                region = "eu-west-3",
                bucket = "documents",
                accessKey = "access",
                secretKey = "secret",
                pathStyleAccess = pathStyleAccess,
                apiCallTimeout = apiCallTimeout,
            ),
        )

    private fun S3Client.objectUrl(bucket: String, key: String): String = utilities().getUrl { it.bucket(bucket).key(key) }.toString()
}
