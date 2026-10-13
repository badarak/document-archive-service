package com.badarak.archive.domain

/** Root of every business rule violation raised by the domain. */
sealed class DomainException(message: String) : RuntimeException(message)

class InvalidDocumentTypeException(val value: String) :
    DomainException("Invalid document type '$value': expected ${DocumentType.FORMAT_DESCRIPTION}")

class InvalidStatusTransitionException(val from: DocumentStatus, val to: DocumentStatus) :
    DomainException("A document cannot move from status $from to $to")
