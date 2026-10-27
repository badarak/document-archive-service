package com.badarak.archive.infrastructure.adapter.scheduling

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.StalledBacklog
import com.badarak.archive.domain.port.inbound.AbandonStalledArchivingsUseCase
import com.badarak.archive.domain.port.inbound.ReconciliationFailure
import com.badarak.archive.domain.port.inbound.ReconciliationOutcome
import com.badarak.archive.domain.port.inbound.ResumeDeletionsUseCase
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** The logs and the metrics of both reconciliation steps, recorded around their use cases. */
class ObservedReconciliationTest {

    private val now = Instant.parse("2026-10-19T10:00:00Z")
    private val registry = SimpleMeterRegistry()
    private val report = ReconciliationReport(ReconciliationMetrics(Clock.fixed(now, ZoneOffset.UTC)).also { it.bindTo(registry) })
    private val resumeDeletions = mockk<ResumeDeletionsUseCase>()
    private val abandonArchivings = mockk<AbandonStalledArchivingsUseCase>()
    private val deletions = ObservedResumeDeletions(resumeDeletions, report)
    private val archivings = ObservedAbandonStalledArchivings(abandonArchivings, report)

    private val logger = LoggerFactory.getLogger(ReconciliationReport::class.java) as Logger
    private val lines = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun captureTheLines() {
        lines.start()
        logger.addAppender(lines)
    }

    @AfterEach
    fun stopCapturing() {
        logger.detachAppender(lines)
    }

    @Test
    fun `the outcome of the use case is returned unchanged`() {
        val outcome = outcome(completed = listOf(DocumentId.generate()))
        every { resumeDeletions.resume() } returns outcome

        assertSame(outcome, deletions.resume())
    }

    @Test
    fun `every abandoned archiving is a warning naming its document`() {
        val abandoned = DocumentId.generate()
        every { abandonArchivings.abandon() } returns outcome(completed = listOf(abandoned))

        archivings.abandon()

        val line = lines.list.single()
        assertEquals(Level.WARN, line.level)
        assertEquals(
            "Reconciliation abandoned the archiving of document $abandoned, left pending beyond its grace period",
            line.formattedMessage,
        )
        assertEquals(
            mapOf("status" to "pending", "documentId" to abandoned, "outcome" to "abandoned"),
            line.values("status", "documentId", "outcome"),
        )
    }

    @Test
    fun `finished deletions are counted in a single line`() {
        every { resumeDeletions.resume() } returns outcome(completed = List(3) { DocumentId.generate() })

        deletions.resume()

        val line = lines.list.single()
        assertEquals(Level.INFO, line.level)
        assertEquals(mapOf("status" to "deleting", "outcome" to "finished", "count" to 3), line.values("status", "outcome", "count"))
    }

    @Test
    fun `every failed document is a warning with its cause and error code, and so is an interrupted batch`() {
        val failed = DocumentId.generate()
        val locked = ObjectStorageException("Object locked", RuntimeException())
        every { resumeDeletions.resume() } returns outcome(failures = listOf(ReconciliationFailure(failed, locked)), interrupted = true)

        deletions.resume()

        val failure = lines.list.single { it.value("outcome") == "failed" }
        assertEquals(
            "Reconciliation of document $failed among the deletions left in progress failed, retried at the next run: Object locked",
            failure.formattedMessage,
        )
        assertEquals(mapOf("documentId" to failed, "errorCode" to "storage-failure"), failure.values("documentId", "errorCode"))
        assertEquals("Object locked", failure.throwableProxy.message)
        assertTrue(lines.list.any { it.value("outcome") == "interrupted" })
    }

    @Test
    fun `every line carries the reconciliation as its operation in the MDC`() {
        every { abandonArchivings.abandon() } returns outcome(completed = List(2) { DocumentId.generate() })

        archivings.abandon()

        assertTrue(lines.list.all { it.mdcPropertyMap["operation"] == "RECONCILIATION" })
    }

    @Test
    fun `a step that fails is logged, counted, and passed on`() {
        val down = DocumentPersistenceException("Database down", RuntimeException())
        every { resumeDeletions.resume() } throws down

        assertSame(down, assertThrows(DocumentPersistenceException::class.java) { deletions.resume() })

        assertEquals(mapOf("outcome" to "failed", "errorCode" to "persistence-failure"), lines.list.single().values("outcome", "errorCode"))
        assertEquals(1.0, counter("archive.reconciliation.runs", "deleting", "failed"))
    }

    @Test
    fun `each run publishes the backlog left and the age of its oldest document, per status`() {
        every { resumeDeletions.resume() } returns outcome(backlog = StalledBacklog(4, now.minus(Duration.ofMinutes(40))))

        deletions.resume()

        assertEquals(4.0, gauge("archive.reconciliation.backlog", "deleting"))
        assertEquals(2400.0, gauge("archive.reconciliation.backlog.oldest.age", "deleting"))
        assertNull(registry.find("archive.reconciliation.backlog").tag("status", "pending").gauge())
    }

    @Test
    fun `each run counts the documents moved on and those that failed, and how the run ended`() {
        val failure = ReconciliationFailure(DocumentId.generate(), ObjectStorageException("Object locked", RuntimeException()))
        every { abandonArchivings.abandon() } returns outcome(completed = List(2) { DocumentId.generate() }, failures = listOf(failure))
        archivings.abandon()
        every { abandonArchivings.abandon() } returns outcome(failures = List(3) { failure }, interrupted = true)
        archivings.abandon()

        assertEquals(2.0, counter("archive.reconciliation.documents", "pending", "completed"))
        assertEquals(4.0, counter("archive.reconciliation.documents", "pending", "failed"))
        assertEquals(1.0, counter("archive.reconciliation.runs", "pending", "completed"))
        assertEquals(1.0, counter("archive.reconciliation.runs", "pending", "interrupted"))
    }

    private fun gauge(name: String, status: String): Double = registry.get(name).tag("status", status).gauge().value()

    private fun counter(name: String, status: String, outcome: String): Double =
        registry.get(name).tag("status", status).tag("outcome", outcome).counter().count()

    private fun ILoggingEvent.value(key: String): Any? = keyValuePairs.orEmpty().firstOrNull { it.key == key }?.value

    private fun ILoggingEvent.values(vararg keys: String): Map<String, Any?> = keys.associateWith { value(it) }

    private fun outcome(
        completed: List<DocumentId> = emptyList(),
        failures: List<ReconciliationFailure> = emptyList(),
        interrupted: Boolean = false,
        backlog: StalledBacklog = StalledBacklog.EMPTY,
    ) = ReconciliationOutcome(completed, failures, interrupted, backlog)
}
