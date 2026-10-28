package com.badarak.archive.infrastructure.config

import ch.qos.logback.classic.LoggerContext
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Sends every log line over OTLP as well, with the observability profile only: the local Grafana stack then shows the
 * logs next to the metrics and traces. Each line keeps its key-value pairs and its MDC as attributes, and is tied to the
 * trace of its request. The console output does not change.
 */
@Configuration(proxyBeanMethods = false)
@Profile("observability")
class OpenTelemetryLogsConfiguration(private val openTelemetry: OpenTelemetry) : InitializingBean {

    override fun afterPropertiesSet() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val appender = OpenTelemetryAppender().apply {
            this.context = context
            setOpenTelemetry(openTelemetry)
            setCaptureKeyValuePairAttributes(true)
            setCaptureMdcAttributes("*")
            start()
        }
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender)
    }
}
