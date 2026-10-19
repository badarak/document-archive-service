package com.badarak.archive.infrastructure.adapter.persistence

import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.time.Instant
import java.util.UUID

interface SpringDataArchivedDocumentRepository :
    JpaRepository<ArchivedDocumentEntity, UUID>,
    JpaSpecificationExecutor<ArchivedDocumentEntity> {

    fun findByIdAndTenantId(id: UUID, tenantId: String): ArchivedDocumentEntity?

    fun findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
        status: String,
        updatedBefore: Instant,
        limit: Limit,
    ): List<ArchivedDocumentEntity>
}
