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
     * Inserts [document], or replaces the stored state of the document with the same identifier.
     *
     * @throws DocumentPersistenceException when the store cannot be reached or refuses the document
     */
    fun save(document: ArchivedDocument)

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
