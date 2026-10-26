package com.badarak.archive.infrastructure.adapter.web

import org.springframework.boot.context.properties.ConfigurationProperties

/** What the log line of each API operation may hold beyond the operation itself: the tenant only when [includeTenant]. */
@ConfigurationProperties("archive.logging")
class OperationLogProperties(val includeTenant: Boolean = false)
