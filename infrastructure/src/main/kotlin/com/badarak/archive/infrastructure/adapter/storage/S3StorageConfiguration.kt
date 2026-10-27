package com.badarak.archive.infrastructure.adapter.storage

import com.badarak.archive.domain.port.outbound.ObjectStorage
import io.micrometer.observation.ObservationRegistry
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

@Configuration
@EnableConfigurationProperties(S3StorageProperties::class)
class S3StorageConfiguration {

    @Bean
    fun s3Client(properties: S3StorageProperties): S3Client = S3Client.builder()
        .endpointOverride(properties.endpoint)
        .region(Region.of(properties.region))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey, properties.secretKey)),
        )
        .forcePathStyle(properties.pathStyleAccess)
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .overrideConfiguration { it.apiCallTimeout(properties.apiCallTimeout) }
        .build()

    @Bean
    fun objectStorage(s3Client: S3Client, properties: S3StorageProperties, observations: ObservationRegistry): ObjectStorage =
        ObservedObjectStorage(ObjectStorageAdapter(s3Client, properties), observations)
}
