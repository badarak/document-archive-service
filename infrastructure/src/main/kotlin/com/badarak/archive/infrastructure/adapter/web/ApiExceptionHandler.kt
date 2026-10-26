package com.badarak.archive.infrastructure.adapter.web

import com.badarak.archive.domain.model.ArchivingAbandonedException
import com.badarak.archive.domain.model.ContentMismatchException
import com.badarak.archive.domain.model.ContentTypeMismatchException
import com.badarak.archive.domain.model.DocumentNotFoundException
import com.badarak.archive.domain.model.DocumentTooLargeException
import com.badarak.archive.domain.model.DomainException
import com.badarak.archive.domain.model.EmptyDocumentException
import com.badarak.archive.domain.model.InvalidDocumentException
import com.badarak.archive.domain.model.InvalidDocumentTypeException
import com.badarak.archive.domain.model.InvalidSearchException
import com.badarak.archive.domain.model.InvalidStatusTransitionException
import com.badarak.archive.domain.model.InvalidTenantIdException
import com.badarak.archive.domain.model.UnsupportedDocumentFormatException
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.MissingObjectException
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.WebRequest
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Answers every API error as a Problem Details document (RFC 9457). Errors raised by the archive carry a
 * stable `code` property, since several of them share the same HTTP status; the code is also left on the request, for
 * the log line of the operation.
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
        is InvalidSearchException -> problem(HttpStatus.BAD_REQUEST, "invalid-search", error.message)
        is ArchivingAbandonedException -> unavailable(error)
        is InvalidStatusTransitionException -> internalError(error)
    }

    @ExceptionHandler(MissingCallerClaimException::class)
    fun handleMissingCallerClaim(error: MissingCallerClaimException): ProblemDetail =
        problem(HttpStatus.FORBIDDEN, CALLER_NOT_IDENTIFIED, error.message)

    @ExceptionHandler(ObjectStorageException::class, DocumentPersistenceException::class)
    fun handleUnavailableArchive(error: RuntimeException): ProblemDetail = unavailable(error)

    @ExceptionHandler(MissingObjectException::class)
    fun handleMissingContent(error: MissingObjectException): ProblemDetail {
        log.error("Content missing from the storage for an available document: {}", error.message, error)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "content-missing", "The content of this document cannot be found")
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleUnexpectedError(error: RuntimeException): ProblemDetail = internalError(error)

    override fun handleMaxUploadSizeExceededException(
        ex: MaxUploadSizeExceededException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? = super.handleMaxUploadSizeExceededException(ex, headers, status, request)
        ?.also {
            (it.body as? ProblemDetail)?.setProperty(CODE, DOCUMENT_TOO_LARGE)
            request.setAttribute(OperationLogFilter.ERROR_CODE, DOCUMENT_TOO_LARGE, RequestAttributes.SCOPE_REQUEST)
        }

    private fun unavailable(error: RuntimeException): ProblemDetail {
        log.error("Archive unavailable: {}", error.message, error)
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "archive-unavailable", "The archive is temporarily unavailable, retry later")
    }

    private fun internalError(error: Exception): ProblemDetail {
        log.error("Unexpected archiving error: {}", error.message, error)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "An unexpected error occurred")
    }

    private fun problem(status: HttpStatus, code: String, detail: String?): ProblemDetail {
        RequestContextHolder.getRequestAttributes()?.setAttribute(OperationLogFilter.ERROR_CODE, code, RequestAttributes.SCOPE_REQUEST)
        return ProblemDetail.forStatusAndDetail(status, detail).apply { setProperty(CODE, code) }
    }

    companion object {
        private const val CODE = "code"
        private const val DOCUMENT_TOO_LARGE = "document-too-large"
        private const val CALLER_NOT_IDENTIFIED = "caller-not-identified"
    }
}
