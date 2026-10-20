package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.ArchivingAbandonedException
import com.badarak.archive.domain.model.DocumentFormat
import com.badarak.archive.domain.model.DocumentFormatPolicy
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentSizePolicy
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.FileName
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.StoredFile
import com.badarak.archive.domain.port.inbound.ArchiveDocumentCommand
import com.badarak.archive.domain.port.inbound.ArchiveDocumentUseCase
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.DocumentRepository
import com.badarak.archive.domain.port.outbound.ObjectStorage
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import java.io.InputStream
import java.time.Clock

/**
 * Archives a document: validates it, records it as PENDING, uploads its content, then records it
 * as AVAILABLE.
 *
 * A refused document leaves no trace. When the upload or the final save fails, whatever content
 * reached the storage is removed, the document is recorded as FAILED and the error is rethrown; if
 * the removal or that save fails too, its error is attached to the original one as suppressed and
 * the document stays PENDING until it is abandoned. If the document was abandoned while its
 * content was uploading, that content is removed and [ArchivingAbandonedException] is thrown. A
 * document is never AVAILABLE without its content, nor FAILED with one.
 */
class ArchiveDocumentService(
    private val documents: DocumentRepository,
    private val storage: ObjectStorage,
    private val formatPolicy: DocumentFormatPolicy,
    private val sizePolicy: DocumentSizePolicy,
    private val clock: Clock,
) : ArchiveDocumentUseCase {

    private val failure = ArchivingFailure(documents, storage, clock)

    override fun archive(command: ArchiveDocumentCommand): ArchivedDocument {
        val file = command.file
        val stream = file.content.buffered()
        val fileName = FileName.of(file.fileName)
        sizePolicy.check(file.sizeBytes)
        formatPolicy.check(fileName, file.contentType, readHeader(stream))
        val pending = ArchivedDocument.create(
            id = DocumentId.generate(),
            owner = DocumentOwner(command.caller.tenantId, command.caller.source),
            metadata = command.metadata,
            file = StoredFile(fileName.value, formatPolicy.mediaType(file.contentType), file.sizeBytes, ObjectKey.generate()),
            sizePolicy = sizePolicy,
            now = clock.instant(),
        )

        documents.create(pending)
        try {
            storage.store(pending.file.objectKey, stream, pending.file.sizeBytes, pending.file.contentType)
        } catch (e: ObjectStorageException) {
            recordFailure(pending, e)
            throw e
        }
        val available = pending.markAvailable(clock.instant())
        val recorded = try {
            documents.transition(available, DocumentStatus.PENDING)
        } catch (e: DocumentPersistenceException) {
            recordFailure(pending, e)
            throw e
        }
        if (!recorded) throw abandoned(pending)
        return available
    }

    private fun abandoned(pending: ArchivedDocument): ArchivingAbandonedException {
        val error = ArchivingAbandonedException(pending.id)
        try {
            storage.delete(pending.file.objectKey)
        } catch (e: ObjectStorageException) {
            error.addSuppressed(e)
        }
        return error
    }

    private fun readHeader(stream: InputStream): ByteArray {
        stream.mark(DocumentFormat.HEADER_LENGTH)
        val header = stream.readNBytes(DocumentFormat.HEADER_LENGTH)
        stream.reset()
        return header
    }

    private fun recordFailure(pending: ArchivedDocument, cause: Exception) {
        try {
            failure.record(pending)
        } catch (e: ObjectStorageException) {
            cause.addSuppressed(e)
        } catch (e: DocumentPersistenceException) {
            cause.addSuppressed(e)
        }
    }
}
