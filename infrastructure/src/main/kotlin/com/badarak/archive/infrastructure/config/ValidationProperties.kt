package com.badarak.archive.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize

/**
 * Validation of the uploaded files: their maximum size, the extensions callers may archive, and whether a declared content
 * type that does not match the format is rejected or replaced by the media type of the format.
 */
@ConfigurationProperties("archive.validation")
class ValidationProperties(val maxFileSize: DataSize, val allowedExtensions: List<String>, val rejectMimeMismatch: Boolean)
