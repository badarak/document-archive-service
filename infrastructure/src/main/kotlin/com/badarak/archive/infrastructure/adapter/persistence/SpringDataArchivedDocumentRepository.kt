package com.badarak.archive.infrastructure.adapter.persistence

import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
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

    /** Moves the document from status [from] to [to] and returns the number of rows changed: 0 when it is no longer in [from]. */
    @Modifying
    @Transactional
    @Query(
        "update ArchivedDocumentEntity d set d.status = :to, d.updatedAt = :updatedAt, d.deletedAt = :deletedAt " +
            "where d.id = :id and d.status = :from",
    )
    fun updateStatus(id: UUID, from: String, to: String, updatedAt: Instant, deletedAt: Instant?): Int
}
