package com.badarak.archive.infrastructure.config

import com.badarak.archive.infrastructure.adapter.storage.S3StorageProperties
import com.badarak.archive.infrastructure.adapter.web.CallerClaimsProperties
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class RequiredSettingsTest {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(RequiredSettingsConfiguration::class.java, OwnSettings::class.java)
        .withPropertyValues(*COMPLETE.map { (name, value) -> "$name=$value" }.toTypedArray())

    @Test
    fun `a complete configuration starts`() {
        runner.run { context -> assertNull(context.startupFailure) }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri", "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            "spring.security.oauth2.resourceserver.jwt.audiences", "archive.storage.s3.region", "archive.storage.s3.bucket",
            "archive.storage.s3.access-key", "archive.storage.s3.secret-key", "archive.security.claims.tenant",
            "archive.security.claims.source",
        ],
    )
    fun `an empty required setting stops the startup, naming the setting`(name: String) {
        runner.withPropertyValues("$name=").run { context ->
            val failure = messages(context.startupFailure)

            assertTrue(
                failure.contains(name.substringBeforeLast('.')) && failure.contains("'${camelCase(name.substringAfterLast('.'))}'"),
                failure,
            )
        }
    }

    @Test
    fun `an empty audience among others stops the startup, as it would match any token`() {
        runner.withPropertyValues("spring.security.oauth2.resourceserver.jwt.audiences=document-archive-service, ").run { context ->
            assertTrue(messages(context.startupFailure).contains("must not hold a blank audience"))
        }
    }

    @Test
    fun `a failure never prints the value of a secret`() {
        runner.withPropertyValues("spring.datasource.url=").run { context ->
            val failure = messages(context.startupFailure)

            assertTrue(failure.contains("'url'"), failure)
            assertFalse(failure.contains(SECRET), failure)
        }
    }

    private fun messages(failure: Throwable?): String =
        generateSequence(requireNotNull(failure) { "The context should not start" }) { it.cause }.joinToString(" | ") { "${it.message}" }

    private fun camelCase(name: String): String = name.split('-').mapIndexed { index, part ->
        if (index ==
            0
        ) {
            part
        } else {
            part.replaceFirstChar(Char::uppercase)
        }
    }.joinToString("")

    @EnableConfigurationProperties(S3StorageProperties::class, CallerClaimsProperties::class)
    class OwnSettings

    private companion object {
        const val SECRET = "s3cr3t-sentinel"
        val COMPLETE = mapOf(
            "spring.datasource.url" to "jdbc:postgresql://db/archive",
            "spring.datasource.username" to "archive_app",
            "spring.datasource.password" to SECRET,
            "spring.security.oauth2.resourceserver.jwt.issuer-uri" to "https://auth.test",
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri" to "https://auth.test/jwks",
            "spring.security.oauth2.resourceserver.jwt.audiences" to "document-archive-service",
            "archive.storage.s3.endpoint" to "https://r2.test",
            "archive.storage.s3.region" to "auto",
            "archive.storage.s3.bucket" to "archive-documents",
            "archive.storage.s3.access-key" to "access",
            "archive.storage.s3.secret-key" to SECRET,
            "archive.security.claims.tenant" to "tenant_id",
            "archive.security.claims.source" to "azp",
        )
    }
}
