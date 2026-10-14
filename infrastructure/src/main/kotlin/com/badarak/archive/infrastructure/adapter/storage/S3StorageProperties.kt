package com.badarak.archive.infrastructure.adapter.storage

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

/**
 * Settings of the S3-compatible object storage (MinIO locally, Cloudflare R2 in production). Path-style
 * access, on by default, is required by MinIO.
 *
 * Not a data class, so the secret key never ends up in a generated toString().
 */
@ConfigurationProperties("archive.storage.s3")
class S3StorageProperties(
    val endpoint: URI,
    val region: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    val pathStyleAccess: Boolean = true,
    val apiCallTimeout: Duration = Duration.ofMinutes(2),
)
