package com.badarak.archive.infrastructure.adapter.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataArchivedDocumentRepository : JpaRepository<ArchivedDocumentEntity, UUID> {

    fun findByIdAndTenantId(id: UUID, tenantId: String): ArchivedDocumentEntity?
}
