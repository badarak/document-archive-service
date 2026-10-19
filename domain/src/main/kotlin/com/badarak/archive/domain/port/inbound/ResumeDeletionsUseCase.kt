package com.badarak.archive.domain.port.inbound

interface ResumeDeletionsUseCase {

    /**
     * Finishes the deletions left DELETING for longer than the grace period, whatever their tenant, oldest first and one
     * batch at most per call; younger ones may still be in progress and are left alone. Running it on several instances
     * at once, or while a client replays its deletion, is harmless: every finished document ends up DELETED.
     *
     * @return the number of deletions finished
     * @throws com.badarak.archive.domain.port.outbound.ObjectStorageException when the storage cannot be reached; the
     * deletions finished before are kept and the others wait for the next call
     * @throws com.badarak.archive.domain.port.outbound.DocumentPersistenceException when the document store cannot be reached
     */
    fun resume(): Int
}
