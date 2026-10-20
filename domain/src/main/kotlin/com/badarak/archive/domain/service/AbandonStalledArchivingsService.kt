package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.port.inbound.AbandonStalledArchivingsUseCase
import com.badarak.archive.domain.port.outbound.DocumentRepository
import com.badarak.archive.domain.port.outbound.ObjectStorage
import java.time.Clock
import java.time.Duration

class AbandonStalledArchivingsService(
    private val documents: DocumentRepository,
    storage: ObjectStorage,
    private val clock: Clock,
    private val gracePeriod: Duration,
    private val batchSize: Int,
) : AbandonStalledArchivingsUseCase {

    private val failure = ArchivingFailure(documents, storage, clock)

    init {
        require(!gracePeriod.isNegative) { "The grace period must not be negative, got $gracePeriod" }
        require(batchSize > 0) { "The batch size must be positive, got $batchSize" }
    }

    override fun abandon(): Int {
        val stalled = documents.findStalled(DocumentStatus.PENDING, clock.instant().minus(gracePeriod), batchSize)
        return stalled.count(failure::record)
    }
}
