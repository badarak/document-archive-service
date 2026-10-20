package com.badarak.archive.infrastructure.adapter.persistence

import jakarta.persistence.EntityManager
import org.springframework.transaction.annotation.Transactional

/**
 * Inserts a new document in a single statement. `save` would merge it instead: its identifier is assigned by the domain, so
 * Spring Data cannot tell it is new and reads the row before writing it.
 */
interface ArchivedDocumentInsertion {

    fun insert(entity: ArchivedDocumentEntity)
}

@Transactional
class ArchivedDocumentInsertionImpl(private val entityManager: EntityManager) : ArchivedDocumentInsertion {

    override fun insert(entity: ArchivedDocumentEntity) {
        entityManager.persist(entity)
    }
}
