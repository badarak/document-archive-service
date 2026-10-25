package com.badarak.archive.domain.port.inbound

interface ResumeDeletionsUseCase {

    /**
     * Finishes the deletions left DELETING for longer than the grace period, whatever their tenant, oldest first and one
     * batch at most per call; younger ones may still be in progress and are left alone. Running it on several instances
     * at once, or while a client replays its deletion, is harmless: every finished document ends up DELETED.
     *
     * Each deletion is finished on its own: one that fails stays DELETING for the next call without holding back the
     * others, and the call stops after several failures in a row (see [ReconciliationOutcome]).
     *
     * @return the deletions finished by this call, and those it could not finish
     * @throws com.badarak.archive.domain.port.outbound.DocumentPersistenceException when the stalled deletions cannot be read
     */
    fun resume(): ReconciliationOutcome
}
