package com.badarak.archive.infrastructure.archiving

import com.badarak.archive.domain.model.DocumentFormatPolicy
import com.badarak.archive.domain.model.DocumentSizePolicy
import com.badarak.archive.domain.port.inbound.ArchiveDocumentUseCase
import com.badarak.archive.domain.port.outbound.DocumentRepository
import com.badarak.archive.domain.port.outbound.ObjectStorage
import com.badarak.archive.domain.service.ArchiveDocumentService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Builds the archiving service, which stays free of Spring, from the adapters and policies. */
@Configuration
class ArchivingConfiguration {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun documentSizePolicy(): DocumentSizePolicy = DocumentSizePolicy()

    @Bean
    fun archiveDocumentUseCase(
        documents: DocumentRepository,
        storage: ObjectStorage,
        formatPolicy: DocumentFormatPolicy,
        sizePolicy: DocumentSizePolicy,
        clock: Clock,
    ): ArchiveDocumentUseCase = ArchiveDocumentService(documents, storage, formatPolicy, sizePolicy, clock)
}
