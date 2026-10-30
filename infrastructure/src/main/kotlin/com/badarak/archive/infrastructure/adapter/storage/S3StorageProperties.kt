package com.badarak.archive.infrastructure.adapter.storage

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.net.URI
import java.time.Duration

/**
 * Settings of the S3-compatible object storage (MinIO locally, Cloudflare R2 in production). Without [endpoint], the
 * client targets AWS S3. Path-style access, on by default, is required by MinIO.
 *
 * The credentials are either both [accessKey] and [secretKey], or neither: the standard AWS chain then finds them
 * (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY, a profile file, a web identity or an instance role).
 *
 * Not a data class, so the secret key never ends up in a generated toString().
 */
@Validated
@ConfigurationProperties("archive.storage.s3")
class S3StorageProperties(
    endpoint: URI? = null,
    @field:NotBlank val region: String = "auto",
    @field:NotBlank val bucket: String,
    accessKey: String? = null,
    secretKey: String? = null,
    val pathStyleAccess: Boolean = true,
    val apiCallTimeout: Duration = Duration.ofMinutes(2),
) {
    val endpoint: URI? = endpoint?.takeUnless { it.toString().isBlank() }
    val accessKey: String? = accessKey?.takeUnless { it.isBlank() }
    val secretKey: String? = secretKey?.takeUnless { it.isBlank() }

    @get:AssertTrue(message = "access-key and secret-key must be both set, or both empty to use the standard AWS credentials chain")
    val credentialsComplete: Boolean
        get() = (accessKey == null) == (secretKey == null)
}
