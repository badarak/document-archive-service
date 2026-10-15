package com.badarak.archive.infrastructure.adapter.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.ProblemDetail
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.multipart.MaxUploadSizeExceededException

class ApiExceptionHandlerTest {

    @Test
    fun `a request above the multipart limit is answered with 413 and the document-too-large code`() {
        val response = ApiExceptionHandler().handleException(
            MaxUploadSizeExceededException(52_428_800),
            ServletWebRequest(MockHttpServletRequest()),
        )!!

        val problem = response.body as ProblemDetail
        assertEquals(413, response.statusCode.value())
        assertEquals("document-too-large", problem.properties?.get("code"))
    }
}
