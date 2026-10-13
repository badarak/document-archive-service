package com.badarak.archive.domain

/** Root of every business rule violation raised by the domain. */
sealed class DomainException(message: String) : RuntimeException(message)

class InvalidDocumentTypeException(val value: String) :
    DomainException("Invalid document type '$value': expected ${DocumentType.FORMAT_DESCRIPTION}")

class InvalidStatusTransitionException(val from: DocumentStatus, val to: DocumentStatus) :
    DomainException("A document cannot move from status $from to $to")

class InvalidDocumentException(val field: String, reason: String) : DomainException("Invalid document $field: $reason")

class EmptyDocumentException(val sizeBytes: Long) : DomainException("A document must not be empty, got a size of $sizeBytes bytes")

class DocumentTooLargeException(val sizeBytes: Long, val maxSizeBytes: Long) :
    DomainException("A document of $sizeBytes bytes exceeds the maximum size of $maxSizeBytes bytes")
