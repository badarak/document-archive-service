package com.badarak.archive.infrastructure.validation

import com.badarak.archive.domain.DocumentFormat
import com.badarak.archive.domain.DocumentFormatPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class DocumentValidationConfigurationIT {

    @Autowired
    private lateinit var documentFormatPolicy: DocumentFormatPolicy

    @Test
    fun `every supported format is allowed when no extension list is configured`() {
        assertEquals(DocumentFormat.entries.toSet(), documentFormatPolicy.allowedFormats)
    }
}
