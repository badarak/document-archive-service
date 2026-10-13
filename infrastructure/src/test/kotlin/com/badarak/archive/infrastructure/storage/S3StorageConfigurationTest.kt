package com.badarak.archive.infrastructure.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

class S3StorageConfigurationTest {

    private val configuration = S3StorageConfiguration()

    @Test
    fun `the client targets the configured endpoint and region`() {
        client().use { client ->
            val clientConfiguration = client.serviceClientConfiguration()

            assertEquals(URI.create("http://localhost:9000"), clientConfiguration.endpointOverride().orElseThrow())
            assertEquals(Region.of("eu-west-3"), clientConfiguration.region())
        }
    }

    @Test
    fun `the client authenticates with the configured keys`() {
        client().use { client ->
            val credentials = client.serviceClientConfiguration().credentialsProvider().resolveIdentity().join()

            assertEquals("access", credentials.accessKeyId())
            assertEquals("secret", credentials.secretAccessKey())
        }
    }

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
    fun `path-style access is the default`() {
        val properties = S3StorageProperties(
            endpoint = URI.create("http://localhost:9000"),
            region = "eu-west-3",
            bucket = "documents",
            accessKey = "access",
            secretKey = "secret",
        )

        assertEquals(true, properties.pathStyleAccess)
    }

    private fun client(pathStyleAccess: Boolean = true): S3Client = configuration.s3Client(
        S3StorageProperties(
            endpoint = URI.create("http://localhost:9000"),
            region = "eu-west-3",
            bucket = "documents",
            accessKey = "access",
            secretKey = "secret",
            pathStyleAccess = pathStyleAccess,
        ),
    )

    private fun S3Client.objectUrl(bucket: String, key: String): String = utilities().getUrl { it.bucket(bucket).key(key) }.toString()
}
