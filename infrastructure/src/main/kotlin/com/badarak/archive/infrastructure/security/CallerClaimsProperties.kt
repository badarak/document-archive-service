package com.badarak.archive.infrastructure.security

import org.springframework.boot.context.properties.ConfigurationProperties

/** Names of the access token claims that identify the caller. */
@ConfigurationProperties("archive.security.claims")
class CallerClaimsProperties(
    /** Claim holding the tenant the caller acts for. */
    val tenant: String,
    /** Claim holding the name of the calling service. */
    val source: String,
)
