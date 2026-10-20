package com.badarak.archive.domain.port.outbound

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentPage
import com.badarak.archive.domain.model.DocumentSearchCriteria
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.PageRequest
import com.badarak.archive.domain.model.TenantId
import java.time.Instant

interface DocumentRepository {

    /**
     * Inserts [document], which must be new: an existing document is never replaced.
     *
     * @throws DocumentPersistenceException when the store cannot be reached, or already holds a document with this identifier
     */
    fun create(document: ArchivedDocument)

    /**
     * Records the status and the dates of [document], provided the stored document is still in status [from]. The check
     * and the write are a single atomic operation: of two concurrent transitions from the same status, only one applies.
     *
     * @return true when the transition was recorded, false when the stored document is no longer in [from] or does not exist
     * @throws DocumentPersistenceException when the store cannot be reached
     */
    fun transition(document: ArchivedDocument, from: DocumentStatus): Boolean

    /**
     * Returns the document with this identifier if it belongs to [tenantId], whatever its status, or null: a document of
     * another tenant is never loaded.
     *
     * @throws DocumentPersistenceException when the store cannot be reached
     */
    fun findByIdAndTenant(id: DocumentId, tenantId: TenantId): ArchivedDocument?

    /**
     * Returns the requested page of the documents of [tenantId] in [status] that match [criteria], newest first. Documents
     * created at the same instant are ordered by identifier, so that consecutive pages never overlap.
     *
     * @throws DocumentPersistenceException when the store cannot be reached
     */
    fun search(tenantId: TenantId, status: DocumentStatus, criteria: DocumentSearchCriteria, page: PageRequest): DocumentPage

    /**
     * Returns at most [limit] documents in [status] last updated before [updatedBefore], oldest update first, whatever
     * their tenant. Reserved to internal maintenance tasks: a request made on behalf of a caller goes through
     * [findByIdAndTenant] or [search], which never cross tenants.
     *
     * @throws DocumentPersistenceException when the store cannot be reached
     */
    fun findStalled(status: DocumentStatus, updatedBefore: Instant, limit: Int): List<ArchivedDocument>
}

class DocumentPersistenceException(message: String, cause: Throwable) : RuntimeException(message, cause)
