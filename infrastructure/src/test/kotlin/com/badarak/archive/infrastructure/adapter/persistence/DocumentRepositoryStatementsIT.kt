package com.badarak.archive.infrastructure.adapter.persistence

import com.badarak.archive.domain.fixture.aDocument
import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentStatus
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@SpringBootTest(
    properties = [
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN",
    ],
)
@ActiveProfiles("test")
class DocumentRepositoryStatementsIT {

    @Autowired
    private lateinit var adapter: DocumentRepositoryAdapter

    @Autowired
    private lateinit var repository: SpringDataArchivedDocumentRepository

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    private val statistics: Statistics by lazy { entityManagerFactory.unwrap(SessionFactory::class.java).statistics }

    private val now = Instant.parse("2026-10-20T09:00:00Z")

    @BeforeEach
    fun resetStatistics() {
        statistics.clear()
    }

    @AfterEach
    fun deleteDocuments() {
        repository.deleteAll()
    }

    @Test
    fun `creating a document takes a single statement, with no read before it`() {
        adapter.create(pendingDocument())

        assertEquals(1, statistics.prepareStatementCount)
    }

    @Test
    fun `a transition takes a single statement, with no read before it`() {
        val pending = pendingDocument()
        adapter.create(pending)
        statistics.clear()

        adapter.transition(pending.markAvailable(now.plusSeconds(1)), DocumentStatus.PENDING)

        assertEquals(1, statistics.prepareStatementCount)
    }

    private fun pendingDocument(): ArchivedDocument = aDocument(DocumentStatus.PENDING, at = now)
}
