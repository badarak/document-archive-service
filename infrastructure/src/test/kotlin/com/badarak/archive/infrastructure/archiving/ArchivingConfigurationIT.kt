package com.badarak.archive.infrastructure.archiving

import com.badarak.archive.domain.model.DocumentSizePolicy
import com.badarak.archive.domain.port.inbound.ArchiveDocumentUseCase
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `spring builds the archiving use case with a UTC clock and the 50 MiB size limit`() {
        assertEquals(ZoneOffset.UTC, clock.zone)
        assertEquals(50L * 1024 * 1024, sizePolicy.maxSizeBytes)
    }
}
