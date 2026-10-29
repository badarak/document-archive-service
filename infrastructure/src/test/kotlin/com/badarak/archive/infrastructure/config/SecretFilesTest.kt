package com.badarak.archive.infrastructure.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import java.nio.file.Files
import java.nio.file.Path

/** The secrets rendered as files, one per variable, feed the configuration as the variables do. */
class SecretFilesTest {

    @TempDir
    private lateinit var secrets: Path

    @Test
    fun `a secret file named after a variable provides its value`() {
        Files.writeString(secrets.resolve("ARCHIVE_DB_PASSWORD"), "from-vault")

        assertEquals("from-vault", environment().getProperty("spring.datasource.password"))
    }

    @Test
    fun `an environment variable wins over the file of the same secret`() {
        Files.writeString(secrets.resolve("ARCHIVE_DB_PASSWORD"), "from-vault")

        assertEquals("from-environment", environment("ARCHIVE_DB_PASSWORD" to "from-environment").getProperty("spring.datasource.password"))
    }

    @Test
    fun `the application starts without any secret directory`() {
        val environment = StandardEnvironment().apply {
            propertySources.addFirst(MapPropertySource("test", mapOf("ARCHIVE_SECRETS_DIR" to secrets.resolve("missing").toString())))
        }

        ConfigDataEnvironmentPostProcessor.applyTo(environment)

        assertEquals("document-archive-service", environment.getProperty("spring.application.name"))
    }

    private fun environment(vararg variables: Pair<String, String>): StandardEnvironment = StandardEnvironment().apply {
        propertySources.addFirst(MapPropertySource("variables", mapOf("ARCHIVE_SECRETS_DIR" to secrets.toString()) + variables))
        ConfigDataEnvironmentPostProcessor.applyTo(this)
    }
}
