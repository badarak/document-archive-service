package com.badarak.archive.infrastructure.adapter.storage

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.net.URI
import java.time.Duration

/**
 * Settings of the S3-compatible object storage (MinIO locally, Cloudflare R2 in production). Path-style
 * access, on by default, is required by MinIO.
 *
 * Not a data class, so the secret key never ends up in a generated toString().
 */
@Validated
@ConfigurationProperties("archive.storage.s3")
class S3StorageProperties(
    val endpoint: URI,
    @field:NotBlank val region: String,
    @field:NotBlank val bucket: String,
    @field:NotBlank val accessKey: String,
    @field:NotBlank val secretKey: String,
    val pathStyleAccess: Boolean = true,
    val apiCallTimeout: Duration = Duration.ofMinutes(2),
)
