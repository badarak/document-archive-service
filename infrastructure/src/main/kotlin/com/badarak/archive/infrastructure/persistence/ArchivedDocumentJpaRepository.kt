package com.badarak.archive.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ArchivedDocumentJpaRepository : JpaRepository<ArchivedDocumentJpaEntity, UUID>
