package com.badarak.archive.infrastructure.adapter.storage

import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.port.outbound.MissingObjectException
import com.badarak.archive.domain.port.outbound.ObjectStorage
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.InputStream

/**
 * Uploads each document in a single PutObject request: documents are small enough to need no multipart upload. Reads
 * stream the object from the storage, without loading it in memory. DeleteObject succeeds on a missing key with S3, MinIO
 * and Cloudflare R2, which makes deletions replayable.
 *
 * Every call is observed as `archive.storage`, a timer tagged with the `operation` (`store`, `open`, `delete`) and its
 * `outcome` (`success`, `missing` for an object that does not exist, `failure`): it tells whether the storage slows down
 * or fails. Opening an object is timed until its first bytes, not until the end of the download.
 */
@Component
class ObjectStorageAdapter(
    private val s3Client: S3Client,
    private val properties: S3StorageProperties,
    private val observations: ObservationRegistry,
) : ObjectStorage {

    override fun store(key: ObjectKey, content: InputStream, sizeBytes: Long, contentType: String) {
        val request = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key.value)
            .contentType(contentType)
            .contentLength(sizeBytes)
            .build()
        try {
            observe("store") { s3Client.putObject(request, RequestBody.fromInputStream(content, sizeBytes)) }
        } catch (e: SdkException) {
            throw ObjectStorageException("Could not store object '$key' in bucket '${properties.bucket}'", e)
        }
    }

    override fun open(key: ObjectKey): InputStream {
        val request = GetObjectRequest.builder().bucket(properties.bucket).key(key.value).build()
        return try {
            observe("open") { s3Client.getObject(request) }
        } catch (e: NoSuchKeyException) {
            throw MissingObjectException(key, e)
        } catch (e: SdkException) {
            throw ObjectStorageException("Could not read object '$key' in bucket '${properties.bucket}'", e)
        }
    }

    override fun delete(key: ObjectKey) {
        val request = DeleteObjectRequest.builder().bucket(properties.bucket).key(key.value).build()
        try {
            observe("delete") { s3Client.deleteObject(request) }
        } catch (e: SdkException) {
            throw ObjectStorageException("Could not delete object '$key' in bucket '${properties.bucket}'", e)
        }
    }

    private fun <T> observe(operation: String, call: () -> T): T {
        val observation = Observation.createNotStarted("archive.storage", observations)
            .lowCardinalityKeyValue("operation", operation)
            .start()
        var outcome = "success"
        try {
            return observation.openScope().use { call() }
        } catch (e: RuntimeException) {
            outcome = if (e is NoSuchKeyException) "missing" else "failure"
            observation.error(e)
            throw e
        } finally {
            observation.lowCardinalityKeyValue("outcome", outcome).stop()
        }
    }
}
