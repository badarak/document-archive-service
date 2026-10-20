package com.badarak.archive.domain.model

sealed class DomainException(message: String) : RuntimeException(message)

class InvalidDocumentTypeException(val value: String) :
    DomainException("Invalid document type '$value': expected ${DocumentType.FORMAT_DESCRIPTION}")

class InvalidStatusTransitionException(val from: DocumentStatus, val to: DocumentStatus) :
    DomainException("A document cannot move from status $from to $to")

class InvalidDocumentException(val field: String, reason: String) : DomainException("Invalid document $field: $reason")

class EmptyDocumentException(val sizeBytes: Long) : DomainException("A document must not be empty, got a size of $sizeBytes bytes")

class DocumentTooLargeException(val sizeBytes: Long, val maxSizeBytes: Long) :
    DomainException("A document of $sizeBytes bytes exceeds the maximum size of $maxSizeBytes bytes")

class UnsupportedDocumentFormatException(val fileName: String, val allowedExtensions: List<String>) :
    DomainException("The format of '$fileName' is not supported: allowed extensions are ${allowedExtensions.joinToString()}")

class ContentTypeMismatchException(val declaredContentType: String, val format: DocumentFormat) :
    DomainException(
        "Content type '$declaredContentType' does not match a .${format.extension} file: " +
            "expected ${format.contentTypes.joinToString(" or ")}",
    )

class ContentMismatchException(val format: DocumentFormat) : DomainException("The content does not match a .${format.extension} file")

class InvalidTenantIdException(val value: String) :
    DomainException("Invalid tenant identifier '$value': expected 1 to ${TenantId.MAX_LENGTH} characters, not only spaces")

class ArchivingAbandonedException(val id: DocumentId) :
    DomainException("The archiving of document $id was abandoned before it completed")

class DocumentNotFoundException(val id: DocumentId) : DomainException("Document $id not found")

class InvalidSearchException(val parameter: String, reason: String) : DomainException("Invalid search $parameter: $reason")
