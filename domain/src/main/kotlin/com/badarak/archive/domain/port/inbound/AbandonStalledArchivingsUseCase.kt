package com.badarak.archive.domain.port.inbound

interface AbandonStalledArchivingsUseCase {

    /**
     * Abandons the archivings left PENDING for longer than the grace period, whatever their tenant, oldest first and one
     * batch at most per call: removes whatever content reached the storage, then records each document as FAILED. Younger
     * ones may still be uploading and are left alone. The caller of such an archiving got an error and archives the file
     * again as a new document, so none is ever made AVAILABLE here. Running it on several instances at once is harmless.
     *
     * Each archiving is abandoned on its own: one that fails stays PENDING for the next call without holding back the
     * others, and the call stops after several failures in a row (see [ReconciliationOutcome]).
     *
     * @return the archivings abandoned by this call, and those it could not abandon
     * @throws com.badarak.archive.domain.port.outbound.DocumentPersistenceException when the stalled archivings cannot be read
     */
    fun abandon(): ReconciliationOutcome
}
