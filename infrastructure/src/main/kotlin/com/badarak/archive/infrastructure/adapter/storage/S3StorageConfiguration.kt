package com.badarak.archive.infrastructure.adapter.storage

import com.badarak.archive.domain.port.outbound.ObjectStorage
import io.micrometer.observation.ObservationRegistry
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

@Configuration
@EnableConfigurationProperties(S3StorageProperties::class)
class S3StorageConfiguration {

    @Bean
    fun s3Client(properties: S3StorageProperties): S3Client = S3Client.builder()
        .apply { properties.endpoint?.let(::endpointOverride) }
        .region(Region.of(properties.region))
        .credentialsProvider(credentialsProvider(properties))
        .forcePathStyle(properties.pathStyleAccess)
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .overrideConfiguration { it.apiCallTimeout(properties.apiCallTimeout) }
        .build()

    /**
     * The configured keys, or the standard AWS chain when there are none. The credentials are resolved once here, so that
     * a deployment without any stops at startup instead of failing at its first upload.
     */
    fun credentialsProvider(properties: S3StorageProperties): AwsCredentialsProvider {
        val accessKey = properties.accessKey
        val secretKey = properties.secretKey
        val provider = if (accessKey != null && secretKey != null) {
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        } else {
            DefaultCredentialsProvider.builder().build()
        }
        try {
            provider.resolveCredentials()
        } catch (e: SdkClientException) {
            throw IllegalStateException(
                "No object storage credentials: set ARCHIVE_S3_ACCESS_KEY and ARCHIVE_S3_SECRET_KEY, or the standard " +
                    "AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY",
                e,
            )
        }
        return provider
    }

    @Bean
    fun objectStorage(s3Client: S3Client, properties: S3StorageProperties, observations: ObservationRegistry): ObjectStorage =
        ObservedObjectStorage(ObjectStorageAdapter(s3Client, properties), observations)
}
