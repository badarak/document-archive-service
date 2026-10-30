package com.badarak.archive.infrastructure.adapter.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `configured keys are used as they are`() {
        val credentials = configuration.credentialsProvider(properties(accessKey = "access", secretKey = "secret")).resolveCredentials()

        assertEquals("access", credentials.accessKeyId())
        assertEquals("secret", credentials.secretAccessKey())
    }

    @Test
    fun `without configured keys, the standard AWS chain finds the credentials`() {
        withSystemProperties("aws.accessKeyId" to "chain-access", "aws.secretAccessKey" to "chain-secret") {
            val credentials = configuration.credentialsProvider(properties(accessKey = null, secretKey = null)).resolveCredentials()

            assertEquals("chain-access", credentials.accessKeyId())
        }
    }

    @Test
    fun `without configured keys nor credentials in the AWS chain, the application does not start`() {
        val nowhere = "/nonexistent/aws"
        withSystemProperties("aws.sharedCredentialsFile" to nowhere, "aws.configFile" to nowhere, "aws.disableEc2Metadata" to "true") {
            val exception = assertThrows(IllegalStateException::class.java) {
                configuration.credentialsProvider(properties(accessKey = null, secretKey = null))
            }

            assertEquals(
                "No object storage credentials: set ARCHIVE_S3_ACCESS_KEY and ARCHIVE_S3_SECRET_KEY, or the standard " +
                    "AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY",
                exception.message,
            )
        }
    }

    @Test
    fun `without endpoint, the client targets AWS S3`() {
        configuration.s3Client(properties(endpoint = null)).use { client ->
            assertTrue(client.serviceClientConfiguration().endpointOverride().isEmpty)
        }
    }

    private fun properties(
        endpoint: URI? = URI.create("http://localhost:9000"),
        accessKey: String? = "access",
        secretKey: String? = "secret",
        pathStyleAccess: Boolean = true,
        apiCallTimeout: Duration = Duration.ofMinutes(2),
    ) = S3StorageProperties(
        endpoint = endpoint,
        region = "eu-west-3",
        bucket = "documents",
        accessKey = accessKey,
        secretKey = secretKey,
        pathStyleAccess = pathStyleAccess,
        apiCallTimeout = apiCallTimeout,
    )

    private fun withSystemProperties(vararg values: Pair<String, String>, block: () -> Unit) {
        val previous = values.associate { (name, _) -> name to System.getProperty(name) }
        values.forEach { (name, value) -> System.setProperty(name, value) }
        try {
            block()
        } finally {
            previous.forEach { (name, value) -> if (value == null) System.clearProperty(name) else System.setProperty(name, value) }
        }
    }

    private fun client(pathStyleAccess: Boolean = true, apiCallTimeout: Duration = Duration.ofMinutes(2)): S3Client =
        configuration.s3Client(properties(pathStyleAccess = pathStyleAccess, apiCallTimeout = apiCallTimeout))

    private fun S3Client.objectUrl(bucket: String, key: String): String = utilities().getUrl { it.bucket(bucket).key(key) }.toString()
}
