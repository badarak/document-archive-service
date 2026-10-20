package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.StalledDocumentsPolicy
import com.badarak.archive.domain.port.inbound.AbandonStalledArchivingsUseCase
import com.badarak.archive.domain.port.outbound.DocumentRepository
import com.badarak.archive.domain.port.outbound.ObjectStorage
import java.time.Clock

class AbandonStalledArchivingsService(
    private val documents: DocumentRepository,
    storage: ObjectStorage,
    private val clock: Clock,
    private val policy: StalledDocumentsPolicy,
) : AbandonStalledArchivingsUseCase {

    private val failure = ArchivingFailure(documents, storage, clock)

    override fun abandon(): Int {
        val stalled = documents.findStalled(DocumentStatus.PENDING, policy.stalledBefore(clock.instant()), policy.batchSize)
        return stalled.count(failure::record)
    }
}
