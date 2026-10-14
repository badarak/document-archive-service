package com.badarak.archive.infrastructure.storage

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

/**
 * Settings of the S3-compatible object storage (MinIO locally, Cloudflare R2 in production).
 * Not a data class, so the secret key never ends up in a generated toString().
 */
@ConfigurationProperties("archive.storage.s3")
class S3StorageProperties(
    val endpoint: URI,
    val region: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    /** Addresses buckets as endpoint/bucket instead of bucket.endpoint; required by MinIO. */
    val pathStyleAccess: Boolean = true,
    /** Longest time a call to the storage may take, retries included, before it fails. */
    val apiCallTimeout: Duration = Duration.ofMinutes(2),
)
