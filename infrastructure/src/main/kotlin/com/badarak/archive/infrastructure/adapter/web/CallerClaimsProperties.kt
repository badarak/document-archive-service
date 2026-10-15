package com.badarak.archive.infrastructure.adapter.web

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("archive.security.claims")
class CallerClaimsProperties(val tenant: String, val source: String)
