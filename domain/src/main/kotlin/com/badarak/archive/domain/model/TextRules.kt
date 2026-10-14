package com.badarak.archive.domain.model

internal fun requireText(field: String, value: String, maxLength: Int) {
    if (value.isBlank()) throw InvalidDocumentException(field, "must not be blank")
    requireMaxLength(field, value, maxLength)
}

internal fun requireMaxLength(field: String, value: String, maxLength: Int) {
    if (value.length > maxLength) throw InvalidDocumentException(field, "must not exceed $maxLength characters")
}
