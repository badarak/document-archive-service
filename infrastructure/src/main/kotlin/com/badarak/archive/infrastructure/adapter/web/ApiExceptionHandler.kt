package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.model.ContentMismatchException
import com.badarak.archive.domain.model.ContentTypeMismatchException
import com.badarak.archive.domain.model.DocumentNotFoundException
import com.badarak.archive.domain.model.DocumentTooLargeException
import com.badarak.archive.domain.model.DomainException
import com.badarak.archive.domain.model.EmptyDocumentException
import com.badarak.archive.domain.model.InvalidDocumentException
import com.badarak.archive.domain.model.InvalidDocumentTypeException
import com.badarak.archive.domain.model.InvalidStatusTransitionException
import com.badarak.archive.domain.model.InvalidTenantIdException
import com.badarak.archive.domain.model.UnsupportedDocumentFormatException
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Answers every API error as a Problem Details document (RFC 9457). Errors raised by the archive carry a
 * stable `code` property, since several of them share the same HTTP status.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DomainException::class)
    fun handleDomainError(error: DomainException): ProblemDetail = when (error) {
        is InvalidDocumentException -> problem(HttpStatus.BAD_REQUEST, "invalid-document", error.message)
        is InvalidDocumentTypeException -> problem(HttpStatus.BAD_REQUEST, "invalid-document-type", error.message)
        is EmptyDocumentException -> problem(HttpStatus.BAD_REQUEST, "empty-document", error.message)
        is DocumentTooLargeException -> problem(HttpStatus.CONTENT_TOO_LARGE, DOCUMENT_TOO_LARGE, error.message)
        is UnsupportedDocumentFormatException -> problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-format", error.message)
        is ContentTypeMismatchException -> problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "content-type-mismatch", error.message)
        is ContentMismatchException -> problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "content-mismatch", error.message)
        is InvalidTenantIdException -> problem(HttpStatus.FORBIDDEN, CALLER_NOT_IDENTIFIED, error.message)
        is DocumentNotFoundException -> problem(HttpStatus.NOT_FOUND, "document-not-found", error.message)
        is InvalidStatusTransitionException -> internalError(error)
    }

    @ExceptionHandler(MissingCallerClaimException::class)
    fun handleMissingCallerClaim(error: MissingCallerClaimException): ProblemDetail =
        problem(HttpStatus.FORBIDDEN, CALLER_NOT_IDENTIFIED, error.message)

    @ExceptionHandler(ObjectStorageException::class, DocumentPersistenceException::class)
    fun handleUnavailableArchive(error: RuntimeException): ProblemDetail {
        log.error("Archiving failed: {}", error.message, error)
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "archive-unavailable", "The archive is temporarily unavailable, retry later")
    }

    override fun handleMaxUploadSizeExceededException(
        ex: MaxUploadSizeExceededException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? = super.handleMaxUploadSizeExceededException(ex, headers, status, request)
        ?.also { (it.body as? ProblemDetail)?.setProperty(CODE, DOCUMENT_TOO_LARGE) }

    private fun internalError(error: Exception): ProblemDetail {
        log.error("Unexpected archiving error: {}", error.message, error)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "An unexpected error occurred")
    }

    private fun problem(status: HttpStatus, code: String, detail: String?): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply { setProperty(CODE, code) }

    companion object {
        private const val CODE = "code"
        private const val DOCUMENT_TOO_LARGE = "document-too-large"
        private const val CALLER_NOT_IDENTIFIED = "caller-not-identified"
    }
}
