package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.StalledDocumentsPolicy
import com.badarak.archive.domain.port.inbound.ReconciliationOutcome
import com.badarak.archive.domain.port.inbound.ResumeDeletionsUseCase
import com.badarak.archive.domain.port.outbound.DocumentRepository
import com.badarak.archive.domain.port.outbound.ObjectStorage
import java.time.Clock

class ResumeDeletionsService(
    private val documents: DocumentRepository,
    storage: ObjectStorage,
    private val clock: Clock,
    private val policy: StalledDocumentsPolicy,
) : ResumeDeletionsUseCase {

    private val deletion = DocumentDeletion(documents, storage, clock)

    override fun resume(): ReconciliationOutcome {
        val stalledBefore = policy.stalledBefore(clock.instant())
        return StalledDocumentsReconciliation.reconcile(
            stalled = documents.findStalled(DocumentStatus.DELETING, stalledBefore, policy.batchSize),
            backlog = { documents.measureStalled(DocumentStatus.DELETING, stalledBefore) },
            step = deletion::finish,
        )
    }
}
