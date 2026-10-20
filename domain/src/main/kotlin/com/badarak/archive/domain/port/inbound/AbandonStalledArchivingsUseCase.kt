package com.badarak.archive.domain.port.inbound

interface AbandonStalledArchivingsUseCase {

    /**
     * Abandons the archivings left PENDING for longer than the grace period, whatever their tenant, oldest first and one
     * batch at most per call: removes whatever content reached the storage, then records each document as FAILED. Younger
     * ones may still be uploading and are left alone. The caller of such an archiving got an error and archives the file
     * again as a new document, so none is ever made AVAILABLE here. Running it on several instances at once is harmless.
     *
     * @return the number of archivings abandoned
     * @throws com.badarak.archive.domain.port.outbound.ObjectStorageException when the storage cannot be reached; the
     * archivings abandoned before are kept and the others wait for the next call
     * @throws com.badarak.archive.domain.port.outbound.DocumentPersistenceException when the document store cannot be reached
     */
    fun abandon(): Int
}
