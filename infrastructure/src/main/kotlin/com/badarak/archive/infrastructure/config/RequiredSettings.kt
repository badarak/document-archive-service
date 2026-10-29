package com.badarak.archive.infrastructure.config

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.validation.annotation.Validated

/**
 * Checks at startup the Spring Boot settings the service cannot run safely without, so that an empty value stops it
 * instead of failing at the first request. An empty audience list matters most: Spring would then accept tokens issued
 * for any other service. Only emptiness is checked, so that a failure never prints a secret value.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataSourceSettings::class, MigrationSettings::class, TokenSettings::class)
class RequiredSettingsConfiguration

/** The database connection: ARCHIVE_DB_URL, ARCHIVE_DB_USERNAME and ARCHIVE_DB_PASSWORD. */
@Validated
@ConfigurationProperties("spring.datasource")
class DataSourceSettings(@field:NotBlank val url: String?, @field:NotBlank val username: String?, @field:NotBlank val password: String?)

/** The account of the migrations, required while they run at startup: ARCHIVE_DB_MIGRATOR_USERNAME and ARCHIVE_DB_MIGRATOR_PASSWORD. */
@Validated
@ConfigurationProperties("spring.liquibase")
class MigrationSettings(val enabled: Boolean = true, val user: String?, val password: String?) {
    @get:AssertTrue(message = "the migrations need their own account: set ARCHIVE_DB_MIGRATOR_USERNAME and ARCHIVE_DB_MIGRATOR_PASSWORD")
    val accountSet: Boolean
        get() = !enabled || (!user.isNullOrBlank() && !password.isNullOrBlank())
}

/** The authorization server: ARCHIVE_JWT_ISSUER, ARCHIVE_JWT_JWKS_URI and ARCHIVE_JWT_AUDIENCE. */
@Validated
@ConfigurationProperties("spring.security.oauth2.resourceserver.jwt")
class TokenSettings(
    @field:NotBlank val issuerUri: String?,
    @field:NotBlank val jwkSetUri: String?,
    @field:NotEmpty val audiences: List<String>?,
) {
    @get:AssertTrue(message = "must not hold a blank audience")
    val audiencesNotBlank: Boolean
        get() = audiences.orEmpty().none { it.isBlank() }
}
