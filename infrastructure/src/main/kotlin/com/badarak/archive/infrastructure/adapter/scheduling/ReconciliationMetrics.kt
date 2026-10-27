package com.badarak.archive.infrastructure.adapter.scheduling

import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.StalledBacklog
import com.badarak.archive.domain.port.inbound.ReconciliationOutcome
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Publishes what the reconciliation runs do, tagged with the status the documents are left in (pending archivings,
 * deleting deletions), so that a blocked or lagging reconciliation can raise an alert:
 * - `archive.reconciliation.backlog`: documents still stalled after the last run;
 * - `archive.reconciliation.backlog.oldest.age`: seconds since the oldest of them was last updated, 0 when there is none;
 * - `archive.reconciliation.documents`: documents moved on (`outcome=completed`) or not (`outcome=failed`);
 * - `archive.reconciliation.runs`: runs over (`completed`), stopped after failures in a row (`interrupted`), or unable to
 * read the stalled documents (`failed`).
 *
 * The only component of the reconciliation that knows the meter registry, which Spring Boot hands to it. A status gets its
 * gauges with its first run: while the reconciliation is disabled, they are missing rather than reporting an empty backlog.
 */
class ReconciliationMetrics(private val clock: Clock) : MeterBinder {

    private lateinit var registry: MeterRegistry
    private val backlogs = ConcurrentHashMap<DocumentStatus, AtomicReference<StalledBacklog>>()

    override fun bindTo(registry: MeterRegistry) {
        this.registry = registry
    }

    fun record(status: DocumentStatus, outcome: ReconciliationOutcome) {
        backlog(status).set(outcome.backlog)
        documents(status, "completed").increment(outcome.completed.size.toDouble())
        documents(status, "failed").increment(outcome.failures.size.toDouble())
        runs(status, if (outcome.interrupted) "interrupted" else "completed").increment()
    }

    fun failed(status: DocumentStatus) {
        runs(status, "failed").increment()
    }

    private fun backlog(status: DocumentStatus): AtomicReference<StalledBacklog> = backlogs.computeIfAbsent(status) {
        AtomicReference(StalledBacklog.EMPTY).also { backlog ->
            Gauge.builder("archive.reconciliation.backlog", backlog) { it.get().count.toDouble() }
                .description("Documents still stalled after the last reconciliation run")
                .tag(STATUS, tag(status))
                .register(registry)
            Gauge.builder("archive.reconciliation.backlog.oldest.age", backlog) { age(it.get()) }
                .description("Seconds since the oldest stalled document was last updated")
                .baseUnit("seconds")
                .tag(STATUS, tag(status))
                .register(registry)
        }
    }

    private fun age(backlog: StalledBacklog): Double =
        backlog.oldestUpdatedAt?.let { Duration.between(it, clock.instant()).toSeconds().toDouble() } ?: 0.0

    private fun documents(status: DocumentStatus, outcome: String): Counter = Counter.builder("archive.reconciliation.documents")
        .description("Stalled documents handled by the reconciliation")
        .tags(STATUS, tag(status), OUTCOME, outcome)
        .register(registry)

    private fun runs(status: DocumentStatus, outcome: String): Counter = Counter.builder("archive.reconciliation.runs")
        .description("Reconciliation runs")
        .tags(STATUS, tag(status), OUTCOME, outcome)
        .register(registry)

    private fun tag(status: DocumentStatus) = status.name.lowercase()

    private companion object {
        const val STATUS = "status"
        const val OUTCOME = "outcome"
    }
}
