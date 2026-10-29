package com.badarak.archive.infrastructure.adapter.web

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("archive.security.claims")
class CallerClaimsProperties(@field:NotBlank val tenant: String, @field:NotBlank val source: String)
