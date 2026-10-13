package com.badarak.archive.domain

/** Root of every business rule violation raised by the domain. */
sealed class DomainException(message: String) : RuntimeException(message)

class InvalidDocumentTypeException(val value: String) :
    DomainException("Invalid document type '$value': expected ${DocumentType.FORMAT_DESCRIPTION}")
