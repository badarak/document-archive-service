package com.badarak.archive.infrastructure.adapter.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.mock.web.MockHttpServletRequest
import java.util.UUID

class ApiOperationTest {

    @ParameterizedTest(name = "{0} {1} is {2}")
    @CsvSource(
        "POST, /api/v1/documents, UPLOAD",
        "GET, /api/v1/documents, SEARCH",
        "GET, /api/v1/documents/7d7d57e1-4c50-4cb9-98b8-c5b60e8a2f01, READ",
        "GET, /api/v1/documents/7d7d57e1-4c50-4cb9-98b8-c5b60e8a2f01/content, DOWNLOAD",
        "DELETE, /api/v1/documents/7d7d57e1-4c50-4cb9-98b8-c5b60e8a2f01, DELETE",
    )
    fun `each operation of the document API is recognised from its method and path`(method: String, path: String, operation: ApiOperation) {
        assertEquals(operation, ApiOperation.of(MockHttpServletRequest(method, path)))
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource("GET, /actuator/health", "PUT, /api/v1/documents/7d7d57e1-4c50-4cb9-98b8-c5b60e8a2f01", "GET, /api/v1/other")
    fun `a request outside the operations of the document API is not recognised`(method: String, path: String) {
        assertNull(ApiOperation.of(MockHttpServletRequest(method, path)))
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource("/api/v1/documents/7d7d57e1-4c50-4cb9-98b8-c5b60e8a2f01", "/api/v1/documents/7d7d57e1-4c50-4cb9-98b8-c5b60e8a2f01/content")
    fun `the document is read from the path`(path: String) {
        val request = MockHttpServletRequest("GET", path)

        assertEquals(UUID.fromString("7d7d57e1-4c50-4cb9-98b8-c5b60e8a2f01"), ApiOperation.of(request)!!.documentId(request))
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource("/api/v1/documents/not-an-id", "/api/v1/documents")
    fun `no document is read from a path without a valid identifier`(path: String) {
        val request = MockHttpServletRequest("GET", path)

        assertNull(ApiOperation.of(request)!!.documentId(request))
    }
}
