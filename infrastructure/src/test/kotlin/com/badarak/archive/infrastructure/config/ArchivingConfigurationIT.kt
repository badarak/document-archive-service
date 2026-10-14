package com.badarak.archive.infrastructure.config

import com.badarak.archive.domain.model.DocumentFormat
import com.badarak.archive.domain.model.DocumentFormatPolicy
import com.badarak.archive.domain.model.DocumentSizePolicy
import com.badarak.archive.domain.port.inbound.ArchiveDocumentUseCase
import com.badarak.archive.domain.service.ArchiveDocumentService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Clock
import java.time.ZoneOffset

@SpringBootTest
@ActiveProfiles("test")
class ArchivingConfigurationIT {

    @Autowired
    private lateinit var useCase: ArchiveDocumentUseCase

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var sizePolicy: DocumentSizePolicy

    @Autowired
    private lateinit var formatPolicy: DocumentFormatPolicy

    @Test
    fun `spring builds the archiving service with a UTC clock and the 50 MiB size limit`() {
        assertInstanceOf(ArchiveDocumentService::class.java, useCase)
        assertEquals(ZoneOffset.UTC, clock.zone)
        assertEquals(50L * 1024 * 1024, sizePolicy.maxSizeBytes)
    }

    @Test
    fun `every supported format is allowed when no extension list is configured`() {
        assertEquals(DocumentFormat.entries.toSet(), formatPolicy.allowedFormats)
    }
}
