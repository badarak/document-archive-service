package com.badarak.archive.infrastructure.config

import ch.qos.logback.classic.LoggerContext
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class OpenTelemetryLogsConfigurationTest {

    private val root = (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(Logger.ROOT_LOGGER_NAME)
    private val runner = ApplicationContextRunner()
        .withUserConfiguration(OpenTelemetryLogsConfiguration::class.java)
        .withBean(OpenTelemetry::class.java, { OpenTelemetry.noop() })

    @AfterEach
    fun detachTheAppender() {
        root.iteratorForAppenders().asSequence().filterIsInstance<OpenTelemetryAppender>().toList().forEach(root::detachAppender)
    }

    @Test
    fun `with the observability profile, the log lines are sent over OTLP as well`() {
        runner.withPropertyValues("spring.profiles.active=observability").run { assertEquals(1, openTelemetryAppenders()) }
    }

    @Test
    fun `without it, the logs only go to the console`() {
        runner.run { assertEquals(0, openTelemetryAppenders()) }
    }

    private fun openTelemetryAppenders(): Int =
        root.iteratorForAppenders().asSequence().count { it is OpenTelemetryAppender && it.isStarted }
}
