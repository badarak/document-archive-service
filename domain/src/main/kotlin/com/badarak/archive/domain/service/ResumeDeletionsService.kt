package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.StalledDocumentsPolicy
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

    override fun resume(): Int {
        val stalled = documents.findStalled(DocumentStatus.DELETING, policy.stalledBefore(clock.instant()), policy.batchSize)
        return stalled.count(deletion::finish)
    }
}
