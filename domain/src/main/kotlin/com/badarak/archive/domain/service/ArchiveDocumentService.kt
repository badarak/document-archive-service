package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.ArchivingAbandonedException
import com.badarak.archive.domain.model.DocumentFormatPolicy
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentSizePolicy
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.FileContent
import com.badarak.archive.domain.model.FileName
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.StoredFile
import com.badarak.archive.domain.port.inbound.ArchiveDocumentCommand
import com.badarak.archive.domain.port.inbound.ArchiveDocumentUseCase
import com.badarak.archive.domain.port.outbound.ContentInspector
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.DocumentRepository
import com.badarak.archive.domain.port.outbound.ObjectStorage
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import java.io.IOException
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
    private val inspector: ContentInspector,
    private val sizePolicy: DocumentSizePolicy,
    private val clock: Clock,
) : ArchiveDocumentUseCase {

    private val failure = ArchivingFailure(documents, storage, clock)

    override fun archive(command: ArchiveDocumentCommand): ArchivedDocument {
        val pending = admit(command)
        documents.create(pending)
        upload(pending, command.file.content)
        return complete(pending)
    }

    private fun admit(command: ArchiveDocumentCommand): ArchivedDocument {
        val file = command.file
        val fileName = FileName.of(file.fileName)
        sizePolicy.check(file.sizeBytes)
        val format = formatPolicy.formatOf(fileName)
        val mediaType = formatPolicy.check(format, file.contentType, inspector.inspect(file.content, file.sizeBytes, format))
        return ArchivedDocument.create(
            id = DocumentId.generate(),
            owner = DocumentOwner(command.caller.tenantId, command.caller.source),
            metadata = command.metadata,
            file = StoredFile(fileName.value, mediaType, file.sizeBytes, ObjectKey.generate()),
            now = clock.instant(),
        )
    }

    private fun upload(pending: ArchivedDocument, content: FileContent) {
        try {
            content.open().use { storage.store(pending.file.objectKey, it, pending.file.sizeBytes, pending.file.contentType) }
        } catch (e: ObjectStorageException) {
            recordFailure(pending, e)
            throw e
        } catch (e: IOException) {
            recordFailure(pending, e)
            throw e
        }
    }

    private fun complete(pending: ArchivedDocument): ArchivedDocument {
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

    private fun recordFailure(pending: ArchivedDocument, cause: Exception) {
        try {
            failure.record(pending)
        } catch (e: ObjectStorageException) {
            cause.addSuppressed(e)
        } catch (e: DocumentPersistenceException) {
            cause.addSuppressed(e)
        }
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
}
