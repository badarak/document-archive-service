package com.badarak.archive.infrastructure.validation

import org.springframework.boot.context.properties.ConfigurationProperties

/** Settings of the checks run on a document before it is archived. */
@ConfigurationProperties("archive.documents")
class DocumentValidationProperties(
    /** Extensions of the formats callers may archive, among those the service supports. */
    val allowedExtensions: List<String>,
)
