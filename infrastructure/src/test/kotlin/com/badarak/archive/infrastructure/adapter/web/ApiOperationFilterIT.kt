package com.badarak.archive.infrastructure.adapter.web

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.badarak.archive.infrastructure.adapter.persistence.SpringDataArchivedDocumentRepository
import com.badarak.archive.infrastructure.journey.InMemoryObjectStorage
import com.badarak.archive.infrastructure.security.TestAuthorizationServer
import com.jayway.jsonpath.JsonPath
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import java.util.concurrent.TimeUnit

/**
 * The log line written for each operation of the document API, read as the structured logs see it (MDC and key-value
 * pairs), and the timer recorded for it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiOperationFilterIT.Doubles::class)
class ApiOperationFilterIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var rows: SpringDataArchivedDocumentRepository

    @Autowired
    private lateinit var storage: InMemoryObjectStorage

    @Autowired
    private lateinit var meters: MeterRegistry

    private val logger = LoggerFactory.getLogger(ApiOperationFilter::class.java) as Logger
    private val lines = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun captureTheLines() {
        lines.start()
        logger.addAppender(lines)
    }

    @AfterEach
    fun stopCapturing() {
        logger.detachAppender(lines)
        rows.deleteAll()
        storage.reset()
    }

    @Test
    fun `an upload is logged with the document it created, its status, outcome and duration`() {
        val response = upload(file("invoice.pdf", "application/pdf", PDF)).andReturn().response
        val id: String = JsonPath.read(response.contentAsString, "$.id")

        val line = lines.list.single()
        assertEquals(Level.INFO, line.level)
        assertEquals("UPLOAD", line.mdcPropertyMap["operation"])
        assertEquals(id, line.mdcPropertyMap["documentId"])
        assertEquals(201, line.value("status"))
        assertEquals("success", line.value("outcome"))
        assertTrue(line.value("durationMs") is Long)
        assertNull(line.value("errorCode"))
        assertNull(line.value("tenantId"))
    }

    @Test
    fun `a refused upload is logged with the code of its error, never with the name of the file`() {
        upload(file("salaries-2026.txt", "text/plain", "secret".toByteArray()))

        val line = lines.list.single()
        assertEquals(415, line.value("status"))
        assertEquals("client-error", line.value("outcome"))
        assertEquals("unsupported-format", line.value("errorCode"))
        assertFalse(line.formattedMessage.contains("salaries"))
        assertFalse(line.keyValuePairs.orEmpty().any { it.value.toString().contains("salaries") })
    }

    @Test
    fun `a request refused by the security is logged too`() {
        mockMvc.get(DOCUMENTS)

        val line = lines.list.single()
        assertEquals("SEARCH", line.mdcPropertyMap["operation"])
        assertEquals(401, line.value("status"))
        assertEquals("unauthenticated", line.value("errorCode"))
    }

    @Test
    fun `an operation the storage fails is logged as an error, with the document it named`() {
        val id: String = JsonPath.read(upload(file("invoice.pdf", "application/pdf", PDF)).andReturn().response.contentAsString, "$.id")
        storage.unavailable = true
        lines.list.clear()

        mockMvc.get("$DOCUMENTS/$id/content") { bearer(READ) }

        val line = lines.list.single()
        assertEquals(Level.ERROR, line.level)
        assertEquals("DOWNLOAD", line.mdcPropertyMap["operation"])
        assertEquals(id, line.mdcPropertyMap["documentId"])
        assertEquals("server-error", line.value("outcome"))
        assertEquals("archive-unavailable", line.value("errorCode"))
    }

    @Test
    fun `a request outside the document API is not logged`() {
        mockMvc.get("/actuator/health")

        assertTrue(lines.list.isEmpty())
    }

    @Test
    fun `each operation is counted and timed by outcome and error code`() {
        val uploaded = count("archive.upload", "success", "none")
        val refused = count("archive.upload", "client-error", "unsupported-format")
        val unauthenticated = count("archive.search", "client-error", "unauthenticated")

        upload(file("invoice.pdf", "application/pdf", PDF))
        upload(file("notes.txt", "text/plain", "notes".toByteArray()))
        mockMvc.get(DOCUMENTS)

        assertEquals(uploaded + 1, count("archive.upload", "success", "none"))
        assertEquals(refused + 1, count("archive.upload", "client-error", "unsupported-format"))
        assertEquals(unauthenticated + 1, count("archive.search", "client-error", "unauthenticated"))
        assertTrue(meters.get("archive.upload").timer().totalTime(TimeUnit.NANOSECONDS) > 0)
    }

    @Test
    fun `the operation timers carry no identifier among their tags`() {
        upload(file("invoice.pdf", "application/pdf", PDF))

        val keys = meters.find("archive.upload").timers().flatMap { timer -> timer.id.tags.map { it.key } }.toSet()
        assertTrue(setOf("outcome", "error.code", "error", "application").containsAll(keys)) { "Unexpected tags in $keys" }
        assertTrue("application" in keys)
    }

    @Nested
    @TestPropertySource(properties = ["archive.logging.include-tenant=true"])
    inner class WhenThePolicyAllowsTheTenant {

        @Test
        fun `the tenant of the caller is logged`() {
            upload(file("invoice.pdf", "application/pdf", PDF))

            assertEquals("acme", lines.list.single().value("tenantId"))
        }
    }

    private fun upload(file: MockMultipartFile): ResultActionsDsl = mockMvc.multipart(DOCUMENTS) {
        file(file)
        file(MockMultipartFile("metadata", "", "application/json", """{"documentType": "INVOICE"}""".toByteArray()))
        bearer(WRITE)
    }

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.bearer(scope: String) {
        header(HttpHeaders.AUTHORIZATION, "Bearer ${TestAuthorizationServer.token(scope = scope, tenant = "acme")}")
    }

    private fun file(name: String, contentType: String, content: ByteArray) = MockMultipartFile("file", name, contentType, content)

    private fun count(name: String, outcome: String, errorCode: String): Long =
        meters.find(name).tag("outcome", outcome).tag("error.code", errorCode).timer()?.count() ?: 0

    private fun ILoggingEvent.value(key: String): Any? = keyValuePairs.orEmpty().firstOrNull { it.key == key }?.value

    @TestConfiguration
    class Doubles {

        @Bean
        @Primary
        fun inMemoryObjectStorage(): InMemoryObjectStorage = InMemoryObjectStorage()
    }

    companion object {
        private const val DOCUMENTS = "/api/v1/documents"
        private const val READ = "documents:read"
        private const val WRITE = "documents:write"
        private val PDF: ByteArray = "%PDF-1.7\n".toByteArray() + ByteArray(64)

        @JvmStatic
        @DynamicPropertySource
        fun authorizationServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") { TestAuthorizationServer.jwksUri }
        }
    }
}
