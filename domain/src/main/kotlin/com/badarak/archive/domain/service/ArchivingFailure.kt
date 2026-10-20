package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.port.outbound.DocumentRepository
import com.badarak.archive.domain.port.outbound.ObjectStorage
import java.time.Clock

/**
 * Ends an archiving that did not complete, shared by the archiving itself and the abandonment of the stalled ones: removes
 * whatever content of the PENDING document reached the storage, then records it as FAILED. The content goes first, so a
 * FAILED document never keeps one; when the removal fails, the document stays PENDING until it is abandoned. Replaying it
 * is harmless, since removing a missing object succeeds, and the document is recorded as FAILED only if it is still
 * PENDING.
 */
internal class ArchivingFailure(private val documents: DocumentRepository, private val storage: ObjectStorage, private val clock: Clock) {

    fun record(pending: ArchivedDocument): Boolean {
        storage.delete(pending.file.objectKey)
        return documents.transition(pending.markFailed(clock.instant()), DocumentStatus.PENDING)
    }
}
