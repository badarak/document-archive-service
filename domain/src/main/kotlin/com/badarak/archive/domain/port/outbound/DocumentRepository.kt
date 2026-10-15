package com.badarak.archive.domain.port.outbound

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.TenantId

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
}

class DocumentPersistenceException(message: String, cause: Throwable) : RuntimeException(message, cause)
