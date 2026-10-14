package com.badarak.archive.infrastructure.archiving

import com.badarak.archive.application.ArchiveDocumentUseCase
import com.badarak.archive.application.port.DocumentRepositoryPort
import com.badarak.archive.application.port.ObjectStoragePort
import com.badarak.archive.domain.DocumentFormatPolicy
import com.badarak.archive.domain.DocumentSizePolicy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Builds the archiving use case, which stays free of Spring, from the adapters and policies. */
@Configuration
class ArchivingConfiguration {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun documentSizePolicy(): DocumentSizePolicy = DocumentSizePolicy()

    @Bean
    fun archiveDocumentUseCase(
        documents: DocumentRepositoryPort,
        storage: ObjectStoragePort,
        formatPolicy: DocumentFormatPolicy,
        sizePolicy: DocumentSizePolicy,
        clock: Clock,
    ): ArchiveDocumentUseCase = ArchiveDocumentUseCase(documents, storage, formatPolicy, sizePolicy, clock)
}
