package com.badarak.archive.infrastructure.adapter.storage

import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.port.outbound.MissingObjectException
import com.badarak.archive.domain.port.outbound.ObjectStorage
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.InputStream

/**
 * Uploads each document in a single PutObject request: documents are small enough to need no multipart upload. Reads
 * stream the object from the storage, without loading it in memory.
 */
@Component
class ObjectStorageAdapter(private val s3Client: S3Client, private val properties: S3StorageProperties) : ObjectStorage {

    override fun store(key: ObjectKey, content: InputStream, sizeBytes: Long, contentType: String) {
        val request = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key.value)
            .contentType(contentType)
            .contentLength(sizeBytes)
            .build()
        try {
            s3Client.putObject(request, RequestBody.fromInputStream(content, sizeBytes))
        } catch (e: SdkException) {
            throw ObjectStorageException("Could not store object '$key' in bucket '${properties.bucket}'", e)
        }
    }

    override fun open(key: ObjectKey): InputStream {
        val request = GetObjectRequest.builder().bucket(properties.bucket).key(key.value).build()
        return try {
            s3Client.getObject(request)
        } catch (e: NoSuchKeyException) {
            throw MissingObjectException(key, e)
        } catch (e: SdkException) {
            throw ObjectStorageException("Could not read object '$key' in bucket '${properties.bucket}'", e)
        }
    }
}
