package com.badarak.archive.infrastructure.config

import com.badarak.archive.domain.model.DocumentFormatPolicy
import com.badarak.archive.domain.model.DocumentSizePolicy
import com.badarak.archive.domain.port.inbound.ArchiveDocumentUseCase
import com.badarak.archive.domain.port.inbound.DeleteDocumentUseCase
import com.badarak.archive.domain.port.inbound.DownloadDocumentUseCase
import com.badarak.archive.domain.port.inbound.GetDocumentUseCase
import com.badarak.archive.domain.port.inbound.ResumeDeletionsUseCase
import com.badarak.archive.domain.port.inbound.SearchDocumentsUseCase
import com.badarak.archive.domain.port.outbound.DocumentRepository
import com.badarak.archive.domain.port.outbound.ObjectStorage
import com.badarak.archive.domain.service.ArchiveDocumentService
import com.badarak.archive.domain.service.DeleteDocumentService
import com.badarak.archive.domain.service.DownloadDocumentService
import com.badarak.archive.domain.service.GetDocumentService
import com.badarak.archive.domain.service.ResumeDeletionsService
import com.badarak.archive.domain.service.SearchDocumentsService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@EnableConfigurationProperties(ArchivingProperties::class, ReconciliationProperties::class)
class ArchivingConfiguration {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun documentSizePolicy(): DocumentSizePolicy = DocumentSizePolicy()

    @Bean
    fun documentFormatPolicy(properties: ArchivingProperties): DocumentFormatPolicy =
        DocumentFormatPolicy.ofExtensions(properties.allowedExtensions)

    @Bean
    fun archiveDocumentUseCase(
        documents: DocumentRepository,
        storage: ObjectStorage,
        formatPolicy: DocumentFormatPolicy,
        sizePolicy: DocumentSizePolicy,
        clock: Clock,
    ): ArchiveDocumentUseCase = ArchiveDocumentService(documents, storage, formatPolicy, sizePolicy, clock)

    @Bean
    fun getDocumentUseCase(documents: DocumentRepository): GetDocumentUseCase = GetDocumentService(documents)

    @Bean
    fun downloadDocumentUseCase(getDocument: GetDocumentUseCase, storage: ObjectStorage): DownloadDocumentUseCase =
        DownloadDocumentService(getDocument, storage)

    @Bean
    fun deleteDocumentUseCase(documents: DocumentRepository, storage: ObjectStorage, clock: Clock): DeleteDocumentUseCase =
        DeleteDocumentService(documents, storage, clock)

    @Bean
    fun resumeDeletionsUseCase(
        documents: DocumentRepository,
        storage: ObjectStorage,
        clock: Clock,
        properties: ReconciliationProperties,
    ): ResumeDeletionsUseCase = ResumeDeletionsService(documents, storage, clock, properties.gracePeriod, properties.batchSize)

    @Bean
    fun searchDocumentsUseCase(documents: DocumentRepository): SearchDocumentsUseCase = SearchDocumentsService(documents)
}
