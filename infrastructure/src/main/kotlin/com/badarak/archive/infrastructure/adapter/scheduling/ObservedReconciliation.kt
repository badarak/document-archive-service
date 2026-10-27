package com.badarak.archive.infrastructure.adapter.scheduling

import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.port.inbound.AbandonStalledArchivingsUseCase
import com.badarak.archive.domain.port.inbound.ReconciliationOutcome
import com.badarak.archive.domain.port.inbound.ResumeDeletionsUseCase

/** Resumes the deletions left in progress through [delegate], and logs and measures what it did with [report]. */
class ObservedResumeDeletions(private val delegate: ResumeDeletionsUseCase, private val report: ReconciliationReport) :
    ResumeDeletionsUseCase {

    override fun resume(): ReconciliationOutcome = report.of(DocumentStatus.DELETING) { delegate.resume() }
}

/** Abandons the archivings left pending through [delegate], and logs and measures what it did with [report]. */
class ObservedAbandonStalledArchivings(private val delegate: AbandonStalledArchivingsUseCase, private val report: ReconciliationReport) :
    AbandonStalledArchivingsUseCase {

    override fun abandon(): ReconciliationOutcome = report.of(DocumentStatus.PENDING) { delegate.abandon() }
}
