package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.port.outbound.ObjectStorage
import com.badarak.archive.infrastructure.adapter.persistence.SpringDataArchivedDocumentRepository
import com.badarak.archive.infrastructure.journey.InMemoryObjectStorage
import com.badarak.archive.infrastructure.security.TestAuthorizationServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClient
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * The uploaded file goes through a temporary file, as Tomcat writes it, and that file is removed once the request is
 * answered, whatever its outcome. MockMvc keeps the parts in memory, hence a real server here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(UploadTemporaryFilesIT.Doubles::class)
class UploadTemporaryFilesIT {

    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var storage: DiskWatchingStorage

    @Autowired
    private lateinit var rows: SpringDataArchivedDocumentRepository

    @AfterEach
    fun reset() {
        rows.deleteAll()
        storage.reset()
    }

    @Test
    fun `an archived file is uploaded from its temporary file, which is then removed`() {
        assertEquals(201, upload("invoice.pdf", PDF))

        assertTrue(storage.temporaryFilesDuringUpload > 0)
        assertNoTemporaryFileLeft()
    }

    @Test
    fun `the temporary file of a rejected file is removed`() {
        assertEquals(415, upload("invoice.pdf", EXECUTABLE))

        assertNoTemporaryFileLeft()
    }

    @Test
    fun `the temporary file is removed when the storage fails`() {
        storage.delegate.unavailable = true

        assertEquals(503, upload("invoice.pdf", PDF))

        assertNoTemporaryFileLeft()
    }

    private fun upload(fileName: String, content: ByteArray): Int {
        val body = MultipartBodyBuilder().apply {
            part("file", named(fileName, content), MediaType.APPLICATION_PDF)
            part("metadata", """{"documentType":"INVOICE","businessReference":"INV-1"}""", MediaType.APPLICATION_JSON)
        }.build()
        return RestClient.create("http://localhost:$port").post().uri(DOCUMENTS)
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${TestAuthorizationServer.token(scope = WRITE)}")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .exchange { _, response -> response.statusCode.value() }
    }

    private fun named(fileName: String, content: ByteArray) = object : ByteArrayResource(content) {
        override fun getFilename(): String = fileName
    }

    private fun assertNoTemporaryFileLeft() {
        val deadline = System.nanoTime() + 2_000_000_000
        while (temporaryFiles() > 0 && System.nanoTime() < deadline) Thread.sleep(20)
        assertEquals(0, temporaryFiles())
    }

    @TestConfiguration
    class Doubles {

        @Bean
        @Primary
        fun diskWatchingStorage(): DiskWatchingStorage = DiskWatchingStorage(InMemoryObjectStorage())
    }

    /** Counts the temporary files present while a content is uploaded, to show that the upload reads one. */
    class DiskWatchingStorage(val delegate: InMemoryObjectStorage) : ObjectStorage by delegate {
        var temporaryFilesDuringUpload = 0

        override fun store(key: ObjectKey, content: InputStream, sizeBytes: Long, contentType: String) {
            temporaryFilesDuringUpload = temporaryFiles()
            delegate.store(key, content, sizeBytes, contentType)
        }

        fun reset() {
            delegate.reset()
            temporaryFilesDuringUpload = 0
        }
    }

    companion object {
        private const val DOCUMENTS = "/api/v1/documents"
        private const val WRITE = "documents:write"
        private val PDF: ByteArray = "%PDF-1.7\n".toByteArray() + ByteArray(64)
        private val EXECUTABLE: ByteArray = "MZ".toByteArray() + ByteArray(64)
        private val uploads: Path = Files.createTempDirectory("archive-uploads")

        private fun temporaryFiles(): Int = Files.list(uploads).use { it.count().toInt() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") { TestAuthorizationServer.jwksUri }
            registry.add("spring.servlet.multipart.location") { uploads.toString() }
        }
    }
}
