package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.port.outbound.DocumentRepository
import com.badarak.archive.domain.port.outbound.ObjectStorage
import java.time.Clock

/**
 * Last step of a deletion, shared by the deletion requested by a client and its resumption: removes the content of a
 * DELETING document from the storage, then records it as DELETED. Replaying it is harmless, since removing a missing
 * object succeeds and the document is recorded as DELETED only if it is still DELETING.
 */
internal class DocumentDeletion(private val documents: DocumentRepository, private val storage: ObjectStorage, private val clock: Clock) {

    fun finish(deleting: ArchivedDocument): Boolean {
        storage.delete(deleting.file.objectKey)
        return documents.transition(deleting.markDeleted(clock.instant()), DocumentStatus.DELETING)
    }
}
