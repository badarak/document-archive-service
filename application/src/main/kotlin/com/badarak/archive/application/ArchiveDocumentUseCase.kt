package com.badarak.archive.application

import com.badarak.archive.application.port.DocumentPersistenceException
import com.badarak.archive.application.port.DocumentRepositoryPort
import com.badarak.archive.application.port.ObjectStorageException
import com.badarak.archive.application.port.ObjectStoragePort
import com.badarak.archive.domain.ArchivedDocument
import com.badarak.archive.domain.DocumentFormat
import com.badarak.archive.domain.DocumentFormatPolicy
import com.badarak.archive.domain.DocumentId
import com.badarak.archive.domain.DocumentSizePolicy
import com.badarak.archive.domain.DocumentType
import com.badarak.archive.domain.FileName
import com.badarak.archive.domain.ObjectKey
import java.io.InputStream
import java.time.Clock

/**
 * Archives a document: validates it, records it as PENDING, uploads its content, then records it
 * as AVAILABLE.
 *
 * A refused document leaves no trace. When the upload or the final save fails, the document is
 * recorded as FAILED and the error is rethrown; if even that save fails, its error is attached to
 * the original one as suppressed and the document stays PENDING. A document is never AVAILABLE
 * without its content.
 */
class ArchiveDocumentUseCase(
    private val documents: DocumentRepositoryPort,
    private val storage: ObjectStoragePort,
    private val formatPolicy: DocumentFormatPolicy,
    private val sizePolicy: DocumentSizePolicy,
    private val clock: Clock,
) {

    fun archive(command: ArchiveDocumentCommand): ArchivedDocument {
        val content = command.content.buffered()
        val fileName = FileName.of(command.fileName)
        sizePolicy.check(command.sizeBytes)
        formatPolicy.check(fileName, command.contentType, readHeader(content))
        val pending = ArchivedDocument.create(
            id = DocumentId.generate(),
            tenantId = command.caller.tenantId,
            documentType = DocumentType(command.documentType),
            source = command.caller.source,
            businessReference = command.businessReference,
            description = command.description,
            tags = command.tags,
            fileName = fileName.value,
            contentType = formatPolicy.mediaType(command.contentType),
            sizeBytes = command.sizeBytes,
            objectKey = ObjectKey.generate(),
            sizePolicy = sizePolicy,
            now = clock.instant(),
        )

        documents.save(pending)
        try {
            storage.store(pending.objectKey, content, pending.sizeBytes, pending.contentType)
        } catch (e: ObjectStorageException) {
            recordFailure(pending, e)
            throw e
        }
        val available = pending.markAvailable(clock.instant())
        try {
            documents.save(available)
        } catch (e: DocumentPersistenceException) {
            recordFailure(pending, e)
            throw e
        }
        return available
    }

    private fun readHeader(content: InputStream): ByteArray {
        content.mark(DocumentFormat.HEADER_LENGTH)
        val header = content.readNBytes(DocumentFormat.HEADER_LENGTH)
        content.reset()
        return header
    }

    private fun recordFailure(pending: ArchivedDocument, cause: Exception) {
        try {
            documents.save(pending.markFailed(clock.instant()))
        } catch (e: DocumentPersistenceException) {
            cause.addSuppressed(e)
        }
    }
}
